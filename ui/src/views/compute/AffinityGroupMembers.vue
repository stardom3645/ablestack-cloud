<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this
work for additional information regarding copyright ownership. The ASF
licenses this file to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance with the License.
You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. -->
<template>
  <section class="affinity-members">
    <h3>{{ $t('label.affinity.members') }}</h3>
    <p>{{ $t('message.affinity.members.help') }}</p>
    <p role="status">{{ $t('label.affinitymembercount') }}: {{ memberCount }} · {{ $t('label.affinity.members.visible') }}: {{ total }}</p>
    <div class="affinity-members-toolbar">
      <a-button type="primary" :disabled="!canChange || busy" @click="openAdd">{{ $t('label.affinity.members.add') }}</a-button>
      <a-button :disabled="!canChange || busy || !selection.length || !!error" @click="confirmRemove">{{ $t('label.affinity.members.remove') }} ({{ selection.length }})</a-button>
      <a-button v-if="batch.targets.length" @click="dialog = 'results'">{{ $t('label.affinity.members.results') }}</a-button>
      <a-button :loading="loading" :aria-label="$t('label.refresh')" @click="fetchData"><template #icon><reload-outlined /></template></a-button>
      <a-input-search v-model:value="draftKeyword" :placeholder="$t('label.search')" allow-clear @search="search" />
    </div>
    <a-alert v-if="error" type="error" show-icon :message="text(error)"><template #description><a-button @click="fetchData">{{ $t('label.retry') }}</a-button></template></a-alert>
    <p v-if="!canRead" role="alert">{{ $t('message.affinity.members.restricted') }}</p>
    <p v-else-if="!keyword && !error && memberCount > total">{{ $t('message.affinity.members.restricted') }}</p>
    <p v-if="selection.length">{{ $t('label.selected') }}: {{ selection.length }} <a-button size="small" @click="selection = []; selected = {}">{{ $t('label.clear') }}</a-button></p>
    <a-table
:columns="columns"
:data-source="rows"
:loading="loading"
:row-key="row => row.id"
:pagination="false"
:scroll="{ x: 960 }"
      :locale="{ emptyText: $t(canRead ? 'message.affinity.members.empty' : 'message.affinity.members.restricted') }"
      :row-selection="canChange ? { selectedRowKeys: selection, preserveSelectedRowKeys: true, onChange: selectMembers, getCheckboxProps: vm => ({ disabled: !eligible(vm) || busy || !!error }) } : undefined">
      <template #bodyCell="{ column, record, text: value }">
        <template v-if="column.key === 'displayname'"><router-link :to="vmLink(record)">{{ record.displayname || record.name }}</router-link><div>{{ record.name }}</div></template>
        <template v-else-if="column.key === 'state'"><Status :text="record.state" /></template>
        <template v-else-if="column.key === 'reason'">{{ eligible(record) ? '' : $t('message.affinity.vm.stopped') }}</template>
        <template v-else>{{ value || $t('label.affinity.members.unavailable') }}</template>
      </template>
    </a-table>
    <a-pagination
class="affinity-members-pagination"
:current="page"
:page-size="pagesize"
:total="total"
:page-size-options="['2', '10', '20', '50']"
show-size-changer
@change="changePage"
@showSizeChange="changePage" />

    <MoldDialog v-if="dialog" size="lg" :title="$t('label.affinity.members.' + (dialog === 'results' ? 'results' : mode))" @cancel="closeDialog">
      <p>{{ resource.name }} · {{ resource.project || resource.account }} / {{ resource.domain }}</p>
      <p>{{ $t('message.affinity.members.help') }}</p>
      <template v-if="dialog === 'choose'">
        <a-input-search v-model:value="candidateDraft" :placeholder="$t('label.search')" allow-clear @search="candidateSearch" />
        <a-alert v-if="candidateError" type="error" :message="text(candidateError)"><template #description><a-button @click="fetchCandidates">{{ $t('label.retry') }}</a-button></template></a-alert>
        <p>{{ $t('label.selected') }}: {{ candidateSelection.length }} · {{ $t('message.affinity.members.page.selection') }}</p>
        <a-button v-if="candidateSelection.length" size="small" @click="candidateSelection = []; candidatesSelected = {}">{{ $t('label.clear') }}</a-button>
        <a-table
:columns="columns"
:data-source="candidates"
:row-key="row => row.id"
:loading="candidateLoading"
:pagination="false"
:scroll="{ x: 900 }"
          :row-selection="{ selectedRowKeys: candidateSelection, preserveSelectedRowKeys: true, onChange: selectCandidates, getCheckboxProps: vm => ({ disabled: !eligible(vm) || linked(vm) || !!candidateError || candidateLoading }) }">
          <template #bodyCell="{ column, record, text: value }">
            <template v-if="column.key === 'displayname'"><router-link :to="vmLink(record)">{{ record.displayname || record.name }}</router-link><div>{{ record.name }}</div></template>
            <template v-else-if="column.key === 'reason'">{{ linked(record) ? $t('message.affinity.members.already') : (!eligible(record) ? $t('message.affinity.vm.stopped') : '') }}</template>
            <template v-else>{{ value || $t('label.affinity.members.unavailable') }}</template>
          </template>
        </a-table>
        <a-pagination :current="candidatePage" :page-size="10" :total="candidateTotal" @change="candidatePage = $event; fetchCandidates()" />
      </template>
      <template v-else>
        <p v-if="dialog === 'confirm'">{{ $t('message.affinity.members.confirm') }}</p>
        <ul class="affinity-member-results">
          <li v-for="vm in batch.targets" :key="vm.id">
            <router-link :to="vmLink(vm)">{{ vm.displayname || vm.name }}</router-link>
            <span v-if="batch.results[vm.id]"> · {{ $t('message.affinity.operation.' + batch.results[vm.id].status) }}</span>
            <div v-if="batch.results[vm.id]?.error" role="alert">{{ text(batch.results[vm.id].error) }}</div>
            <div v-if="batch.results[vm.id]?.jobId">{{ $t('label.affinity.job') }}: {{ batch.results[vm.id].jobId }}</div>
            <p v-if="batch.results[vm.id]?.status === 'unknown' && !batch.results[vm.id]?.jobId">{{ $t('message.affinity.operation.no.receipt') }}</p>
            <a-button v-if="recheckable(batch.results[vm.id])" size="small" @click="recheck(vm)">{{ $t('label.affinity.recheck') }}</a-button>
          </li>
        </ul>
      </template>
      <template #footer>
        <a-button @click="closeDialog">{{ $t('label.close') }}</a-button>
        <a-button v-if="dialog === 'choose'" type="primary" :disabled="!candidateSelection.length || !!candidateError || candidateLoading" @click="confirmAdd">{{ $t('label.next') }}</a-button>
        <a-button v-if="dialog === 'confirm'" type="primary" :loading="busy" :disabled="!canChange" @click="execute(false)">{{ $t('label.ok') }}</a-button>
        <a-button v-if="dialog === 'results' && hasIdle" type="primary" :loading="busy" :disabled="!canChange" @click="execute(false)">{{ $t('label.affinity.members.continue') }}</a-button>
        <a-button v-if="dialog === 'results' && hasFailed" type="primary" :loading="busy" :disabled="!canChange" @click="execute(true)">{{ $t('label.affinity.members.retry') }}</a-button>
      </template>
    </MoldDialog>
  </section>
</template>
<script>
import { reactive } from 'vue'
import { getAPI, postAPI } from '@/api'
import MoldDialog from '@/components/view/MoldDialog.vue'
import Status from '@/components/widgets/Status'
import { affinityOwnerKey, affinitySessionScope, affinityVmCanChange, affinityApiError, getAffinityOperation } from '@/utils/affinityGroupOperations'
import { affinityMemberCount, affinityVmIds, affinityMemberSnapshot, affinityMemberLocked, affinityMemberPage, affinityMemberParams, prepareAffinityMemberChange, affinityMemberBatch } from '@/utils/affinityGroupMembers'
import { listRefreshMixin } from '@/utils/listRefreshMixin'

export default {
  name: 'AffinityGroupMembers',
  components: { MoldDialog, Status },
  mixins: [listRefreshMixin(['fetchData'], { active: vm => vm.active && !vm.dialog && !vm.busy })],
  props: { resource: { type: Object, required: true }, active: { type: Boolean, default: true } },
  inject: { parentFetchData: { default: () => () => {} } },
  data: () => ({
    rows: [],
    total: 0,
    memberCount: 0,
    error: '',
    loading: false,
    page: 1,
    pagesize: 10,
    keyword: '',
    draftKeyword: '',
    selection: [],
    selected: {},
    dialog: '',
    mode: '',
    candidates: [],
    candidateTotal: 0,
    candidatePage: 1,
    candidateKeyword: '',
    candidateDraft: '',
    candidateError: '',
    candidateLoading: false,
    candidateSelection: [],
    candidatesSelected: {},
    batch: { mode: '', targets: [], results: {}, busy: false }
  }),
  computed: {
    securityScope () { return affinitySessionScope(this.$store) },
    scope () { try { return [this.securityScope, this.resource.id, affinityOwnerKey(this.resource)].join('|') } catch (_) { return '' } },
    canRead () { return !!this.scope && 'listVirtualMachines' in this.$store.getters.apis },
    canChange () { return this.canRead && 'updateVMAffinityGroup' in this.$store.getters.apis && 'listAffinityGroups' in this.$store.getters.apis },
    busy () { return this.batch.busy || Object.values(this.batch.results).some(row => ['preparing', 'submitting', 'pending', 'retrying'].includes(row.status)) },
    hasFailed () { return Object.values(this.batch.results).some(row => row.status === 'failed') },
    hasIdle () { return Object.values(this.batch.results).some(row => row.status === 'idle') },
    columns () { return ['displayname', 'state', 'hostname', 'account', 'project', 'domain', 'reason'].map(key => ({ key, dataIndex: key, title: this.$t('label.' + (key === 'reason' ? 'affinity.members.reason' : key)) })) }
  },
  created () { this.alive = true; this.version = 0; this.candidateVersion = 0; this.unsubscribers = []; this.bindBatch(); this.fetchData() },
  beforeUnmount () { this.alive = false; this.version++; this.candidateVersion++; this.unsubscribers.forEach(fn => fn()) },
  watch: {
    scope () { this.version++; this.candidateVersion++; this.unsubscribers.forEach(fn => fn()); this.unsubscribers = []; this.rows = []; this.total = 0; this.memberCount = 0; this.selection = []; this.selected = {}; this.error = ''; this.dialog = ''; this.page = 1; this.keyword = ''; this.draftKeyword = ''; this.bindBatch(); this.fetchData() },
    active (value) { if (value) this.fetchData(); else { this.version++; this.candidateVersion++; this.dialog = '' } }
  },
  methods: {
    text (value) { return value.startsWith('message.') ? this.$t(value) : value },
    eligible: affinityVmCanChange,
    linked (vm) { return affinityVmIds(vm).includes(this.resource.id) },
    vmLink (vm) { return { path: '/vm/' + vm.id, query: vm.projectid ? { projectid: vm.projectid } : {} } },
    recheckable (row) { return row?.jobId && ['unknown', 'timeout', 'cancelled'].includes(row.status) },
    operation (vm) { const store = this.$store; return getAffinityOperation(this.securityScope, vm.id, { get: getAPI, post: postAPI, getScope: () => affinitySessionScope(store) }) },
    bindBatch () {
      this.batch = reactive(affinityMemberBatch(this.securityScope, this.resource.id))
      for (const vm of this.batch.targets) this.observe(vm)
    },
    observe (vm) {
      const scope = this.scope
      this.unsubscribers.push(this.operation(vm).subscribe(state => { if (this.alive && scope === this.scope) this.batch.results[vm.id] = state }))
    },
    async fetchData () {
      if (!this.active || !this.canRead) return
      const version = ++this.version; const scope = this.scope
      const current = () => this.alive && this.active && version === this.version && scope === this.scope
      this.loading = true
      try {
        const [response, groupResponse] = await Promise.all([
          getAPI('listVirtualMachines', affinityMemberParams(this.resource, this.page, this.pagesize, this.keyword), { preserveOnFailure: true, timeout: 15000 }),
          getAPI('listAffinityGroups', { id: this.resource.id, listall: true, projectid: this.resource.projectid || '-1' }, { preserveOnFailure: true, timeout: 15000 })
        ])
        const group = groupResponse?.listaffinitygroupsresponse?.affinitygroup?.find(row => row.id === this.resource.id)
        if (!group || affinityOwnerKey(group) !== affinityOwnerKey(this.resource)) throw new Error('message.affinity.owner.changed')
        const { rows, count } = affinityMemberPage(response)
        if (!current()) return
        this.error = ''; this.memberCount = affinityMemberCount(group)
        if (this.page > Math.max(1, Math.ceil(count / this.pagesize))) { this.page = Math.max(1, Math.ceil(count / this.pagesize)); this.fetchData(); return }
        this.rows = rows; this.total = count
      } catch (error) { if (current()) this.error = affinityApiError(error) || error.message || 'message.affinity.members.lookup' } finally { if (current()) this.loading = false }
    },
    search (keyword) { this.keyword = keyword; this.page = 1; this.fetchData() },
    changePage (page, size) { this.page = size !== this.pagesize ? 1 : page; this.pagesize = size; this.fetchData() },
    selectMembers (ids) { this.selection = [...new Set(ids)]; for (const vm of this.rows) if (ids.includes(vm.id) && !this.selected[vm.id]) this.selected[vm.id] = affinityMemberSnapshot(vm); for (const id of Object.keys(this.selected)) if (!ids.includes(id)) delete this.selected[id] },
    selectCandidates (ids) { this.candidateSelection = [...new Set(ids)]; for (const vm of this.candidates) if (ids.includes(vm.id) && !this.candidatesSelected[vm.id]) this.candidatesSelected[vm.id] = affinityMemberSnapshot(vm); for (const id of Object.keys(this.candidatesSelected)) if (!ids.includes(id)) delete this.candidatesSelected[id] },
    openAdd () {
      if (!this.canChange || this.busy) return
      this.mode = 'add'; this.dialog = 'choose'; this.candidatePage = 1; this.candidateKeyword = ''; this.candidateDraft = ''; this.candidateSelection = []; this.candidatesSelected = {}; this.candidates = []; this.candidateError = ''; this.fetchCandidates()
    },
    closeDialog () { this.dialog = ''; this.candidateVersion++ },
    candidateSearch (keyword) { this.candidateKeyword = keyword; this.candidatePage = 1; this.fetchCandidates() },
    async fetchCandidates () {
      const version = ++this.candidateVersion; const scope = this.scope
      const current = () => this.alive && this.active && scope === this.scope && this.dialog === 'choose' && version === this.candidateVersion
      this.candidateLoading = true
      try {
        const value = affinityMemberPage(await getAPI('listVirtualMachines', affinityMemberParams(this.resource, this.candidatePage, 10, this.candidateKeyword, false), { preserveOnFailure: true, timeout: 15000 }))
        if (!current()) return
        this.candidates = value.rows; this.candidateTotal = value.count; this.candidateError = ''
      } catch (error) { if (current()) this.candidateError = affinityApiError(error) || 'message.affinity.members.lookup' } finally { if (current()) this.candidateLoading = false }
    },
    confirmAdd () { if (!this.candidateError && !this.candidateLoading) this.confirm('add', this.candidateSelection.map(id => this.candidatesSelected[id])) },
    confirmRemove () { if (!this.error) this.confirm('remove', this.selection.map(id => this.selected[id])) },
    confirm (mode, targets) {
      if (!this.canChange || this.busy || !targets.length || targets.some(vm => !vm || !this.eligible(vm))) return
      // Explicit confirmation starts a new batch. Never discard unresolved receipts.
      if (Object.values(this.batch.results).some(row => affinityMemberLocked(row.status))) { this.dialog = 'results'; return }
      this.unsubscribers.forEach(fn => fn()); this.unsubscribers = []
      this.batch.mode = mode; this.mode = mode; this.batch.targets = targets.map(affinityMemberSnapshot); this.batch.results = {}
      for (const vm of targets) { const op = this.operation(vm); if (op.snapshot().status === 'succeeded') op.acknowledge(); this.observe(vm) }
      this.dialog = 'confirm'
    },
    async execute (retry) {
      if (!this.canChange || this.busy || !this.batch.targets.length) return
      const scope = this.scope; const group = { ...this.resource }; const batch = this.batch
      const current = () => this.alive && this.active && scope === this.scope && this.canChange
      batch.busy = true; this.dialog = 'results'
      try {
        for (const original of batch.targets) {
          if (!current()) break
          const op = this.operation(original); const previous = batch.results[original.id]
          if (retry ? previous?.status !== 'failed' : previous?.status !== 'idle') continue
          let snapshot = original
          if (retry) {
            try {
              const result = await getAPI('listVirtualMachines', { id: original.id, details: 'min,affgrp', ...(original.projectid ? { projectid: original.projectid } : {}), listall: true }, { preserveOnFailure: true, timeout: 15000 })
              snapshot = result.listvirtualmachinesresponse?.virtualmachine?.find(vm => vm.id === original.id)
              if (!snapshot || affinityOwnerKey(snapshot) !== affinityOwnerKey(original)) throw new Error('message.affinity.owner.changed')
            } catch (error) { batch.results[original.id] = { status: 'failed', error: affinityApiError(error) || error.message }; continue }
          }
          await op.run('updateVMAffinityGroup', { id: original.id }, () => prepareAffinityMemberChange(getAPI, group, snapshot, batch.mode, current))
        }
        if (current()) { this.selection = []; this.selected = {}; await Promise.all([this.fetchData(), this.parentFetchData()]) }
      } finally { batch.busy = false }
    },
    async recheck (vm) { await this.operation(vm).recheck(); if (this.alive) { await this.fetchData(); this.parentFetchData() } }
  }
}
</script>
<style scoped>
.affinity-members { min-width: 0; }
.affinity-members-toolbar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 16px; }
.affinity-members-toolbar :deep(.ant-input-search) { width: 280px; max-width: 100%; margin-left: auto; }
.affinity-members-pagination { margin-top: 16px; text-align: right; }
.affinity-member-results { padding-left: 20px; }
.affinity-member-results li { margin-bottom: 12px; }
</style>
