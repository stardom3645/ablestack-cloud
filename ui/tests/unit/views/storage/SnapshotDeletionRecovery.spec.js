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
import { shallowMount } from '@vue/test-utils'
import Recovery from '@/views/storage/SnapshotDeletionRecovery.vue'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const id = '00000000-0000-0000-0000-000000000001'
const zoneid = '00000000-0000-0000-0000-000000000002'
const preview = { snapshotid: id, name: 'Disposable fixture', state: 'Destroyed', scopehash: 'revision', reference: [{ referenceid: 7, state: 'Destroying', storename: 'Secondary' }] }
let wrapper
function mount () {
  wrapper = shallowMount(Recovery, { global: { renderStubDefaultSlot: true, mocks: { $t: x => x, $route: { path: '/snapshot' }, $store: { getters: { userInfo: { id: 'admin' }, project: {} } } } } })
  return wrapper
}
async function diagnosed () {
  mount(); await wrapper.setData({ snapshotId: id, zoneId: zoneid })
  postAPI.mockResolvedValueOnce({ deletesnapshotresponse: { jobid: 'diagnosis' } })
  getAPI.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 1, jobresult: preview } })
  await wrapper.vm.diagnose()
}
beforeEach(() => { getAPI.mockReset(); postAPI.mockReset() })
afterEach(() => wrapper?.unmount())

test('diagnosis does not perform deletion and requires confirmation', async () => {
  await diagnosed()
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(postAPI).toHaveBeenCalledWith('deleteSnapshot', { id, zoneid, dryrun: true })
  expect(wrapper.vm.preview).toEqual(preview)
  expect(wrapper.vm.canRecover).toBe(false)
})
test('input changes invalidate the inspected target and confirmation', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  expect(wrapper.vm.canRecover).toBe(true)
  await wrapper.setData({ zoneId: '' })
  expect(wrapper.vm.preview).toBeNull()
  expect(wrapper.vm.canRecover).toBe(false)
})
test('late diagnosis cannot overwrite a new target', async () => {
  mount(); await wrapper.setData({ snapshotId: id })
  let complete
  postAPI.mockReturnValueOnce(new Promise(resolve => { complete = resolve }))
  const pending = wrapper.vm.diagnose()
  await wrapper.setData({ snapshotId: zoneid })
  complete({ deletesnapshotresponse: preview }); await pending
  expect(wrapper.vm.preview).toBeNull()
})
test('execution submits only the inspected scope and revision once', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  postAPI.mockResolvedValueOnce({ deletesnapshotresponse: { jobid: 'delete-job' } })
  getAPI.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 1, jobresult: { success: true } } })
  const first = wrapper.vm.recover(); await wrapper.vm.recover(); await first
  expect(postAPI).toHaveBeenLastCalledWith('deleteSnapshot', { id, zoneid, expectedscopehash: 'revision' })
  expect(postAPI).toHaveBeenCalledTimes(2)
  expect(wrapper.vm.completed).toBe(true)
  expect(wrapper.emitted('refresh-data')).toHaveLength(1)
})
test('unknown job results retain the job and query again without resubmitting', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  postAPI.mockResolvedValueOnce({ deletesnapshotresponse: { jobid: 'delete-job' } })
  getAPI.mockRejectedValueOnce(new Error('network failure'))
  await wrapper.vm.recover()
  expect(wrapper.vm.pendingJob).toBe('delete-job')
  expect(wrapper.vm.canRecover).toBe(false)
  await wrapper.vm.recover()
  getAPI.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 1, jobresult: { success: true } } })
  await wrapper.vm.checkResult()
  expect(postAPI).toHaveBeenCalledTimes(2)
  expect(wrapper.vm.completed).toBe(true)
})
test('terminal job failure clears confirmation and requires a new diagnosis', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  postAPI.mockResolvedValueOnce({ deletesnapshotresponse: { jobid: 'delete-job' } })
  getAPI.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 2, jobresult: { errortext: 'store unavailable' } } })
  await wrapper.vm.recover()
  expect(wrapper.vm.pendingJob).toBe('')
  expect(wrapper.vm.preview).toBeNull()
  expect(wrapper.vm.error).toBe('store unavailable')
})
test('unknown submission without a job id cannot resubmit', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  postAPI.mockRejectedValueOnce(new Error('connection closed'))
  await wrapper.vm.recover(); await wrapper.vm.recover()
  expect(wrapper.vm.canRecover).toBe(false)
  expect(postAPI).toHaveBeenCalledTimes(2)
})
test('invalid UUID is rejected before any API request', async () => {
  mount(); await wrapper.setData({ snapshotId: 'wrong' }); await wrapper.vm.diagnose()
  expect(postAPI).not.toHaveBeenCalled()
})
test('project change disables execution and ignores diagnosis', async () => {
  await diagnosed(); await wrapper.setData({ acknowledged: true })
  wrapper.vm.$store.getters.project = { id: 'other' }
  expect(wrapper.vm.dialogCurrent()).toBe(false)
  await wrapper.vm.recover()
  expect(postAPI).toHaveBeenCalledTimes(1)
})
