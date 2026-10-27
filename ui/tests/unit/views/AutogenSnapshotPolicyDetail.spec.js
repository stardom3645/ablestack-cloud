// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See NOTICE for ownership information.
// Licensed under the Apache License, Version 2.0 (the "License");
// http://www.apache.org/licenses/LICENSE-2.0 . Distributed AS IS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
import Autogen from '@/views/AutogenView'
import store from '@/store'
import { getAPI, callAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn(), callAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: {}, state: { user: { token: 'session' } } }))
const id = '12345678-1234-1234-1234-123456789abc'
const row = () => ({ id, volumeid: id, volumename: 'disk', account: 'owner', domainid: id, intervaltype: 2, maxsnaps: 2, schedule: '5:3:2', timezone: 'UTC', schedulingenabled: false, tags: [], zone: [], storage: [], policycontext: { version: 1, volumeid: id, observedat: '2026-10-27T00:00:00Z', schedulingenabled: false, schedulestate: 'PAUSED', historysource: 'RETAINED_SCHEDULER_JOBS', historylimit: 50, currentjobknown: false, pollintervalseconds: 300 } })
const reply = p => ({ listsnapshotpoliciesresponse: { count: 1, snapshotpolicy: [p || row()] } })
let ctx
beforeEach(() => {
  jest.clearAllMocks(); store.getters = { userInfo: { id: 'user', roletype: 'Admin' }, apis: { listSnapshotPolicies: {} }, project: {}, customColumns: {}, defaultListViewPageSize: 20 }; store.state.user.token = 'session'
  ctx = { $store: store, $route: { name: 'snapshotpolicy', params: { id }, path: '/snapshotpolicy/' + id, fullPath: '/snapshotpolicy/' + id, query: {}, meta: { name: 'snapshotpolicy', permission: ['listSnapshotPolicies'], columns: ['intervaltype'], actions: [] } }, $router: { replace: jest.fn(), push: jest.fn() }, $refs: {}, $emit: jest.fn(), $t: k => k, $showIcon: () => false, $i18n: { locale: 'ko_KR' }, $nextTick: f => f(), $notifyError: jest.fn() }
  Object.assign(ctx, Autogen.data.call(ctx)); Object.entries(Autogen.methods).forEach(([k, f]) => { ctx[k] = f.bind(ctx) })
  for (const k of ['isSnapshotList', 'isSnapshotDetail', 'isPolicyDetail', 'isSnapshotReadView', 'storageActionReady']) Object.defineProperty(ctx, k, { get: () => Autogen.computed[k].call(ctx) })
  ctx.updateSelectedColumns = jest.fn(); getAPI.mockResolvedValue(reply())
})
test('detail failure and incomplete refresh preserve last data/time and block actions; retry recovers', async () => {
  await ctx.fetchData(); expect(ctx.resource.id).toBe(id); const old = ctx.resource; const time = ctx.listLastUpdated
  getAPI.mockRejectedValueOnce({ response: { status: 503 } }); await ctx.fetchData(); expect(ctx.resource).toBe(old); expect(ctx.storageActionReady).toBe(false); expect(ctx.listLastUpdated).toBe(time)
  getAPI.mockResolvedValueOnce(reply({ ...row(), policycontext: undefined })); await ctx.fetchData(); expect(ctx.resource).toBe(old); expect(ctx.snapshotListError).toBe('message.snapshot.policy.detail.incomplete')
  getAPI.mockResolvedValue(reply()); await ctx.fetchData(); expect(ctx.storageActionReady).toBe(true); expect(callAPI).not.toHaveBeenCalled()
})
test.each([403, 531, 404])('permission or missing %s clears successful data', async status => {
  await ctx.fetchData(); getAPI.mockRejectedValue({ response: { status } }); await ctx.fetchData(); expect(ctx.resource).toEqual({}); expect(ctx.storageActionReady).toBe(false)
})
test.each(['user', 'role', 'token', 'project', 'permissions'])('%s scope transition removes old data and discards late results', async change => {
  await ctx.fetchData(); const first = ctx.listScope(); let resolve
  getAPI.mockImplementationOnce(() => new Promise(r => { resolve = r })); const pending = ctx.fetchData(); await Promise.resolve()
  if (change === 'user') store.getters.userInfo.id = 'new'
  if (change === 'role') store.getters.userInfo.roleid = 'new'
  if (change === 'token') store.state.user.token = 'new'
  if (change === 'project') store.getters.project = { id }
  if (change === 'permissions') store.getters.apis.listVolumes = {}
  expect(ctx.listScope()).not.toBe(first); getAPI.mockResolvedValueOnce({ listsnapshotpoliciesresponse: {} }); const newer = ctx.fetchData(); expect(ctx.resource).toEqual({})
  resolve(reply()); await pending; await newer; expect(ctx.resource).toEqual({})
})
