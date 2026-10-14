// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.
import { shallowMount } from '@vue/test-utils'
import { h } from 'vue'
import ListView from '@/components/view/ListView'
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}) }))
const id = '11111111-1111-1111-1111-111111111111'
function cell (record, path = '/ssh', resourcePath = '') {
  return shallowMount(ListView, {
    props: { columns: [{ key: 'name', dataIndex: 'name' }], items: [record], resourcePath },
    global: {
      provide: { parentFetchData: jest.fn(), parentToggleLoading: jest.fn() },
      mocks: { $route: { name: 'ssh', path, meta: {} }, $store: { getters: { apis: {}, userInfo: { roletype: 'Admin' } } }, $t: key => key, $showIcon: () => false },
      stubs: { 'a-table': { render () { return h('div', this.$slots.bodyCell({ column: { key: 'name' }, text: record.name, record })) } }, 'router-link': { props: ['to'], template: '<a :data-target="JSON.stringify(to)"><slot /></a>' } }
    }
  })
}
test.each([false, true])('SSH annotation=%s has a canonical UUID/owner name link', hasannotations => {
  const wrapper = cell({ id, name: 'same', account: 'admin', domainid: id, hasannotations })
  const links = wrapper.findAll('a').map(link => JSON.parse(link.attributes('data-target')))
  expect(links[0]).toEqual({ path: '/ssh/' + id, query: { account: 'admin', domainid: id } })
  expect(links.length).toBe(hasannotations ? 2 : 1)
  if (hasannotations) expect(links[1]).toEqual({ path: '/ssh/' + id, query: { account: 'admin', domainid: id, tab: 'comments' } })
  wrapper.unmount()
})
test('a project SSH reference includes project context in both links', () => {
  const wrapper = cell({ id, name: 'same', projectid: id, hasannotations: true })
  wrapper.findAll('a').forEach(link => expect(JSON.parse(link.attributes('data-target')).query.projectid).toBe(id))
  wrapper.unmount()
})
test('embedded SSH lists use the same reference contract', () => {
  const wrapper = cell({ id, name: 'same', account: 'admin' }, '/vm/one', '/ssh')
  expect(JSON.parse(wrapper.find('a').attributes('data-target'))).toEqual({ path: '/ssh/' + id, query: { account: 'admin' } })
  wrapper.unmount()
})
test('non-SSH comment links retain their existing UUID/tab contract', () => {
  const wrapper = cell({ id, name: 'same', hasannotations: true }, '/vm')
  expect(wrapper.findAll('a').map(link => JSON.parse(link.attributes('data-target')))).toEqual([{ path: '/vm/' + id }, { path: '/vm/' + id, query: { tab: 'comments' } }])
  wrapper.unmount()
})
test('VM tables embedded in an SSH detail do not turn their VM IDs into SSH links', () => {
  const wrapper = cell({ id, name: 'same' }, '/ssh/one', '/vm')
  expect(JSON.parse(wrapper.find('a').attributes('data-target')).path).toBe('/vm/' + id)
  wrapper.unmount()
})
