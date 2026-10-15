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

import RegisterUserData from '@/views/compute/RegisterUserData'
import { canonicalUserData, userDataName, userDataParams } from '@/utils/userDataRegistration'
import { postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }

test('plain text is UTF-8, never auto-detected as Base64', () => {
  expect(canonicalUserData('eA==', false)).toBe('ZUE9PQ==')
  expect(atob(canonicalUserData('한글\n', false))).toBe(unescape(encodeURIComponent('한글\n')))
})
test.each(['eA', 'eA==', 'eA%3D%3D', 'eA\n'])('normalizes permitted Base64 %s', value => {
  expect(canonicalUserData(value, true)).toBe('eA==')
})
test('binary Base64 and literal plus with percent padding remain supported', () => {
  expect(canonicalUserData('_wAB', true)).toBe('/wAB')
  expect(canonicalUserData('Zm+8/w8%3D', true)).toBe('Zm+8/w8=')
})
test.each(['%', '%GG', 'A', 'eA=', 'eA===', 'eA==Yg==', '!!!!'])('rejects malformed Base64 %s', value => {
  expect(() => canonicalUserData(value, true)).toThrow('base64')
})
test.each(['', ' ', '\t\n', '\u3000\u00a0'])('rejects empty or whitespace plain text and encoded content', value => {
  expect(() => canonicalUserData(value, false)).toThrow('empty')
  const encoded = btoa(unescape(encodeURIComponent(value)))
  expect(() => canonicalUserData(encoded, true)).toThrow()
})
test('canonical byte boundaries follow the supplied server limit, including padding', () => {
  expect(canonicalUserData('x'.repeat(24576), false, 32768).length).toBe(32768)
  expect(() => canonicalUserData('x'.repeat(24577), false, 32768)).toThrow('size')
  expect(canonicalUserData('eA', true, 4)).toBe('eA==')
  expect(() => canonicalUserData('eA', true, 3)).toThrow('size')
  expect(canonicalUserData('한'.repeat(8192), false, 32768).length).toBe(32768)
})
test('name and variable normalization and duplicate policy', () => {
  expect(userDataName('  이름  ')).toBe('이름')
  expect(() => userDataName('\u00a0')).toThrow('name')
  expect(() => userDataName('x'.repeat(256))).toThrow('name')
  expect(userDataParams([' USER ', 'user', 'env.name', 'test-key'])).toBe('USER,user,env.name,test-key')
  for (const params of [['x', ' x '], [' '], ['1x'], ['x=y'], ['a b']]) expect(() => userDataParams(params)).toThrow('params')
})

function context () {
  const ctx = { $route: { name: 'userdata' }, $t: key => key, $store: { getters: { userInfo: { roletype: 'User' } } }, $emit: jest.fn(), $notifyError: jest.fn(), $message: { success: jest.fn() } }
  Object.assign(ctx, RegisterUserData.data.call(ctx))
  Object.entries(RegisterUserData.methods).forEach(([key, method]) => { ctx[key] = method.bind(ctx) })
  ctx.form = { name: '  name  ', userdata: 'eA==', isbase64: false, params: [' USER '] }
  ctx.formRef = { value: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } }
  return ctx
}
beforeEach(() => jest.clearAllMocks())
test('server validation failures retain the complete form and allow retry', async () => {
  const ctx = context(); const before = { ...ctx.form }; const error = new Error('duplicate')
  postAPI.mockRejectedValueOnce(error)
  ctx.handleSubmit(); await flush()
  expect(ctx.form).toEqual(before); expect(ctx.loading).toBe(false)
  expect(ctx.$emit).not.toHaveBeenCalled(); expect(ctx.$notifyError).toHaveBeenCalledWith(error)
  expect(postAPI).toHaveBeenCalledWith('registerUserData', { name: 'name', userdata: 'ZUE9PQ==', params: 'USER' }, { preserveOnFailure: true })
  postAPI.mockResolvedValueOnce({}); ctx.handleSubmit(); await flush()
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data'], ['close-action']])
})
test('two queued validations submit only once', async () => {
  const ctx = context(); postAPI.mockReturnValue(new Promise(() => {}))
  ctx.handleSubmit(); ctx.handleSubmit(); await flush()
  expect(postAPI).toHaveBeenCalledTimes(1)
})
