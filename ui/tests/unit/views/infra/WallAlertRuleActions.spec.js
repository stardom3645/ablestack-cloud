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

import { shallowMount, flushPromises } from '@vue/test-utils'
import { getAPI, postAPI } from '@/api'
import RuleModal from '@/views/infra/WallAlertRuleActionModal.vue'
import Toolbar from '@/views/infra/WallAlertRuleToolbar.vue'
import ListView from '@/components/view/ListView.vue'
import AutoAlertBanner from '@/components/header/AutoAlertBanner.vue'
import config from '@/config/section/infra/alertRules'
import ko from '@public/locales/ko_KR.json'
import en from '@public/locales/en.json'

jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))

const rules = [{ uid: 'a', name: 'CPU', isPaused: false }, { uid: 'b', name: 'Memory', isPaused: false }]
const pause = config.actions.find(action => action.wallRuleAction === 'pause')
const message = { success: jest.fn() }
const translate = (key, params = {}) => Object.entries(params).reduce((text, [name, value]) => text.replace(`{${name}}`, value), ko[key] || key)
const mountModal = props => shallowMount(RuleModal, {
  props: { currentAction: pause, ...props },
  global: { mocks: { $t: translate, $message: message } }
})

beforeEach(() => { jest.clearAllMocks(); postAPI.mockResolvedValue({}) })

test('selected-rule confirmation ignores the context-menu resource and snapshots the selection', async () => {
  const wrapper = mountModal({ currentAction: { ...pause, invokedAsGroupAction: true }, resource: { uid: 'other' }, selectedItems: rules })
  await flushPromises()
  expect(wrapper.vm.targets.map(r => r.uid)).toEqual(['a', 'b'])
  await wrapper.setProps({ selectedItems: [{ uid: 'replacement' }] })
  await wrapper.vm.submit()
  expect(postAPI).not.toHaveBeenCalled()
  await wrapper.setData({ acknowledged: true })
  await wrapper.vm.submit()
  expect(postAPI.mock.calls.map(call => call[1].id)).toEqual(['a', 'b'])
  expect(wrapper.emitted('refresh-data')).toHaveLength(1)
  expect(wrapper.emitted('close-action')).toHaveLength(1)
  wrapper.unmount()
})

test('a single-rule action works without any selection', async () => {
  const wrapper = mountModal({ resource: rules[0] })
  await flushPromises()
  await wrapper.setData({ acknowledged: true })
  await wrapper.vm.submit()
  expect(postAPI).toHaveBeenCalledWith('pauseWallAlertRule', { id: 'a', paused: true })
  wrapper.unmount()
})

test('failed all-rules loading never falls back to the current selection', async () => {
  getAPI.mockRejectedValue(new Error('offline'))
  const wrapper = mountModal({ currentAction: { ...pause, wallRuleScope: 'all', invokedAsGroupAction: true }, selectedItems: rules })
  await flushPromises()
  await wrapper.setData({ acknowledged: true })
  await wrapper.vm.submit()
  expect(wrapper.vm.loadFailed).toBe(true)
  expect(wrapper.vm.targets).toEqual([])
  expect(postAPI).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('all-rules loading skips stopped rules and writes nothing before confirmation', async () => {
  getAPI.mockResolvedValue({ listwallalertrulesresponse: { wallalertruleresponse: [...rules, { uid: 'c', isPaused: true }], count: 3 } })
  const wrapper = mountModal({ currentAction: { ...pause, wallRuleScope: 'all', invokedAsGroupAction: true } })
  await flushPromises()
  expect(wrapper.vm.rules).toHaveLength(3)
  expect(wrapper.vm.targets).toHaveLength(2)
  expect(postAPI).not.toHaveBeenCalled()
  await wrapper.setData({ acknowledged: true })
  await wrapper.vm.submit()
  expect(postAPI).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})

test('partial failure stays open, refreshes completed work and retries only failed targets', async () => {
  postAPI.mockRejectedValueOnce(new Error('temporary failure')).mockResolvedValue({})
  const wrapper = mountModal({ currentAction: { ...pause, invokedAsGroupAction: true }, selectedItems: rules })
  await flushPromises()
  await wrapper.setData({ acknowledged: true })
  await wrapper.vm.submit()
  expect(wrapper.vm.failures.map(f => f.rule.uid)).toEqual(['a'])
  expect(wrapper.vm.errorText(wrapper.vm.failures[0].error)).toBe('temporary failure')
  expect(wrapper.emitted('close-action')).toBeUndefined()
  expect(wrapper.emitted('refresh-data')).toHaveLength(1)
  await wrapper.vm.submit()
  expect(postAPI.mock.calls.map(call => call[1].id)).toEqual(['a', 'b', 'a'])
  expect(wrapper.emitted('close-action')).toHaveLength(1)
  wrapper.unmount()
})

test('submitting twice or closing while running cannot duplicate or interrupt the batch', async () => {
  let finish
  postAPI.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const wrapper = mountModal({ resource: rules[0] })
  await flushPromises()
  await wrapper.setData({ acknowledged: true })
  const pending = wrapper.vm.submit()
  wrapper.vm.submit()
  wrapper.vm.close()
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(wrapper.emitted('close-action')).toBeUndefined()
  finish({})
  await pending
  wrapper.unmount()
})

test('toolbar respects API permissions and distinguishes selected and all scopes', async () => {
  const wrapper = shallowMount(Toolbar, {
    props: { actions: config.actions, selectedItems: rules },
    global: {
      mocks: { $t: translate, $store: { getters: { apis: { pauseWallAlertRule: {} } } } },
      stubs: {
        'a-space': { template: '<div><slot /></div>' },
        'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
        'a-menu': { template: '<div><slot /></div>' },
        'a-menu-item': { template: '<li><slot /></li>' },
        'a-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' }
      }
    }
  })
  expect(wrapper.vm.availableActions.map(a => a.wallRuleAction)).toEqual(['pause', 'resume'])
  expect(wrapper.vm.canApply(pause)).toBe(true)
  expect(wrapper.vm.canApply(config.actions.find(a => a.wallRuleAction === 'resume'))).toBe(false)
  await wrapper.findAll('button')[0].trigger('click')
  expect(wrapper.emitted('exec-action')[0][0]).toMatchObject({ wallRuleScope: 'selected', resource: undefined })
  await wrapper.findAll('li')[0].trigger('click')
  expect(wrapper.emitted('exec-action')[1][0]).toMatchObject({ wallRuleScope: 'all', resource: undefined })
  wrapper.unmount()
})

test('alert-rule list enables checkboxes and exposes eligible context actions for mixed states', async () => {
  const wrapper = shallowMount(ListView, {
    props: { columns: [], items: [...rules, { uid: 'c', isPaused: true }], actions: config.actions },
    global: {
      provide: { parentFetchData: jest.fn(), parentToggleLoading: jest.fn() },
      mocks: { $route: { name: 'alertRules', path: '/alertRules', meta: {} }, $store: { getters: { apis: { pauseWallAlertRule: {}, createWallAlertSilence: {} } } }, $t: translate }
    }
  })
  expect(wrapper.vm.enableGroupAction()).toBe(true)
  wrapper.vm.onSelectChange(['a', 'c'])
  await wrapper.vm.$nextTick()
  expect(wrapper.vm.selectedItems.map(r => r.uid)).toEqual(['a', 'c'])
  expect(wrapper.vm.contextMenuActions.map(a => a.wallRuleAction)).toEqual(['pause', 'resume', 'silence'])
  wrapper.unmount()
})

test.each([ko, en])('availability messages are complete sentences for every known state and unknown fallback', locale => {
  for (const state of ['Disabled', 'NotConfigured', 'Unavailable', 'ConnectionFailed', 'DnsError', 'TlsError', 'Timeout', 'AuthenticationFailed', 'InvalidResponse', 'Degraded', 'future-state']) {
    const vm = { wallState: state, $te: key => key in locale, $t: key => locale[key] || key }
    const message = AutoAlertBanner.computed.wallAvailabilityMessage.call(vm)
    expect(message).toBe((locale['message.wall.availability.' + state] || locale['message.wall.availability.Unavailable']) + ' ' + locale['message.wall.availability.impact'])
    expect(message).not.toContain('message.wall.')
    expect(message).not.toMatch(/비활성$|Disabled$/)
  }
})
