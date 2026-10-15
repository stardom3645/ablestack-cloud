# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
"""Dedicated UD-03 fixtures. Credentials are read only from environment variables."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
PREFIX = 'UD1384-31-20261016'
spec = importlib.util.spec_from_file_location('ud01', ROOT.parent / 'userdata-1382-20261015' / 'verify_api.py')
ud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ud)

def save(name, data):
    (ROOT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def ok(api, command, **params):
    result = api.call(command, **params)
    assert 'errorcode' not in result, (command, result)
    return result

def inventory(api):
    original = ud.inventory(api)
    save('baseline.private.json', dict(userdata=original))
    user = ok(api, 'listUsers', username='admin')['user'][0]
    data = {
        'root_domain_id': user['domainid'],
        'zones': ok(api, 'listZones').get('zone', []),
        'templates': ok(api, 'listTemplates', templatefilter='executable', isready='true', page=1, pagesize=200).get('template', []),
        'offerings': ok(api, 'listServiceOfferings', page=1, pagesize=200).get('serviceoffering', []),
        'networks': ok(api, 'listNetworks', listall='true', page=1, pagesize=200).get('network', []),
        'autoscale': ok(api, 'listAutoScaleVmGroups', listall='true', page=1, pagesize=200).get('autoscalevmgroup', [])
    }
    save('inventory.private.json', data)
    print(json.dumps({
        'baseline_userdata_count': len(original),
        'templates': [{k:r.get(k) for k in ['id','name','isready','hypervisor','size','userdataid']} for r in data['templates']],
        'offerings': [{k:r.get(k) for k in ['id','name','cpunumber','memory','iscustomized']} for r in data['offerings']],
        'networks': [{k:r.get(k) for k in ['id','name','type','networkofferingid','account','domainid','supportsvmautoscaling']} for r in data['networks']],
        'autoscale': [{k:r.get(k) for k in ['id','name','state','account','domainid','vmprofileid']} for r in data['autoscale']]
    }, ensure_ascii=False))

def prepare(api):
    inv = json.loads((ROOT / 'inventory.private.json').read_text(encoding='utf-8'))
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8')) if (ROOT / 'runtime-state.json').exists() else dict(prefix=PREFIX, userdata=[], accounts=[], root_domain_id=inv['root_domain_id'])
    assert state['prefix'] == PREFIX
    if not state.get('domain_id'):
        domain = ok(api, 'createDomain', name=PREFIX.lower())['domain']
        state.update(domain_id=domain['id'], domain_path=domain['path'])
        save('runtime-state.json', state)
    domain = dict(id=state['domain_id'])
    for suffix, accounttype in [('user',0),('domainadmin',2),('empty',0)]:
        name = PREFIX.lower()+'-'+suffix
        if any(row['role'] == suffix for row in state['accounts']):
            continue
        account = ok(api, 'createAccount', username=name, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1384', lastname='QA', email=suffix+'@example.invalid', account=name, accounttype=accounttype, domainid=domain['id'])['account']
        state['accounts'].append(dict(id=account['id'], username=name, role=suffix))
        save('runtime-state.json', state)
    if not state.get('project_id'):
        matches = ok(api, 'listProjects', name=PREFIX+'-project', listall='true').get('project', [])
        project = matches[0] if matches else api.job('createProject', name=PREFIX+'-project', displaytext=PREFIX, account=state['accounts'][0]['username'], domainid=domain['id'])['project']
        state['project_id'] = project['id']
    save('runtime-state.json', state)
    targets = [('admin',99,dict(account='admin',domainid=inv['root_domain_id'])),
               ('user',11,dict(account=state['accounts'][0]['username'],domainid=domain['id'])),
               ('domainadmin',30,dict(account=state['accounts'][1]['username'],domainid=domain['id'])),
               ('project',11,dict(projectid=state['project_id']))]
    for suffix, count, scope in targets:
        for number in range(1,count+1):
            name = PREFIX+'-'+suffix+'-'+str(number).zfill(3)
            if any(row['name'] == name for row in state['userdata']):
                continue
            content = '#cloud-config\n# '+name+'\n# variable: VAR1\n'
            row = ok(api, 'registerUserData', name=name, userdata=base64.b64encode(content.encode()).decode(), params='VAR1', **scope)['userdata']
            state['userdata'].append(dict(id=row['id'], name=row['name'], scope=suffix))
            save('runtime-state.json', state)
        print(suffix, count, flush=True)
    role = ok(api, 'createRole', name=PREFIX+'-no-list-role', type='User', ispublic='false')['role']
    state['role_id'] = role['id']; save('runtime-state.json', state)
    for rule, permission in [('listUserData','deny'),('list*','allow'),('get*','allow'),('query*','allow'),('find*','allow'),('deployVirtualMachine','allow'),('logout','allow'),('*','deny')]:
        ok(api, 'createRolePermission', roleid=role['id'], rule=rule, permission=permission)
    name = PREFIX.lower()+'-denied'
    account = ok(api, 'createAccount', username=name, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1384', lastname='QA', email='denied@example.invalid', account=name, domainid=domain['id'], roleid=role['id'])['account']
    state['accounts'].append(dict(id=account['id'], username=name, role='denied')); save('runtime-state.json', state)
    print('Prepared fixtures without creating a VM')

def verify(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    results = []
    def snapshot(label, client, scope, expected):
        rows = []
        pages = []
        for page in range(1, max(1, (expected+9)//10)+1):
            result = ok(client, 'listUserData', page=page, pagesize=10, **scope)
            current = result.get('userdata', [])
            assert result.get('count', 0) == expected
            rows.extend(current)
            pages.append(dict(page=page, total=result.get('count', 0), rows=[{k:r.get(k) for k in ['id','name','account','domainid','projectid','params']} for r in current]))
        assert len(rows) == len({r['id'] for r in rows}) == expected
        results.append(dict(scope=label, count=expected, pages=pages, all_pages_unique=True))
        return rows
    root = snapshot('admin', api, dict(account='admin', domainid=state['root_domain_id']), 100)
    result = ok(api, 'listUserData', page=1, pagesize=100, account='admin', domainid=state['root_domain_id'])
    assert {r['id'] for r in root} == {r['id'] for r in result['userdata']}
    for suffix, count in [('user',11),('domainadmin',30),('empty',0)]:
        account = next(a for a in state['accounts'] if a['role'] == suffix)
        client = ud.Mold(account['username'], domain='/'+PREFIX.lower()+'/')
        snapshot(suffix, client, dict(account=account['username'], domainid=state['domain_id']), count)
    snapshot('project', api, dict(projectid=state['project_id']), 11)
    denied = next(a for a in state['accounts'] if a['role'] == 'denied')
    result = ud.Mold(denied['username'], domain='/'+PREFIX.lower()+'/').call('listUserData')
    assert result.get('errorcode') == 432
    name = PREFIX+'-admin-001'
    result = ok(api, 'listUserData', account='admin', domainid=state['root_domain_id'], keyword=name, page=1, pagesize=10)
    assert result['count'] == 1 and result['userdata'][0]['name'] == name
    save('api-pages.json', dict(scopes=results, pagesize100_same_ids=True, denied_errorcode=432, exact_keyword_count=1))
    print('API owner/page/keyword/permission qualification PASS')

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    {'inventory': inventory, 'prepare': prepare, 'verify': verify}[sys.argv[1]](ud.Mold())
