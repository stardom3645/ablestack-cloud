// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import Up from '@/views/compute/AutoScaleUpPolicyTab.vue'
import Down from '@/views/compute/AutoScaleDownPolicyTab.vue'
import Profile from '@/views/compute/AutoScaleVmProfile.vue'
import LB from '@/views/compute/AutoScaleLoadBalancing.vue'
import Schedules from '@/views/compute/ResourceSchedules.vue'
import { postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { apis: {}, userInfo: {}, project: null } }))

test.each([Up, Down])('policy dialog draft changes do not mutate the displayed policy before confirmation', component => {
  const policy = { id: 'policy', name: 'before', duration: 60, quiettime: 300, conditions: [{ id: 'condition' }] }
  const ctx = { policy, policyDraft: {}, mutationError: 'old' }
  component.methods.openPolicyModal.call(ctx)
  ctx.policyDraft.name = 'cancelled edit'
  ctx.policyDraft.duration = 100
  component.methods.closeModal.call(ctx)
  expect(policy.name).toBe('before')
  expect(policy.duration).toBe(60)
  expect(ctx.editPolicyModalVisible).toBe(false)
  expect(postAPI).not.toHaveBeenCalled()
})

test.each([Up, Down])('policy confirmation sends the dialog values and retains the current condition links', async component => {
  const mutate = jest.fn().mockResolvedValue({})
  const ctx = { resource: { interval: 10 }, policy: { id: 'p', name: 'old', duration: 60, quiettime: 300, conditions: [{ id: 'c' }] }, policyDraft: { name: 'changed', duration: 80, quiettime: 400 }, editPolicyModalVisible: true, isEditable: true, mutate, saveEdit: action => action() }
  await component.methods.updateAutoScalePolicy.call(ctx, null, null)
  expect(mutate).toHaveBeenCalledWith('updateAutoScalePolicy', { id: 'p', name: 'changed', duration: 80, quiettime: 400, conditionids: 'c' })
  expect(ctx.policy.name).toBe('old')
})

test.each([Up, Down])('switching policies clears the previous condition target', component => {
  const ctx = { policies: [{ id: 'p2', conditions: [{ id: 'c2' }] }], selectedPolicyId: 'p2', selectedConditionKeys: ['c1'], resource: {}, $notifyError: jest.fn() }
  // No remote affected-group lookup when the policy is not selected.
  ctx.selectedPolicyId = null
  component.methods.switchPolicy.call(ctx)
  expect(ctx.selectedConditionKeys).toEqual([])
  expect(component.computed.selectedTableCondition.call(ctx)).toBeUndefined()
})

test('profile list refresh clears parameter targets so a renamed/removed item cannot be deleted', () => {
  const ctx = { selectedParamKeys: ['rootdisksize'], allParams: [{ name: 'rootdisksize', value: '120' }] }
  Profile.methods.finalizeParams.call(ctx, {}, { keypairs: 'key' })
  expect(ctx.selectedParamKeys).toEqual([])
  expect(Profile.computed.selectedParam.call(ctx)).toBeUndefined()
  expect(ctx.allParams).toEqual([{ name: 'keypairs', value: 'key' }])
})

test('VM selection preserves the exact VM and IP tuple when one VM has multiple LB addresses', () => {
  const instance = { loadbalancerruleinstance: { id: 'vm' }, lbvmipaddresses: ['10.0.0.4', '10.0.0.5'] }
  const ctx = { currentRule: { ruleInstances: [instance] }, selectedInstanceKeys: ['vm/10.0.0.5'] }
  ctx.ruleInstances = LB.computed.ruleInstances.call(ctx)
  expect(LB.computed.selectedInstance.call(ctx)).toEqual({ key: 'vm/10.0.0.5', ip: '10.0.0.5', instance })
  ctx.selectedInstanceKeys = ['missing']
  expect(LB.computed.selectedInstance.call(ctx)).toBeUndefined()
})

test.each([[[]], [['a', 'b']], [['stale']]])('schedule edit/delete requires exactly one current record: %s', keys => {
  expect(Schedules.computed.selectedSchedule.call({ selectedScheduleKeys: keys, schedules: [{ id: 'a' }, { id: 'b' }] })).toBeFalsy()
})
test('schedule edit resolves the selected record by ID rather than its old list position', () => {
  const item = { id: 'b', schedule: '0 9 * * *' }
  expect(Schedules.computed.selectedSchedule.call({ selectedScheduleKeys: ['b'], schedules: [item, { id: 'a' }] })).toBe(item)
})
