// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

// Preserve a user's saved visibility choices when former single-value columns
// are consolidated. A selection consisting only of removed columns stays useful.
export function autoScaleVisibleColumns (columns) {
  const oldDefault = ['name', 'state', 'minmembers', 'maxmembers', 'availablevirtualmachinecount', 'publicip', 'publicport', 'privateport', 'associatednetworkname', 'account'].every(key => columns.includes(key))
  if (oldDefault) return ['name', 'state', 'availablevirtualmachinecount', 'associatednetworkname', 'lastcheckedat', 'account', ...(columns.includes('project') ? ['project'] : [])]
  const mapped = columns.map(key => ['minmembers', 'maxmembers'].includes(key) ? 'availablevirtualmachinecount' : ['publicip', 'publicport', 'privateport'].includes(key) ? 'associatednetworkname' : key)
  return [...new Set(mapped.filter(key => ['name', 'state', 'availablevirtualmachinecount', 'associatednetworkname', 'lastcheckedat', 'account', 'project'].includes(key)))]
}

// The executable filter excludes some project-shared private images. Query the
// dedicated shared filter only when an exact, scoped image lookup is empty.
export async function autoScaleTemplateById (request, params) {
  const response = await request('listTemplates', { ...params, templatefilter: 'executable' })
  if (response.listtemplatesresponse?.template?.some(item => item.id === params.id)) return response
  return request('listTemplates', { ...params, templatefilter: 'sharedexecutable' })
}
