# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Dedicated UD-02 fixtures; password is provided through the environment."""
import base64
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ud01', ROOT.parent / 'userdata-1382-20261015' / 'verify_api.py')
ud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ud)
ud.ROOT = ROOT
ud.PREFIX = 'UD1383-31-20261015'

def verify(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text())
    rows = [row for row in ud.inventory(api) if row['name'].startswith(ud.PREFIX)]
    rows += api.call('listUserData', projectid=state['project_id']).get('userdata', [])
    result = []
    for row in rows:
        payload = base64.b64decode(row['userdata']).decode()
        assert 'UD1383' in payload, row['name']
        result.append(dict(id=row['id'], name=row['name'], account=row.get('account'), domain=row.get('domain'), projectid=row.get('projectid'), decoded=payload, params=row.get('params')))
    ud.save('ui-api-results.json', result)
    print(json.dumps(result, ensure_ascii=False))

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    api = ud.Mold()
    {'baseline': ud.baseline, 'prepare': ud.prepare_ui, 'verify': verify, 'cleanup': ud.cleanup_ui}[sys.argv[1]](api)
