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
import ResetUserData from '@/views/compute/ResetUserData.vue'
import { getAPI, postAPI } from '@/api'
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

function userDataContext (autoscale, tab, values) {
  postAPI.mockResolvedValue({ updateautoscalevmprofileresponse: { jobid: 'job' } })
  getAPI.mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 1, jobresult: { autoscalevmprofile: { id: 'profile' } } } })
  return { isAutoScaleProfile: autoscale, userdataTabKey: tab, form: values, resource: autoscale ? { resetUserDataApiName: 'updateAutoScaleVmProfile', resetUserDataResourceId: 'profile' } : { id: 'vm' }, loadingData: false, templateUserDataValues: {}, userDataValues: {}, $toBase64AndURIEncoded: value => 'encoded:' + value, $t: key => key, $message: { success: jest.fn() }, $emit: jest.fn(), closeAction: jest.fn() }
}
test('registered AutoScale UserData selection ignores a retained manual draft', async () => {
  await ResetUserData.methods.handleSubmit.call(userDataContext(true, 'userdataregistered', { userdata: 'stale', userdataid: 'registered' }))
  expect(postAPI).toHaveBeenCalledWith('updateAutoScaleVmProfile', { id: 'profile', userdataid: 'registered' })
})
test('manual AutoScale UserData ignores a retained registered selection', async () => {
  await ResetUserData.methods.handleSubmit.call(userDataContext(true, 'userdatatext', { userdata: 'manual', userdataid: 'registered' }))
  expect(postAPI).toHaveBeenCalledWith('updateAutoScaleVmProfile', { id: 'profile', userdata: 'encoded:manual' })
})
test('registered None clears AutoScale custom data even when the manual draft is retained', async () => {
  await ResetUserData.methods.handleSubmit.call(userDataContext(true, 'userdataregistered', { userdata: 'stale' }))
  expect(postAPI).toHaveBeenCalledWith('updateAutoScaleVmProfile', { id: 'profile', userdata: '' })
})
test('the default VM reset request keeps its existing parameters', async () => {
  await ResetUserData.methods.handleSubmit.call(userDataContext(false, 'userdataregistered', { userdata: 'manual', userdataid: 'registered' }))
  expect(postAPI).toHaveBeenCalledWith('resetUserDataForVirtualMachine', { id: 'vm', userdata: 'encoded:manual', userdataid: 'registered' })
})
test('a registered script with no variables leaves an empty variable list without failing', async () => {
  getAPI.mockResolvedValue({ listuserdataresponse: { userdata: [{ id: 'registered' }] } })
  const ctx = { form: {}, userDataParams: [{ key: 'stale' }] }
  ResetUserData.methods.updateUserData.call(ctx, 'registered')
  await Promise.resolve()
  expect(ctx.userDataParams).toEqual([])
  expect(ctx.form.userdataid).toBe('registered')
})
test('AutoScale dialog template metadata uses allowed project lists and never reads an unrelated script when the template has no UserData', async () => {
  getAPI.mockResolvedValue({ listtemplatesresponse: { template: [{ id: 'template' }] } })
  const ctx = { isAutoScaleProfile: true, resource: { templateid: 'template', projectid: 'project', zoneid: 'zone' }, updateTemplateLinkedUserData: jest.fn(), $notifyError: jest.fn() }
  await ResetUserData.methods.fetchTemplateData.call(ctx)
  expect(getAPI).toHaveBeenCalledWith('listTemplates', expect.objectContaining({ projectid: 'project', isready: true, templatefilter: 'executable' }))
  expect(ctx.updateTemplateLinkedUserData).not.toHaveBeenCalled()
})

test.each(['AutoScaleVmGroup', 'VirtualMachine'])('schedule state rendering is localized only for the AutoScale resource: %s', resourceType => {
  const ctx = { resourceType, columnKeys: ['enabled'], selectedColumnKeys: ['enabled'], $t: key => key }
  Schedules.methods.updateColumns.call(ctx)
  if (resourceType === 'AutoScaleVmGroup') {
    expect(ctx.columns[0].customRender({ record: { enabled: false } }).props.displayLabel).toBe('state.disabled')
    expect(ctx.columns[0].customRender({ record: { enabled: true } }).props.displayLabel).toBe('state.enabled')
  } else {
    expect(ctx.columns[0].key).toBe('enabled')
    expect(ctx.columns[0].customRender).toBeUndefined()
  }
})

const removalCases = [
  ['schedule', Schedules, 'prepareScheduleRemoval', 'confirmScheduleRemoval', 'removeSchedule', () => ({ selectedSchedule: { id: 'schedule' }, schedules: [{ id: 'schedule', description: 'current' }] }), ctx => { ctx.selectedSchedule = null }, ctx => { ctx.schedules = [] }],
  ['parameter', Profile, 'prepareParamRemoval', 'confirmParamRemoval', 'deleteParam', () => ({ profileid: 'profile', selectedParam: { name: 'rootdisksize' }, allParams: [{ name: 'rootdisksize', value: '100' }] }), ctx => { ctx.selectedParam = undefined }, ctx => { ctx.profileid = 'different' }],
  ...[Up, Down].flatMap((component, index) => [
    ['condition-' + index, component, 'prepareConditionRemoval', 'confirmConditionRemoval', 'deleteConditionFromAutoScalePolicy', () => ({ policy: { id: 'policy', conditions: [{ id: 'condition' }] }, selectedTableCondition: { id: 'condition' } }), ctx => { ctx.selectedTableCondition = undefined }, ctx => { ctx.policy = { id: 'different', conditions: [{ id: 'condition' }] } }],
    ['policy-' + index, component, 'preparePolicyRemoval', 'confirmPolicyRemoval', 'removeScalePolicyFromGroup', () => ({ policy: { id: 'policy' }, policies: [{ id: 'policy' }, { id: 'other' }] }), () => {}, ctx => { ctx.policy = { id: 'other' } }]
  ]),
  ['VM-IP', LB, 'prepareInstanceRemoval', 'confirmInstanceRemoval', 'handleDeleteInstanceFromRule', () => ({ currentRule: { id: 'rule' }, selectedInstance: { key: 'vm/10.0.0.2' }, ruleInstances: [{ key: 'vm/10.0.0.2', ip: '10.0.0.2', instance: { loadbalancerruleinstance: { id: 'vm' } } }] }), ctx => { ctx.selectedInstance = undefined }, ctx => { ctx.ruleInstances = [{ key: 'vm/10.0.0.3', ip: '10.0.0.3' }] }]
]
function removalContext (setup, mutation) {
  return { ...setup(), resource: { id: 'group' }, loading: false, saving: false, tabLoading: false, isSubmitted: false, $t: key => key, $message: { warning: jest.fn() }, [mutation]: jest.fn() }
}
test.each(removalCases)('%s row confirmation keeps the clicked record when background state clears former selection', (name, component, prepare, confirm, mutation, setup, clear) => {
  const ctx = removalContext(setup, mutation)
  component.methods[prepare].call(ctx, name === 'schedule' ? ctx.selectedSchedule : name === 'parameter' ? ctx.selectedParam : name === 'VM-IP' ? ctx.selectedInstance : ctx.selectedTableCondition)
  clear(ctx)
  component.methods[confirm].call(ctx)
  expect(ctx[mutation]).toHaveBeenCalledTimes(1)
  expect(ctx.$message.warning).not.toHaveBeenCalled()
  if (name === 'schedule') expect(ctx[mutation]).toHaveBeenCalledWith(ctx.schedules[0])
  if (name === 'parameter') expect(ctx[mutation]).toHaveBeenCalledWith('rootdisksize')
  if (name.startsWith('condition')) expect(ctx[mutation]).toHaveBeenCalledWith('condition')
  if (name === 'VM-IP') expect(ctx[mutation]).toHaveBeenCalledWith(ctx.ruleInstances[0].instance, ctx.currentRule, '10.0.0.2')
})
test.each(removalCases)('%s confirmation refuses the target when its list, parent or VM-IP tuple changed', (name, component, prepare, confirm, mutation, setup, clear, change) => {
  const ctx = removalContext(setup, mutation)
  component.methods[prepare].call(ctx, name === 'schedule' ? ctx.selectedSchedule : name === 'parameter' ? ctx.selectedParam : name === 'VM-IP' ? ctx.selectedInstance : ctx.selectedTableCondition)
  change(ctx)
  component.methods[confirm].call(ctx)
  expect(ctx[mutation]).not.toHaveBeenCalled()
  expect(ctx.$message.warning).toHaveBeenCalledWith('message.autoscale.selection.stale')
})
test.each(removalCases)('%s confirmation cannot mutate a different group after navigation', (name, component, prepare, confirm, mutation, setup) => {
  const ctx = removalContext(setup, mutation)
  component.methods[prepare].call(ctx, name === 'schedule' ? ctx.selectedSchedule : name === 'parameter' ? ctx.selectedParam : name === 'VM-IP' ? ctx.selectedInstance : ctx.selectedTableCondition)
  ctx.resource = { id: 'another-group' }
  component.methods[confirm].call(ctx)
  expect(ctx[mutation]).not.toHaveBeenCalled()
})

test.each(['설정 안함', 'None'])('the actual localized library None row %s clears the previous AutoScale selection and variable values', async label => {
  const ctx = userDataContext(true, 'userdataregistered', { userdataid: 'old', userdata: 'retained' })
  ctx.$t = key => key === 'label.noselect' ? label : key
  ctx.userDataValues = { old: 'secret' }
  ResetUserData.methods.updateUserData.call(ctx, label)
  await ResetUserData.methods.handleSubmit.call(ctx)
  expect(postAPI).toHaveBeenCalledWith('updateAutoScaleVmProfile', { id: 'profile', userdata: '' })
  expect(getAPI).not.toHaveBeenCalledWith('listUserData', { id: label })
})
test('returning from manual entry to the AutoScale library submits None as displayed by the remounted selection widget', async () => {
  const ctx = userDataContext(true, 'userdataregistered', { userdataid: 'old-library', userdata: 'manual' })
  ResetUserData.methods.onUserdataTabChange.call(ctx, 'userdatatext', 'userdataTabKey')
  ResetUserData.methods.onUserdataTabChange.call(ctx, 'userdataregistered', 'userdataTabKey')
  await ResetUserData.methods.handleSubmit.call(ctx)
  expect(postAPI).toHaveBeenCalledWith('updateAutoScaleVmProfile', { id: 'profile', userdata: '' })
})
test('the same library tab navigation keeps the existing VM request contract', async () => {
  const ctx = userDataContext(false, 'userdataregistered', { userdataid: 'old-library', userdata: 'manual' })
  ResetUserData.methods.onUserdataTabChange.call(ctx, 'userdatatext', 'userdataTabKey')
  ResetUserData.methods.onUserdataTabChange.call(ctx, 'userdataregistered', 'userdataTabKey')
  await ResetUserData.methods.handleSubmit.call(ctx)
  expect(postAPI).toHaveBeenCalledWith('resetUserDataForVirtualMachine', { id: 'vm', userdataid: 'old-library', userdata: 'encoded:manual' })
})
test('a late variable lookup for an unselected AutoScale script cannot repopulate the None form', async () => {
  let finishLookup
  getAPI.mockImplementation(() => new Promise(resolve => { finishLookup = resolve }))
  const ctx = { isAutoScaleProfile: true, form: {}, userDataParams: [], userDataValues: {}, $t: () => '설정 안함' }
  ResetUserData.methods.updateUserData.call(ctx, 'script')
  ResetUserData.methods.updateUserData.call(ctx, '설정 안함')
  finishLookup({ listuserdataresponse: { userdata: [{ params: 'old-variable' }] } })
  await Promise.resolve()
  expect(ctx.userDataParams).toEqual([])
})

test.each([Up, Down])('policy row actions select the clicked policy independently of the prior selection', component => {
  const ctx = { selectedPolicyId: 'old', switchPolicy: jest.fn() }
  component.methods.selectPolicy.call(ctx, { id: 'clicked' })
  expect(ctx.selectedPolicyId).toBe('clicked')
  expect(ctx.switchPolicy).toHaveBeenCalledTimes(1)
})
test('VM row detach pins exactly the clicked IP when the same VM has several addresses', () => {
  const rows = ['10.0.0.2', '10.0.0.3'].map(ip => ({ key: 'vm/' + ip, ip, instance: { loadbalancerruleinstance: { id: 'vm' } } }))
  const ctx = { resource: { id: 'group' }, currentRule: { id: 'rule' }, ruleInstances: rows, selectedInstance: rows[0], loading: false, handleDeleteInstanceFromRule: jest.fn(), $t: key => key, $message: { warning: jest.fn() } }
  LB.methods.prepareInstanceRemoval.call(ctx, rows[1])
  LB.methods.confirmInstanceRemoval.call(ctx)
  expect(ctx.handleDeleteInstanceFromRule).toHaveBeenCalledWith(rows[1].instance, ctx.currentRule, '10.0.0.3')
})

test.each(removalCases.filter(([name]) => !name.startsWith('policy')))('%s confirmation from an older row cannot execute a newly opened target', (name, component, prepare, confirm, mutation, setup) => {
  const ctx = removalContext(setup, mutation)
  const first = name === 'schedule' ? ctx.selectedSchedule : name === 'parameter' ? ctx.selectedParam : name === 'VM-IP' ? ctx.selectedInstance : ctx.selectedTableCondition
  component.methods[prepare].call(ctx, first)
  const second = { ...first, id: 'another', name: 'another', key: 'another' }
  component.methods[prepare].call(ctx, second)
  component.methods[confirm].call(ctx, first)
  expect(ctx[mutation]).not.toHaveBeenCalled()
  expect(ctx.$message.warning).toHaveBeenCalledWith('message.autoscale.selection.stale')
})
