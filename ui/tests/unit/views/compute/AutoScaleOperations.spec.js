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

import Operations from '@/views/compute/AutoScaleOperations.vue'
import Evaluation from '@/views/compute/AutoScaleEvaluation.vue'
import { autoScaleStateSummary, autoScaleTime, autoScaleEvaluationChanged, autoScaleLbState } from '@/utils/autoScaleOperations'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const defer = () => { let resolveDeferred; let rejectDeferred; const promise = new Promise((resolve, reject) => { resolveDeferred = resolve; rejectDeferred = reject }); return { resolve: resolveDeferred, reject: rejectDeferred, promise } }
function context () {
  const resource = { id: 'group', projectid: 'project', lbruleid: 'lb' }
  const ctx = { resource, ...Operations.data.call({ resource }), $store: { getters: { apis: { listAutoScaleVmGroups: {}, listVirtualMachines: {}, listLoadBalancerRuleInstances: {}, listEvents: {} } } } }
  Object.entries(Operations.methods).forEach(([name, fn]) => { ctx[name] = fn.bind(ctx) })
  return ctx
}
beforeEach(() => jest.clearAllMocks())
test('summary separates transitional, stopped, error and cleanup members without implying health', () => {
  expect(autoScaleStateSummary({ Running: 2, Starting: 1, Stopping: 1, Migrating: 1, Stopped: 1, Error: 1, Unknown: 1, Removed: 1, Missing: 1, Destroyed: 1, Expunging: 1, Shutdowned: 1 })).toEqual({ running: 2, transition: 3, stopped: 1, error: 2, cleanup: 4, other: 1, total: 13 })
  expect(autoScaleStateSummary(null)).toBeNull()
  expect(autoScaleStateSummary({})).toEqual({ running: 0, transition: 0, stopped: 0, error: 0, cleanup: 0, other: 0, total: 0 })
})
test.each([undefined, null, 'invalid', NaN, Infinity, 8640000000000001])('invalid timestamps render absent instead of throwing: %s', value => {
  expect(autoScaleTime(value, jest.fn(), key => key)).toBe('label.autoscale.not.recorded')
})
test('times preserve epoch millisecond accuracy', () => {
  expect(autoScaleTime(1791712345678, value => value, key => key)).toBe(new Date(1791712345678).toISOString())
})
test('changed and detached policies are labelled historical, unchanged ones remain current', () => {
  const resource = { scaleuppolicies: [{ id: 'p', duration: 60, conditions: [{ id: 'c', relationaloperator: 'GT', threshold: '20' }] }], evaluation: { policies: [{ id: 'p', duration: 60, conditions: [{ id: 'c', operator: 'GT', threshold: 20 }] }] } }
  expect(autoScaleEvaluationChanged(resource)).toBe(false)
  resource.scaleuppolicies[0].conditions[0].threshold = '30'
  expect(autoScaleEvaluationChanged(resource)).toBe(true)
  resource.scaleuppolicies = []
  expect(autoScaleEvaluationChanged(resource)).toBe(true)
})
test('LB read failure and denial cannot become an unregistered health verdict', () => {
  const state = { rows: [{ id: 'vm', servicestate: 'Active' }], loaded: true }
  expect(autoScaleLbState({ id: 'vm' }, state)).toBe('Active')
  expect(autoScaleLbState({ id: 'other' }, state)).toBe('UNREGISTERED')
  expect(autoScaleLbState({ id: 'other' }, { ...state, failed: true })).toBe('STALE')
  expect(autoScaleLbState({ id: 'other' }, { ...state, denied: true })).toBe('DENIED')
  expect(autoScaleLbState({ id: 'other' }, { ...state, loaded: false, failed: true })).toBe('FAILED')
})
test('an older response never overwrites the current member page', async () => {
  const ctx = context(); const first = defer(); const second = defer()
  getAPI.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
  const a = ctx.fetchMembers(); ctx.page = 2; const b = ctx.fetchMembers()
  second.resolve({ listvirtualmachinesresponse: { virtualmachine: [{ id: 'new' }], count: 11 } }); await b
  first.resolve({ listvirtualmachinesresponse: { virtualmachine: [{ id: 'old' }], count: 1 } }); await a
  expect(ctx.parts.members.rows).toEqual([{ id: 'new' }]); expect(ctx.total).toBe(11); expect(ctx.page).toBe(2)
})
test('partial refresh retains previous failed source and updates independent sources', async () => {
  const ctx = context(); ctx.parts.members.rows = [{ id: 'old' }]; ctx.parts.members.loaded = true; ctx.page = 3; ctx.keyword = 'filter'
  getAPI.mockImplementation(api => api === 'listVirtualMachines' ? Promise.reject(new Error('offline')) : Promise.resolve({ listautoscalevmgroupsresponse: { autoscalevmgroup: [{ id: 'group', memberstatecounts: { Running: 2 } }] }, listloadbalancerruleinstancesresponse: { loadbalancerruleinstance: [] }, listeventsresponse: { event: [{ id: 'event' }] } }))
  await ctx.fetchData()
  expect(ctx.parts.members.rows).toEqual([{ id: 'old' }]); expect(ctx.parts.members.failed).toBe(true)
  expect(ctx.parts.group.rows.memberstatecounts).toEqual({ Running: 2 }); expect(ctx.parts.events.rows).toEqual([{ id: 'event' }])
  expect(ctx.page).toBe(3); expect(ctx.keyword).toBe('filter')
})
test('denied APIs are not called and stale previously authorized rows are hidden', async () => {
  const ctx = context(); ctx.$store.getters.apis = { listAutoScaleVmGroups: {} }; ctx.parts.members.rows = [{ id: 'private' }]
  getAPI.mockResolvedValue({ listautoscalevmgroupsresponse: { autoscalevmgroup: [ctx.resource] } })
  await ctx.fetchData()
  expect(getAPI).toHaveBeenCalledTimes(1); expect(ctx.parts.members.denied).toBe(true); expect(ctx.parts.members.rows).toEqual([])
})
test('all reads are scoped to the group and project; member search and page are server side', async () => {
  const ctx = context(); ctx.keyword = 'member'; ctx.page = 2
  getAPI.mockResolvedValue({ listvirtualmachinesresponse: {} }); await ctx.fetchMembers()
  expect(getAPI).toHaveBeenCalledWith('listVirtualMachines', expect.objectContaining({ autoscalevmgroupid: 'group', projectid: 'project', page: 2, pagesize: 10, keyword: 'member', details: 'min' }))
})
test('unmount invalidates pending reads', async () => {
  const ctx = context(); const pending = defer(); getAPI.mockReturnValue(pending.promise)
  const result = ctx.fetchMembers(); Operations.beforeUnmount.call(ctx)
  pending.resolve({ listvirtualmachinesresponse: { virtualmachine: [{ id: 'late' }] } }); await result
  expect(ctx.parts.members.rows).toEqual([])
})
test('resource replacement cannot apply a prior resource response', async () => {
  const ctx = context(); const pending = defer(); getAPI.mockReturnValueOnce(pending.promise)
  const result = ctx.fetchMembers(); ctx.parts.members = { rows: [], sequence: 0 }
  pending.resolve({ listvirtualmachinesresponse: { virtualmachine: [{ id: 'wrong-group' }] } }); await result
  expect(ctx.parts.members.rows).toEqual([])
})
test('unknown evaluation status has a readable fallback', () => {
  expect(Evaluation.methods.statusLabel.call({ $te: () => false, $t: key => key }, 'FUTURE')).toContain('(FUTURE)')
})
