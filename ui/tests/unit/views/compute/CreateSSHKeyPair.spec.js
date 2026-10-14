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
import CreateSSHKeyPair from '@/views/compute/CreateSSHKeyPair'
import { postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
let ctx, anchor
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
beforeEach(() => {
  jest.clearAllMocks()
  anchor = { click: jest.fn() }
  jest.spyOn(document, 'createElement').mockReturnValue(anchor)
  ctx = { $t: key => key, $store: { getters: { userInfo: { roletype: 'Admin' }, project: { id: 'project-1' } } }, $refs: { name: {}, publickey: {}, domainid: {}, account: {} }, $emit: jest.fn(), $notifyError: jest.fn(), $message: { success: jest.fn() }, $notification: { info: jest.fn() } }
  Object.assign(ctx, CreateSSHKeyPair.data.call(ctx))
  for (const mixin of CreateSSHKeyPair.mixins) Object.entries(mixin.methods).forEach(([key, method]) => { ctx[key] = method.bind(ctx) })
  Object.entries(CreateSSHKeyPair.methods).forEach(([key, method]) => { ctx[key] = method.bind(ctx) })
  ctx.form = { name: 'retained-key', publickey: 'invalid-key', domainid: 1, account: 'owner-1' }
  ctx.domains = [{ id: null }, { id: 'domain-1', path: '/domain-1' }]
  ctx.selectedDomain = ctx.domains[1]
  ctx.formRef = { value: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } }
  postAPI.mockResolvedValue({ registersshkeypairresponse: { keypair: { id: 'registered-id' } } })
})
afterEach(() => { jest.restoreAllMocks() })
test.each(['invalid public key', 'duplicate name', 'HTTP 503', 'connection lost'])('%s leaves the registration form, owner selection and project intact', async reason => {
  const failure = new Error(reason); postAPI.mockRejectedValueOnce(failure)
  const form = { ...ctx.form }; const selected = ctx.selectedDomain
  await ctx.handleSubmit({ preventDefault: jest.fn() })
  expect(ctx.form).toEqual(form)
  expect(ctx.selectedDomain).toBe(selected)
  expect(ctx.$store.getters.project.id).toBe('project-1')
  expect(ctx.loading).toBe(false)
  expect(ctx.isSubmitted).toBe(false)
  expect(ctx.hiddenElement).toBe(null)
  expect(ctx.$notifyError).toHaveBeenCalledWith(failure)
  expect(postAPI.mock.calls[0][2]).toEqual({ preserveOnFailure: true })
  expect(ctx.$message.success).not.toHaveBeenCalled()
  expect(ctx.$emit).not.toHaveBeenCalled()
})
test('the same form can be corrected and registered after failure', async () => {
  postAPI.mockRejectedValueOnce(new Error('invalid'))
  await ctx.handleSubmit()
  ctx.form.publickey = 'ssh-ed25519 valid-key'
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenNthCalledWith(2, 'registerSSHKeyPair', { name: 'retained-key', publickey: 'ssh-ed25519 valid-key', domainid: 'domain-1', account: 'owner-1' }, { preserveOnFailure: true })
  expect(ctx.$message.success).toHaveBeenCalledTimes(1)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data'], ['close-action']])
  expect(ctx.loading).toBe(false)
})
test('registration success refreshes before closing and never creates a private-key result', async () => {
  await ctx.handleSubmit({ name: 'form-finish-values' })
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data'], ['close-action']])
  expect(ctx.hiddenElement).toBe(null)
  expect(ctx.isSubmitted).toBe(false)
})
test('two submissions during asynchronous validation produce one request', async () => {
  let finish
  ctx.formRef.value.validate.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const pending = ctx.handleSubmit(); ctx.handleSubmit(); ctx.handleSubmit({ preventDefault: jest.fn() })
  expect(ctx.loading).toBe(true)
  expect(ctx.formRef.value.validate).toHaveBeenCalledTimes(1)
  expect(postAPI).not.toHaveBeenCalled()
  finish(); await pending
  expect(postAPI).toHaveBeenCalledTimes(1)
})
test.each([true, false])('pending API request blocks repeats and unlocks after success=%s', async success => {
  let finish, fail
  postAPI.mockImplementationOnce(() => new Promise((resolve, reject) => { finish = resolve; fail = reject }))
  const pending = ctx.handleSubmit(); await flush()
  ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1); expect(ctx.loading).toBe(true)
  if (success) finish({ registersshkeypairresponse: { keypair: {} } }); else fail(new Error('temporary failure'))
  await pending; expect(ctx.loading).toBe(false)
  expect(ctx.$emit).toHaveBeenCalledTimes(success ? 2 : 0)
})
test('required-field validation scrolls to the field and unlocks retry without an API call', async () => {
  ctx.formRef.value.validate.mockRejectedValueOnce({ errorFields: [{ name: ['name'] }] })
  await ctx.handleSubmit()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.formRef.value.scrollToField).toHaveBeenCalledWith(['name'])
  expect(ctx.loading).toBe(false)
  await ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('unexpected validation error is reported without losing the form', async () => {
  const failure = new Error('validation unavailable'); ctx.formRef.value.validate.mockRejectedValueOnce(failure)
  await ctx.handleSubmit()
  expect(ctx.$notifyError).toHaveBeenCalledWith(failure)
  expect(ctx.form.name).toBe('retained-key')
  expect(ctx.loading).toBe(false)
  expect(postAPI).not.toHaveBeenCalled()
})
test('hidden owner fields are omitted while visible input is preserved', async () => {
  ctx.$refs.account = null; ctx.$refs.domainid = null
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenCalledWith('registerSSHKeyPair', { name: 'retained-key', publickey: 'invalid-key' }, { preserveOnFailure: true })
  expect(ctx.form.account).toBe('owner-1')
})
test('default domain does not add an owner domain to registration', async () => {
  ctx.form.domainid = 0
  await ctx.handleSubmit()
  expect(postAPI.mock.calls[0][1]).not.toHaveProperty('domainid')
})
test('new key generation retains the private-key result and existing download/copy/close behavior', async () => {
  ctx.form.publickey = ''
  const key = 'unit-test-private-key\n'
  postAPI.mockResolvedValueOnce({ createsshkeypairresponse: { keypair: { privatekey: key } } })
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenCalledWith('createSSHKeyPair', { name: 'retained-key', domainid: 'domain-1', account: 'owner-1' }, { preserveOnFailure: true })
  expect(ctx.isSubmitted).toBe(true)
  expect(ctx.loading).toBe(false)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data']])
  expect(anchor.innerHTML).toBe(key)
  expect(anchor.href).toBe('data:text/plain;charset=utf-8,' + encodeURI(key))
  expect(anchor.download).toBe('retained-key.key')
  ctx.downloadKey(); expect(anchor.click).toHaveBeenCalledTimes(1)
  ctx.notifyCopied(); expect(ctx.$notification.info).toHaveBeenCalledWith({ message: 'message.success.copy.clipboard' })
  ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
  ctx.closeAction(); expect(ctx.$emit).toHaveBeenLastCalledWith('close-action')
})
test('new key generation failure retains inputs and can be retried', async () => {
  delete ctx.form.publickey; postAPI.mockRejectedValueOnce(new Error('duplicate'))
  await ctx.handleSubmit()
  expect(ctx.form.name).toBe('retained-key')
  expect(ctx.isSubmitted).toBe(false)
  expect(ctx.hiddenElement).toBe(null)
  expect(ctx.loading).toBe(false)
  expect(ctx.$emit).not.toHaveBeenCalled()
  postAPI.mockResolvedValueOnce({ createsshkeypairresponse: { keypair: { privatekey: 'unit-only-key' } } })
  await ctx.handleSubmit(); expect(ctx.isSubmitted).toBe(true)
})
