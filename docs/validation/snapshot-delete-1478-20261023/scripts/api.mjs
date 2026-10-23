// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with this
// work for additional information regarding copyright ownership. The ASF
// licenses this file to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the License.
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations.
import fs from 'node:fs'
const cluster = process.argv[2]
const phase = process.argv[3]
const root = process.env.EVIDENCE_ROOT || '/home/ablecloud/work/snapshot-delete-1478-20261023'
const registryFile = `${root}/${cluster}-fixtures.json`
const prefix = `SNAP1478-${cluster}`
let cookies = '', session = ''
const clean = x => JSON.parse(JSON.stringify(x, (k, v) => /password|sessionkey|secretkey|apikey|jsessionid/i.test(k) ? '[REDACTED]' : v))
async function api (command, args = {}) {
  const body = new URLSearchParams({ command, response: 'json', ...args, ...(session ? { sessionkey: session } : {}) })
  const response = await fetch(`http://10.10.${cluster}.10:8080/client/api`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', ...(cookies ? { Cookie: cookies } : {}) }, body, signal: AbortSignal.timeout(60000) })
  const set = response.headers.getSetCookie()
  if (set.length) cookies = set.map(v => v.split(';')[0]).join('; ')
  const json = await response.json(), data = Object.values(json)[0]
  fs.appendFileSync(`${root}/${cluster}-api.jsonl`, JSON.stringify(clean({ at: new Date().toISOString(), command, args, status: response.status, data })) + '\n')
  if (!response.ok || data.errorcode) throw new Error(JSON.stringify(clean(data)))
  return data
}
async function job (command, args) {
  const response = await api(command, args)
  if (!response.jobid) return response
  console.log(cluster, command, response.jobid)
  for (let i = 0; i < 300; i++) {
    const result = await api('queryAsyncJobResult', { jobid: response.jobid })
    if (result.jobstatus === 1) return result.jobresult
    if (result.jobstatus === 2) throw new Error(JSON.stringify(result))
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error(`Job result unknown: ${response.jobid}`)
}
const login = await api('login', { username: process.env.MOLD_TEST_USER || 'admin', password: process.env.MOLD_TEST_PASSWORD, domain: '/' })
session = login.sessionkey
try {
 const reg = fs.existsSync(registryFile) ? JSON.parse(fs.readFileSync(registryFile)) : { cluster, prefix, volumes: [], snapshots: [] }
 const save = () => fs.writeFileSync(registryFile, JSON.stringify(reg, null, 2))
 if (phase === 'create') {
  if (reg.volumes.length) throw new Error('Fixture already exists')
  const inventory = {}
  for (const command of ['listVolumes', 'listSnapshots', 'listStoragePools', 'listDiskOfferings']) inventory[command] = await api(command, { listall: true, page: 1, pagesize: 500, ...(command === 'listSnapshots' ? { showunique: false } : {}) })
  fs.writeFileSync(`${root}/${cluster}-existing-before.json`, JSON.stringify(inventory, null, 2))
  const pool = inventory.listStoragePools.storagepool[0]
  const disk = inventory.listDiskOfferings.diskoffering.find(x => x.name === (cluster === '31' ? 'Custom-GFS-Sparse' : 'Custom'))
  const result = await job('createVolume', { name: `${prefix}-source`, zoneid: pool.zoneid, storageid: pool.id, diskofferingid: disk.id, size: 1 })
  reg.volumes.push(result.volume); save()
  for (const label of ['normal', 'residue', 'partial', 'control']) {
    const r = await job('createSnapshot', { volumeid: reg.volumes[0].id, name: `${prefix}-${label}`, asyncbackup: false, usestoragereplication: false })
    reg.snapshots.push({ ...r.snapshot, scenario: label }); save()
  }
 } else if (phase === 'archive-baseline') {
  const s = reg.snapshots.find(x => x.scenario === 'residue')
  const result = await job('archiveSnapshot', { id: s.id }); fs.writeFileSync(`${root}/${cluster}-archive-baseline.json`, JSON.stringify(result, null, 2))
 } else if (phase === 'call') {
  const command = process.argv[4], args = JSON.parse(process.argv[5] || '{}')
  const result = await job(command, args); console.log(JSON.stringify(result))
 } else if (phase === 'collect') {
  const result = await api('listSnapshots', { keyword: prefix, listall: true, showunique: false, page: 1, pagesize: 500 })
  fs.writeFileSync(`${root}/${cluster}-current.json`, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result))
 }
 console.log(cluster, phase, 'complete')
} finally { await api('logout').catch(() => {}) }
