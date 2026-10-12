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

test('Korean cron and picker localization are opted in for AutoScale while other schedule resources retain their existing cron defaults', () => {
  const locale = Schedules.computed.cronLocale.call({ resourceType: 'AutoScaleVmGroup', $i18n: { locale: 'ko_KR' } })
  expect(locale.day.text).toBe('일')
  expect(locale['*'].minute.empty.text).toBe('매분')
  expect(Schedules.computed.datePickerLocale.call({ $i18n: { locale: 'ko_KR' } }).lang.locale).toBe('ko_KR')
  expect(Schedules.computed.cronLocale.call({ resourceType: 'VirtualMachine', $i18n: { locale: 'ko_KR' } })).toBeUndefined()
})

beforeEach(() => jest.clearAllMocks())
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
test.each(removalCases)('%s confirmation keeps the opened target when a background refresh clears row selection', (name, component, prepare, confirm, mutation, setup, clear) => {
  const ctx = removalContext(setup, mutation)
  component.methods[prepare].call(ctx)
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
  component.methods[prepare].call(ctx)
  change(ctx)
  component.methods[confirm].call(ctx)
  expect(ctx[mutation]).not.toHaveBeenCalled()
  expect(ctx.$message.warning).toHaveBeenCalledWith('message.autoscale.selection.stale')
})
test.each(removalCases)('%s confirmation cannot mutate a different group after navigation', (name, component, prepare, confirm, mutation, setup) => {
  const ctx = removalContext(setup, mutation)
  component.methods[prepare].call(ctx)
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
