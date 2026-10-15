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

<template>
  <section class="userdata-selection">
    <div class="userdata-selection-toolbar">
      <a-radio :checked="selectedId === noSelection" :disabled="selectionDisabled" @change="selectItem()">{{ $t('label.noselect') }}</a-radio>
      <a-input-search
        v-if="showSearch"
        v-model:value="filter"
        :disabled="disabled || !allowed || !ownerReady"
        :placeholder="$t('label.search')"
        :aria-label="$t('label.user.data') + ' ' + $t('label.search')"
        @keydown.enter.stop.prevent
        @keyup.enter.stop
        @search="handleSearch" />
    </div>
    <a-alert v-if="listError || selectionError" type="error" show-icon role="alert" :message="$t(listError || selectionError)">
      <template #action><a-button size="small" :loading="loading" @click="load(requestedQuery)">{{ $t('label.refresh') }}</a-button></template>
    </a-alert>
    <p v-if="selectedId !== noSelection" role="status">{{ $t('message.userdata.selection.selected', { name: selectedItem?.name || selectedId }) }}</p>
    <a-table
      :loading="loading"
      :columns="columns"
      :data-source="items"
      row-key="id"
      :row-selection="rowSelection"
      :custom-row="onClickRow"
      :pagination="pagination"
      size="middle"
      :scroll="{ x: 480, y: 225 }"
      @change="handleTableChange">
      <template #headerCell="{ column }">
        <template v-if="column.key === 'name'"><solution-outlined /> {{ $t('label.user.data') }}</template>
        <template v-if="column.key === 'account'"><user-outlined /> {{ $t('label.account') }}</template>
        <template v-if="column.key === 'domain'"><block-outlined /> {{ $t('label.domain') }}</template>
      </template>
    </a-table>
  </section>
</template>

<script>
import { getAPI } from '@/api'
import { NO_USER_DATA, userDataOwnerParams } from '@/utils/userDataSelection'

export default {
  name: 'UserDataSelection',
  props: {
    scope: { type: Object, default: () => ({}) },
    value: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    preFillContent: { type: Object, default: () => ({}) },
    zoneId: { type: String, default: '' },
    showSearch: { type: Boolean, default: true }
  },
  emits: ['select-user-data-item', 'ready'],
  data () {
    return {
      noSelection: NO_USER_DATA,
      selectedId: this.value || this.preFillContent.userdataid || NO_USER_DATA,
      selectedItem: null,
      initializeSelection: true,
      items: [],
      total: 0,
      filter: '',
      query: { page: 1, pageSize: 10, keyword: '' },
      requestedQuery: { page: 1, pageSize: 10, keyword: '' },
      loading: false,
      listError: '',
      selectionError: '',
      generation: 0,
      active: true,
      columns: [
        { key: 'name', dataIndex: 'name', width: '40%' },
        { key: 'account', dataIndex: 'account', width: '30%' },
        { key: 'domain', dataIndex: 'domain', width: '30%' }
      ]
    }
  },
  computed: {
    allowed () { return Boolean(this.$store.getters.apis?.listUserData) },
    owner () { return userDataOwnerParams(this.scope) },
    ownerReady () { return Boolean(this.owner.projectid || (this.owner.account && this.owner.domainid)) },
    scopeKey () { return JSON.stringify([this.owner, this.zoneId, this.allowed]) },
    selectionDisabled () { return this.disabled || this.loading || Boolean(this.listError) || !this.allowed || !this.ownerReady },
    ready () { return !this.loading && !this.listError && !this.selectionError && this.allowed && this.ownerReady },
    pagination () {
      return {
        current: this.query.page,
        pageSize: this.query.pageSize,
        total: this.total,
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50', '100'],
        showTotal: total => this.$t('label.total') + ': ' + total
      }
    },
    rowSelection () {
      return {
        type: 'radio',
        selectedRowKeys: this.selectedId === NO_USER_DATA ? [] : [this.selectedId],
        preserveSelectedRowKeys: true,
        getCheckboxProps: () => ({ disabled: this.selectionDisabled }),
        onChange: (keys, rows) => this.selectItem(rows[0])
      }
    }
  },
  watch: {
    ready: { immediate: true, handler (ready) { this.$emit('ready', ready) } },
    scopeKey () {
      this.filter = ''
      this.items = []
      this.total = 0
      this.initializeSelection = false
      this.selectItem(undefined, true)
      this.load({ page: 1, pageSize: 10, keyword: '' })
    },
    value (value) {
      const id = value || NO_USER_DATA
      if (id === this.selectedId) return
      this.selectedId = id
      this.selectedItem = null
      this.selectionError = ''
      if (id !== NO_USER_DATA) {
        this.initializeSelection = true
        this.load(this.requestedQuery)
      }
    }
  },
  created () { this.load(this.query) },
  beforeUnmount () { this.active = false; this.generation++ },
  methods: {
    async load (query) {
      const generation = ++this.generation
      const current = () => this.active && generation === this.generation
      this.requestedQuery = { ...query }
      this.listError = ''
      this.loading = true
      if (!this.allowed || !this.ownerReady) {
        this.listError = this.allowed ? 'message.userdata.selection.scope' : 'message.userdata.selection.forbidden'
        this.loading = false
        return
      }
      const owner = { ...this.owner, ignoreproject: true, listall: false }
      try {
        const requested = { ...query }
        let result
        // Recover deleted pages with a bounded fallback, never an unbounded retry.
        for (let attempt = 0; attempt < 3; attempt++) {
          const json = await getAPI('listUserData', { ...owner, page: requested.page, pagesize: requested.pageSize, keyword: requested.keyword || undefined }, { preserveOnFailure: true, timeout: 15000 })
          if (!current()) return
          result = json?.listuserdataresponse
          if (!result || result.errorcode) throw new Error('Invalid listUserData response')
          const total = Number(result.count || 0)
          const rows = result.userdata || []
          if (rows.length || requested.page === 1) break
          requested.page = attempt === 1 ? 1 : Math.max(1, Math.min(requested.page - 1, Math.ceil(total / requested.pageSize)))
        }
        if (!current()) return
        this.items = result.userdata || []
        this.total = Number(result.count || 0)
        this.query = { ...requested }
        this.requestedQuery = { ...requested }
        this.selectionError = ''
        if (this.selectedId !== NO_USER_DATA) {
          const id = this.selectedId
          let item = this.items.find(item => item.id === id)
          if (!item) {
            const json = await getAPI('listUserData', { ...owner, id }, { preserveOnFailure: true, timeout: 15000 })
            if (!current() || this.selectedId !== id) return
            item = json?.listuserdataresponse?.userdata?.find(item => item.id === id)
          }
          if (!item) this.selectionError = 'message.userdata.selection.missing'
          else {
            this.selectedItem = item
            if (this.initializeSelection) this.$emit('select-user-data-item', id, item)
          }
        } else if (this.initializeSelection) this.$emit('select-user-data-item', NO_USER_DATA)
        this.initializeSelection = false
      } catch (error) {
        if (!current()) return
        const code = error?.response?.data?.errorresponse?.errorcode
        this.listError = [401, 403].includes(error?.response?.status) || code === 432
          ? 'message.userdata.selection.forbidden' : 'message.userdata.selection.failed'
      } finally {
        if (current()) this.loading = false
      }
    },
    selectItem (item, force = false) {
      if (!force && this.selectionDisabled) return
      if (item?.id === this.selectedId && this.selectedItem && !this.selectionError) return
      this.selectedId = item?.id || NO_USER_DATA
      this.selectedItem = item || null
      this.selectionError = ''
      this.initializeSelection = false
      this.$emit('select-user-data-item', this.selectedId, item)
    },
    handleSearch (keyword) {
      this.filter = keyword
      this.load({ page: 1, pageSize: this.query.pageSize, keyword: keyword.trim() })
    },
    handleTableChange (pagination) {
      this.load({ ...this.query, page: pagination.pageSize === this.query.pageSize ? pagination.current : 1, pageSize: pagination.pageSize })
    },
    onClickRow (item) { return { onClick: () => this.selectItem(item) } }
  }
}
</script>

<style lang="less" scoped>
.userdata-selection { min-width: 0; max-width: 100%; }
.userdata-selection-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.userdata-selection-toolbar .ant-input-search { width: 320px; max-width: 100%; }
.userdata-selection .ant-alert { margin-bottom: 12px; }
.userdata-selection .ant-table-wrapper { margin: 12px 0; }
:deep(.ant-table-cell) { overflow-wrap: anywhere; }
:deep(.ant-table-tbody) > tr > td { cursor: pointer; }
</style>
