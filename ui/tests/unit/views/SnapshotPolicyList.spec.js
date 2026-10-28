// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file for copyright ownership.
// Licensed under the Apache License, Version 2.0 (the "License"); you may obtain
// a copy at http://www.apache.org/licenses/LICENSE-2.0 . Distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
import Autogen from '@/views/AutogenView'
import { getAPI, callAPI } from '@/api'
import store from '@/store'
import { snapshotPolicyListRoute, snapshotPolicySortKeys, snapshotPolicyListQuery, snapshotPolicySortColumn, snapshotPolicySortQuery } from '@/utils/snapshotPolicyList'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn(), callAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: {}, state: { user: { token: 'session' } } }))
const volumeId = '22222222-2222-4222-8222-222222222222'; const domainId = '33333333-3333-4333-8333-333333333333'
const a = '11111111-1111-4111-8111-111111111111'; const z = '99999999-9999-4999-8999-999999999999'; const currentId = '55555555-5555-4555-8555-555555555555'
const scope = { keyword: '한글', q: '한글', volumeid: volumeId, account: 'owner', domainid: domainId, projectid: '0' }
const row = { id: a, volumeid: volumeId, account: 'owner', domainid: domainId, volumename: 'disk', intervaltype: 3, maxsnaps: 2, schedule: '59:23:28', timezone: 'UTC' }
const reply = (rows = [row], count = rows.length) => ({ listsnapshotpoliciesresponse: { snapshotpolicy: rows, count } })
let ctx
beforeEach(() => {
  jest.clearAllMocks()
  store.getters = { defaultListViewPageSize: 20, userInfo: { id: 'user', roletype: 'Admin' }, project: {}, apis: { listSnapshotPolicies: {}, createSnapshotPolicy: {} }, customColumns: {}, metrics: false, listAllProjects: false }
  store.state.user.token = 'session'
  const query = snapshotPolicyListQuery({ projectid: '0' })
  ctx = { $store: store, $route: { name: 'snapshotpolicy', path: '/snapshotpolicy', fullPath: '/snapshotpolicy?' + new URLSearchParams(query), params: {}, query, meta: { name: 'snapshotpolicy', permission: ['listSnapshotPolicies'], columns: ['intervaltype', 'maxsnaps', 'schedule', 'timezone', 'volumename'], actions: [], searchFilters: ['volumeid'] } }, $router: { replace: jest.fn(), push: jest.fn() }, $refs: { listview: { resetSelection: jest.fn() } }, $emit: jest.fn(), $t: key => key, $showIcon: () => false, $i18n: { locale: 'ko_KR' }, $message: { info: jest.fn() }, $notifyError: jest.fn(), $nextTick: callback => callback() }
  Object.assign(ctx, Autogen.data.call(ctx))
  Object.entries(Autogen.methods).forEach(([key, fn]) => { ctx[key] = fn.bind(ctx) })
  ctx.updateSelectedColumns = jest.fn(); getAPI.mockResolvedValue(reply())
})

test('logout clears the policy list without an unauthenticated refresh and ignores its old response', async () => {
  await ctx.fetchData(); let release
  getAPI.mockReturnValueOnce(new Promise(resolve => { release = resolve })); const old = ctx.fetchData()
  const count = getAPI.mock.calls.length; store.state.user.token = ''; store.getters.apis = {}
  await ctx.fetchData()
  expect(getAPI).toHaveBeenCalledTimes(count); expect(ctx.items).toEqual([]); expect(ctx.resource).toEqual({})
  expect(ctx.listLoadedScope).toBeNull(); expect(ctx.listRequestPending).toBe(false)
  release(reply()); await old; expect(ctx.items).toEqual([]); expect(ctx.$notifyError).not.toHaveBeenCalled()
})

test('detail to a forbidden list URL cannot retain an empty detail layout before the read guard', async () => {
  ctx.dataView = true; ctx.resource = { ...row }; store.getters.userInfo.roletype = 'User'
  ctx.$route.query = snapshotPolicyListQuery({ projectid: '0', fordisplay: 'false' })
  await ctx.fetchData()
  expect(ctx.dataView).toBe(false); expect(ctx.resource).toEqual({}); expect(getAPI).not.toHaveBeenCalled()
})

test.each(snapshotPolicySortKeys)('%s has a server sorter and both URL directions retain scope and reset page', key => {
  ctx.$route.query = { ...scope, page: '3' }
  for (const [order, direction] of [['ascend', 'asc'], ['descend', 'desc']]) {
    ctx.handleTableChange({}, {}, { field: key, order })
    const query = ctx.$router.push.mock.calls.at(-1)[0].query
    expect(query).toEqual({ ...scope, page: '1', pagesize: '20', sortkey: key, sortorder: direction })
    const column = snapshotPolicySortColumn(key, query, ctx.$t)
    expect(column.sorter).toBe(true); expect(column.sortOrder).toBe(order)
  }
})

test('legacy and malformed links normalize once, retain ACL filters and do not query before redirect', async () => {
  ctx.$route.query = { ...scope, sortkey: ['schedule'], sortorder: 'ASC;--', page: '-1', pagesize: 'NaN' }
  await ctx.fetchData()
  expect(ctx.$router.replace).toHaveBeenCalledWith({ query: { ...scope, page: '1', pagesize: '20', sortkey: 'id', sortorder: 'desc' } })
  expect(getAPI).not.toHaveBeenCalled(); expect(callAPI).not.toHaveBeenCalled()
  expect(snapshotPolicyListQuery({ ...scope, sortkey: 'MAXSNAPS', sortorder: 'ASC', page: '02', pagesize: '010' })).toEqual({ ...scope, sortkey: 'maxsnaps', sortorder: 'asc', page: '2', pagesize: '10' })
})

test.each([{ page: [], pagesize: true }, { page: '1.5', pagesize: '0' }, { page: '2147483648', pagesize: ['20'] }])('invalid pagination normalizes %j', query => {
  expect(snapshotPolicyListQuery(query)).toMatchObject({ page: '1', pagesize: '20' })
})

test('clear restores id-desc, unsupported fields cannot locally sort, and schedule has the documented tooltip', () => {
  expect(snapshotPolicySortQuery(scope, {}, 50)).toEqual({ ...scope, page: '1', pagesize: '50', sortkey: 'id', sortorder: 'desc' })
  expect(snapshotPolicySortColumn('unknown', {}, ctx.$t)).toEqual({ sorter: false, sortOrder: null })
  expect(snapshotPolicySortColumn('schedule', {}, ctx.$t).showSorterTooltip.title).toBe('message.snapshot.policy.schedule.sort')
  expect(snapshotPolicyListRoute({ name: 'snapshotpolicy', params: { id: 'detail' } })).toBe(false)
})

test.each(['한글', ''])('search keeps ownership, volume and sort and resets page (%s)', searchQuery => {
  ctx.$route.query = { ...scope, page: '3', sortkey: 'maxsnaps', sortorder: 'asc' }
  ctx.onSearch({ searchQuery })
  const result = ctx.$router.push.mock.calls.at(-1)[0].query
  expect(result).toMatchObject({ volumeid: volumeId, account: 'owner', domainid: domainId, projectid: '0', sortkey: 'maxsnaps', sortorder: 'asc', page: '1', pagesize: '20' })
  if (searchQuery) expect(result).toMatchObject({ keyword: searchQuery, q: searchQuery })
  else { expect(result.keyword).toBeUndefined(); expect(result.q).toBeUndefined() }
})

test('ordinary paging keeps sort, while both size change events reset page', () => {
  ctx.$route.query = { ...scope, sortkey: 'schedule', sortorder: 'asc' }
  ctx.changePage(2, 20)
  expect(ctx.$router.push.mock.calls.at(-1)[0].query.page).toBe(2)
  ctx.changePage(2, 50)
  expect(ctx.$router.push.mock.calls.at(-1)[0].query.page).toBe(1)
  ctx.changePageSize(2, 50)
  expect(ctx.$router.push.mock.calls.at(-1)[0].query).toEqual({ ...scope, page: 1, pagesize: 50, sortkey: 'schedule', sortorder: 'asc' })
})

test('real fetch forwards sorting and keeps server order/count; arrow changes when the cached schema is reused', async () => {
  getAPI.mockResolvedValue(reply([{ ...row, id: z, maxsnaps: 1 }, row], 2))
  ctx.$route.query = snapshotPolicyListQuery({ projectid: '0', sortkey: 'maxsnaps', sortorder: 'asc' }); ctx.$route.fullPath = '/snapshotpolicy?' + new URLSearchParams(ctx.$route.query)
  await ctx.fetchData()
  expect(getAPI).toHaveBeenCalledWith('listSnapshotPolicies', expect.objectContaining({ sortkey: 'maxsnaps', sortorder: 'asc', page: 1, pagesize: 20 }), { preserveOnFailure: true, timeout: 20000 })
  expect(ctx.items.map(x => x.id)).toEqual([z, a]); expect(ctx.itemCount).toBe(2)
  expect(ctx.columns.find(x => x.key === 'maxsnaps')).toMatchObject({ sorter: true, sortOrder: 'ascend' })
  ctx.$route.query.sortorder = 'desc'; ctx.$route.fullPath += '&new'; await ctx.fetchData()
  expect(ctx.columns.find(x => x.key === 'maxsnaps').sortOrder).toBe('descend')
  expect(ctx.columns.every(x => x.sorter === true)).toBe(true)
})

test('last-page deletion shrinks to the real last page with current sort and search', async () => {
  ctx.$route.query = snapshotPolicyListQuery({ ...scope, page: '2', pagesize: '20', sortkey: 'maxsnaps', sortorder: 'asc' }); ctx.$route.fullPath = '/snapshotpolicy?' + new URLSearchParams(ctx.$route.query)
  getAPI.mockResolvedValue({ listsnapshotpoliciesresponse: { count: 20 } })
  await ctx.fetchData()
  expect(ctx.$router.replace).toHaveBeenCalledWith({ query: { ...ctx.$route.query, page: '1' } })
  expect(ctx.items).toEqual([])
})

test('a zero-result API envelope without count returns to page one and retains filters', async () => {
  ctx.$route.query = snapshotPolicyListQuery({ projectid: '0', id: a, page: '2' }); ctx.$route.fullPath += '&empty'
  getAPI.mockResolvedValue({ listsnapshotpoliciesresponse: {} }); await ctx.fetchData()
  expect(ctx.$router.replace).toHaveBeenCalledWith({ query: { ...ctx.$route.query, page: '1' } })
})

test('canonical URL replacement immediately discards prior rows and outstanding requests', async () => {
  await ctx.fetchData(); const version = ctx.listRequestVersion; ctx.$route.query = { ...ctx.$route.query, sortkey: 'not-a-key' }; await ctx.fetchData()
  expect(ctx.items).toEqual([]); expect(ctx.listLoadedScope).toBeNull(); expect(ctx.listRequestVersion).toBeGreaterThan(version)
})

test('same-scope 503 retains rows and query, retry recovers, permission denial clears rows', async () => {
  await ctx.fetchData(); const rows = ctx.items; const query = { ...ctx.$route.query }
  getAPI.mockRejectedValueOnce({ response: { status: 503 } }); await ctx.fetchData()
  expect(ctx.items).toBe(rows); expect(ctx.listRefreshError).toBe(true); expect(ctx.$route.query).toEqual(query)
  getAPI.mockResolvedValue(reply()); await ctx.fetchData(); expect(ctx.listRefreshError).toBe(false)
  getAPI.mockRejectedValueOnce({ response: { status: 432 } }); await ctx.fetchData()
  expect(ctx.items).toEqual([]); expect(ctx.itemCount).toBe(0)
})

test.each(['sortorder', 'page', 'keyword', 'role', 'session'])('late response cannot replace newer %s scope', async change => {
  await ctx.fetchData()
  const complete = []; getAPI.mockImplementation(() => new Promise(resolve => complete.push(resolve)))
  const old = ctx.fetchData()
  if (change === 'role') store.getters.userInfo.roletype = 'User'
  else if (change === 'session') store.state.user.token = 'new-session'
  else { ctx.$route.query = { ...ctx.$route.query, [change]: change === 'page' ? '2' : change === 'sortorder' ? 'asc' : 'new' }; ctx.$route.fullPath += '&changed' }
  if (change === 'keyword') ctx.$route.query.q = ctx.$route.query.keyword
  const fresh = ctx.fetchData(); expect(ctx.items).toEqual([])
  complete[0](reply([{ ...row, id: z }])); await old; expect(ctx.items).toEqual([])
  complete[1](reply([{ ...row, id: currentId }], change === 'page' ? 21 : 1)); await fresh
  expect(ctx.items[0].id).toBe(currentId)
})
