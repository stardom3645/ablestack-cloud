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
  <div class="snapshot-zones" data-testid="snapshot-zones">
    <p class="snapshot-copy-caption">{{ $t('message.snapshot.copy.units') }}</p>
    <a-alert v-if="listRefreshFailed" type="warning" show-icon :message="$t(copyReadDenied ? 'message.snapshot.copy.denied' : 'message.list.refresh.stale')">
      <template #description><a-button type="link" size="small" @click="fetchData">{{ $t('label.retry') }}</a-button></template>
    </a-alert>
    <div class="snapshot-copy-tools">
      <span>{{ $t('message.snapshot.copy.page', [zoneGroups.length, dataSource.length]) }}</span>
      <a-button v-if="deleteApi in $store.getters.apis && selectedRowKeys.length" danger :disabled="listRefreshFailed || fetchLoading" @click="bulkActionConfirmation">
        <template #icon><delete-outlined /></template>{{ $t('label.snapshot.copy.delete.zones') }}
      </a-button>
    </div>
    <a-table
      class="snapshot-zone-table"
      size="small"
      :loading="loading || fetchLoading"
      :columns="columns"
      :data-source="zoneGroups"
      :pagination="false"
      :row-selection="deleteApi in $store.getters.apis ? { selectedRowKeys, onChange: onSelectChange, getCheckboxProps: record => ({ disabled: !record.zoneid || listRefreshFailed || fetchLoading }) } : undefined"
      :row-key="record => record.key"
      :expanded-row-keys="expandedZoneKeys"
      @expand="onExpandZone">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'zonename'">
          <router-link v-if="record.zoneid && ('listZones' in $store.getters.apis)" :to="'/zone/' + encodeURIComponent(record.zoneid)">
            <resource-icon v-if="fetchZoneIcon(record.zoneid)" :image="fetchZoneIcon(record.zoneid)" size="1x" />
            <global-outlined v-else /> {{ record.zonename || record.zoneid }}
          </router-link>
          <span v-else>{{ record.zonename || $t('label.snapshot.copy.unknown') }}</span>
        </template>
        <template v-else-if="column.key === 'copies'">{{ record.copies.length }}</template>
        <template v-else-if="column.key === 'actions'">
          <tooltip-button v-if="record.zoneid && resource.state === 'BackedUp' && (copyApi in $store.getters.apis)" :disabled="listRefreshFailed || fetchLoading" :title="$t('label.snapshot.copy.from.zone')" icon="copy-outlined" @onClick="showCopySnapshot(record)" />
          <tooltip-button v-if="record.zoneid && resource.state === 'BackedUp' && (deleteApi in $store.getters.apis)" :disabled="listRefreshFailed || fetchLoading" :title="$t('label.snapshot.copy.delete.zone')" icon="delete-outlined" danger @onClick="onShowDeleteModal(record)" />
        </template>
      </template>
      <template #expandedRowRender="{ record: zone }">
        <a-table class="snapshot-copy-table" size="small" :columns="copyColumns" :data-source="zone.copies" :pagination="false" :row-key="record => record.copy.id" :scroll="{ x: 760 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'role'">{{ copyRole(record) }}</template>
            <template v-else-if="column.key === 'datastore'">
              <router-link v-if="storePath(record)" :to="storePath(record)">{{ record.datastorename || record.datastoreid }}</router-link>
              <span v-else>{{ record.datastorename || $t(record.copy.datastoreknown ? 'label.snapshot.copy.restricted.store' : 'label.snapshot.copy.unknown.store') }}</span>
            </template>
            <template v-else-if="column.key === 'state'"><Status :text="record.copy.state || 'Unknown'" :display-text="true" /></template>
            <template v-else-if="column.key === 'availability'">{{ $t('label.snapshot.copy.' + record.copy.availability.toLowerCase()) }}</template>
            <template v-else-if="column.key === 'size'">{{ copySize(record) }}</template>
            <template v-else-if="column.key === 'progress'">
              <span v-if="Number.isInteger(record.copy.downloadpercent)">{{ record.copy.downloadpercent }}%</span>
              <span v-else-if="record.copy.downloadstate === 'DOWNLOADED'">{{ $t('label.snapshot.copy.completed') }}</span>
              <span v-else>{{ record.copy.downloadstate || '—' }}</span>
              <p v-if="record.copy.error" class="snapshot-copy-error" role="status">{{ record.copy.error }}</p>
            </template>
          </template>
        </a-table>
      </template>
    </a-table>
    <a-pagination
      class="row-element"
      size="small"
      :current="page"
      :page-size="pageSize"
      :total="itemCount"
      :show-total="total => $t('message.snapshot.copy.total', [total])"
      :page-size-options="['10', '20', '40', '80', '100']"
      @change="handleChangePage"
      @showSizeChange="handleChangePageSize"
      showSizeChanger />

    <MoldDialog
      v-if="copyApi in $store.getters.apis"
      size="md"
      form-actions
      :title="$t('label.action.copy.snapshot')"
      :visible="showCopyActionForm"
      :closable="true"
      :maskClosable="false"
      :confirmLoading="copyLoading"
      @cancel="onCloseModal"
      centered>
      <a-spin :spinning="copyLoading" v-ctrl-enter="handleCopySnapshotSubmit">
        <StorageSnapshotSafety compact :context="copySafetyContext" :loading="copySafetyLoading" :error="copySafetyError" @refresh="refreshCopySafety" />
        <a-form
          :ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          class="snapshot-copy-form"
          @finish="handleCopySnapshotSubmit">
          <a-form-item ref="zoneid" name="zoneid" :label="$t('label.zoneid')">
            <a-select
              id="zone-selection"
              mode="multiple"
              :placeholder="$t('label.select.zones')"
              v-model:value="form.zoneid"
              showSearch
              optionFilterProp="label"
              :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }"
              :loading="zoneLoading"
              v-focus="true">
              <a-select-option v-for="zone in zones" :key="zone.id" :label="zone.name">
                <div>
                  <span v-if="zone.icon && zone.icon.base64image">
                    <resource-icon :image="zone.icon.base64image" size="1x" style="margin-right: 5px"/>
                  </span>
                  <global-outlined v-else style="margin-right: 5px" />
                  {{ zone.name }}
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item name="useStorageReplication" ref="useStorageReplication">
            <template #label>
              <tooltip-label :title="$t('label.usestoragereplication')" :tooltip="apiParams.usestoragereplication.description" />
            </template>
            <a-switch v-model:checked="form.useStorageReplication" />
          </a-form-item>
          <a-form-item v-if="isAdmin && form.useStorageReplication" ref="storageid" name="storageid" :label="$t('label.storagepools')">
            <a-select
              id="storage-selection"
              mode="multiple"
              :placeholder="$t('label.select.storagepools')"
              v-model:value="form.storageid"
              showSearch
              optionFilterProp="label"
              :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }"
              :loading="storagePoolLoading"
              v-focus="true">
              <a-select-option v-for="opt in storagePools" :key="opt.id" :label="opt.name || opt.description">
                <div>
                  <span v-if="opt.icon && opt.icon.base64image">
                    <resource-icon :image="opt.icon.base64image" size="1x" style="margin-right: 5px"/>
                  </span>
                  <global-outlined v-else style="margin-right: 5px" />
                  {{ opt.name || opt.description }}
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>
          <MoldDialogActions :busy="copyLoading">
            <a-button :disabled="copyLoading" @click="onCloseModal">{{ $t('label.cancel') }}</a-button>
            <a-button
              type="primary"
              ref="submit"
              :disabled="isCopySnapshotSubmitDisabled"
              @click="handleCopySnapshotSubmit">
                {{ $t('label.ok') }}
              </a-button>
          </MoldDialogActions>
        </a-form>
      </a-spin>
    </MoldDialog>

    <StorageSnapshotActionDialog v-if="safetyDeleteAction" :key="deleteGeneration" :resource="resource" :current-action="safetyDeleteAction" @refresh-data="fetchData" @close-action="safetyDeleteAction = null; resetSelection(); fetchData()" />
  </div>
</template>

<script>
import StorageSnapshotActionDialog from '@/views/storage/StorageSnapshotActionDialog.vue'
import StorageSnapshotSafety from '@/components/view/StorageSnapshotSafety.vue'
import { freezeSnapshotTarget, freshStorageSnapshotContext, snapshotSecurityScope, snapshotActionParams, snapshotResourceKeys, copyTarget } from '@/utils/storageSnapshotContext'
import { submitSnapshotOperation, snapshotOperationBusy } from '@/utils/storageSnapshotOperations'
import { listRefreshMixin, detailTabRouteKey } from '@/utils/listRefreshMixin'
import MoldDialog from '@/components/view/MoldDialog.vue'
import MoldDialogActions from '@/components/view/MoldDialogActions.vue'
import { readSnapshotCopyPage, snapshotCopyZones, snapshotCopySize } from '@/utils/snapshotCopies'

import { ref, reactive, toRaw } from 'vue'
import { getAPI } from '@/api'
import { isAdmin } from '@/role'
import OsLogo from '@/components/widgets/OsLogo'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipButton from '@/components/widgets/TooltipButton'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import Status from '@/components/widgets/Status'

export default {
  mixins: [listRefreshMixin(['fetchData'], { routeKey: vm => JSON.stringify([detailTabRouteKey(vm.$route), snapshotSecurityScope(vm.$store), 'listSnapshots' in vm.$store.getters.apis]), active: vm => !vm.showCopyActionForm && !vm.safetyDeleteAction })],
  name: 'SnapshotZones',
  components: {
    MoldDialog,
    MoldDialogActions,
    TooltipButton,
    TooltipLabel,
    OsLogo,
    ResourceIcon,
    StorageSnapshotActionDialog,
    StorageSnapshotSafety,
    Status
  },
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      safetyDeleteAction: null,
      deleteGeneration: 0,
      copySequence: 0,
      copySafetySequence: 0,
      copyScope: '',
      copyTarget: null,
      copySafetyContext: null,
      copySafetyError: '',
      copySafetyFingerprint: '',
      copySafetyLoading: false,
      expandedZoneKeys: [],
      loadedCopyScope: '',
      copyReadDenied: false,
      zoneIcons: {},
      dataSource: [],
      page: 1,
      pageSize: 10,
      itemCount: 0,
      fetchLoading: false,
      showCopyActionForm: false,
      currentRecord: {},
      zones: [],
      zoneLoading: false,
      storagePools: [],
      storagePoolLoading: false,
      copyLoading: false,
      deleteLoading: false,
      showDeleteSnapshot: false,
      forcedDelete: false,
      selectedRowKeys: [],
      showGroupActionModal: false,
      selectedItems: [],
      selectedColumns: [],
      filterColumns: ['Status', 'Ready'],
      showConfirmationAction: false,
      message: {
        title: this.$t('label.action.bulk.delete.snapshots'),
        confirmMessage: this.$t('label.confirm.delete.snapshot.zones')
      },
      modalWidth: '30vw',
      showTable: false
    }
  },
  beforeCreate () {
    this.deleteApi = 'deleteSnapshot'
    this.copyApi = 'copySnapshot'
    this.apiParams = this.$getApiParams(this.copyApi)
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  watch: {
    'resource.id' () { this.resetCopyRead() },
    copySecurityScope () { this.resetCopyRead() },
    canReadCopies () { this.resetCopyRead() },
    loading (newData, oldData) {
      if (!newData && !this.showGroupActionModal) {
        this.fetchData()
      }
    }
  },
  computed: {
    copySecurityScope () { return snapshotSecurityScope(this.$store) },
    canReadCopies () { return 'listSnapshots' in this.$store.getters.apis },
    zoneGroups () { return snapshotCopyZones(this.dataSource) },
    columns () {
      const columns = [
        { key: 'zonename', title: this.$t('label.zonename') },
        { key: 'copies', title: this.$t('label.snapshot.copy.on.page') }
      ]
      if ((this.copyApi in this.$store.getters.apis) || (this.deleteApi in this.$store.getters.apis)) columns.push({ key: 'actions', title: this.$t('label.snapshot.copy.zone.actions'), width: 110 })
      return columns
    },
    copyColumns () {
      return ['role', 'datastore', 'state', 'availability', 'size', 'progress'].map(key => ({ key, title: this.$t('label.snapshot.copy.column.' + key) }))
    },
    isCopySnapshotSubmitDisabled () {
      return this.copyLoading || this.copySafetyLoading || !this.copySafetyContext || !!this.copySafetyError || !!this.copySafetyContext.reason || !this.copyCurrent() || snapshotOperationBusy(snapshotSecurityScope(this.$store), snapshotResourceKeys(this.copyTarget || {}, this.copySafetyContext)) || ((this.form.storageid || []).length === 0 && (this.form.zoneid || []).length === 0)
    },
    isAdmin () {
      return isAdmin()
    }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        useStorageReplication: false
      })
      this.rules = reactive({
        zoneid: [{ type: 'array', required: false }]
      })
    },
    async fetchData () {
      const listRequest = this.listRequestToken('fetchData')
      const scope = JSON.stringify([this.resource.id, this.copySecurityScope, this.page, this.pageSize])
      if (scope !== this.loadedCopyScope) {
        this.dataSource = []; this.itemCount = 0; this.resetSelection(); this.expandedZoneKeys = []
      }
      if (!this.canReadCopies) { this.fetchLoading = false; return }
      const params = { id: this.resource.id, showunique: false, listall: true, page: this.page, pagesize: this.pageSize }
      if (this.$store.getters.project?.id) params.projectid = this.$store.getters.project.id
      this.fetchLoading = !listRequest.loaded
      if (!listRequest.loaded) this.fetchZoneData()
      try {
        const json = await getAPI('listSnapshots', params, { preserveOnFailure: true, timeout: 20000 })
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        const result = readSnapshotCopyPage(json, this.resource.id, this.pageSize, this.page)
        const last = Math.max(1, Math.ceil(result.count / this.pageSize))
        if (this.page > last) {
          this.page = last; this.resetSelection()
          return this.fetchData()
        }
        const expanded = new Set(this.expandedZoneKeys)
        const known = new Set(this.zoneGroups.map(row => row.key))
        this.dataSource = result.rows; this.itemCount = result.count; this.loadedCopyScope = scope
        this.expandedZoneKeys = this.zoneGroups.filter(row => expanded.has(row.key) || !known.has(row.key)).map(row => row.key)
        this.setSelection(this.selectedRowKeys.filter(key => this.zoneGroups.some(row => row.key === key)))
        this.listRefreshFailed = false; this.copyReadDenied = false
      } catch (error) {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true; this.listRefreshFailed = true
        this.copyReadDenied = [401, 403].includes(error.response?.status) || Object.values(error.response?.data || {}).some(value => Number(value?.errorcode) === 531)
        if (this.copyReadDenied) {
          this.dataSource = []; this.itemCount = 0; this.loadedCopyScope = ''; this.expandedZoneKeys = []; this.resetSelection()
        }
        if (!listRequest.loaded) this.$notifyError(error)
      } finally {
        if (this.isListRequestCurrent('fetchData', listRequest)) this.fetchLoading = false
      }
    },
    resetCopyRead () {
      this.onCloseModal(); this.safetyDeleteAction = null; this.fetchLoading = false; this.copyReadDenied = false
      this.dataSource = []; this.itemCount = 0; this.loadedCopyScope = ''; this.expandedZoneKeys = []; this.page = 1
      this.resetSelection(); this.fetchData()
    },
    onExpandZone (expanded, row) {
      this.expandedZoneKeys = expanded ? [...new Set([...this.expandedZoneKeys, row.key])] : this.expandedZoneKeys.filter(key => key !== row.key)
    },
    fetchZoneIcon (zoneid) { return this.zoneIcons[zoneid] || '' },
    copyRole (row) { return this.$t('label.snapshot.copy.' + (row.copy.role === 'Primary' ? 'primary' : row.copy.role === 'Image' ? 'secondary' : 'unknown')) },
    copySize (row) { return snapshotCopySize(row.copy.virtualsize) || this.$t('label.snapshot.copy.unknown') },
    storePath (row) {
      if (!row.datastoreid) return ''
      if (row.copy.role === 'Primary' && ('listStoragePools' in this.$store.getters.apis)) return '/storagepool/' + encodeURIComponent(row.datastoreid)
      if (row.copy.role === 'Image' && ('listImageStores' in this.$store.getters.apis)) return '/imagestore/' + encodeURIComponent(row.datastoreid)
      return ''
    },
    handleChangePage (page, pageSize) {
      this.resetSelection()
      this.page = page
      this.pageSize = pageSize
      this.fetchData()
    },
    handleChangePageSize (currentPage, pageSize) {
      this.resetSelection()
      this.page = 1
      this.pageSize = pageSize
      this.fetchData()
    },
    isActionPermitted () {
      return this.resource.state && this.resource.state === 'BackedUp'
    },
    setSelection (selection) {
      this.selectedItems = this.zoneGroups.filter(row => row.zoneid && selection.includes(row.key))
      this.selectedRowKeys = this.selectedItems.map(row => row.key)
      this.$emit('selection-change', this.selectedItems.map(row => row.zoneid))
    },
    resetSelection () {
      this.setSelection([])
    },
    onSelectChange (selectedRowKeys, selectedRows) {
      this.setSelection(selectedRowKeys)
    },
    bulkActionConfirmation () {
      if (!this.selectedItems.length || this.listRefreshFailed) return
      this.showConfirmationAction = true
      this.selectedColumns = this.columns.filter(column => {
        return !this.filterColumns.includes(column.title)
      })
      this.selectedItems = this.selectedItems.map(v => ({ ...v, status: 'InProgress' }))
      this.onShowDeleteModal(this.selectedItems[0], true)
    },
    fetchZoneData () {
      const origin = JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]); const sequence = this.copySequence
      this.zones = []
      this.zoneLoading = true
      if (!('listZones' in this.$store.getters.apis)) { this.zoneLoading = false; return }
      getAPI('listZones', { showicon: true }, { preserveOnFailure: true }).then(json => {
        if (origin !== JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) || sequence !== this.copySequence) return
        const zones = json.listzonesresponse.zone || []
        this.zoneIcons = Object.fromEntries(zones.filter(zone => zone.icon?.base64image).map(zone => [zone.id, zone.icon.base64image]))
        this.zones = zones.filter(zone => this.currentRecord.zoneid !== zone.id)
      }).catch(error => { if (origin === JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) && sequence === this.copySequence) this.$notifyError(error) }).finally(() => {
        if (origin !== JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) || sequence !== this.copySequence) return
        this.zoneLoading = false
      })
    },
    fetchStoragePoolData () {
      const origin = JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]); const sequence = this.copySequence
      const params = {}
      params.showicon = true
      this.storagePoolsLoading = true
      getAPI('listStoragePools', params).then(json => {
        if (origin !== JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) || sequence !== this.copySequence) return
        const listStoragePools = json.liststoragepoolsresponse.storagepool
        if (listStoragePools) {
          this.storagePools = listStoragePools
          this.storagePools = this.storagePools.filter(pool => [true, 'true'].includes(pool.storagecapabilities?.CAN_COPY_SNAPSHOT_BETWEEN_ZONES_AND_SAME_POOL_TYPE) && pool.zoneid !== this.resource.zoneid)
        }
      }).finally(() => {
        if (origin !== JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) || sequence !== this.copySequence) return
        this.storagePoolsLoading = false
      })
    },
    showCopySnapshot (record) {
      if (!record?.zoneid || this.listRefreshFailed) return
      this.copySequence++
      this.currentRecord = Object.freeze({ ...record })
      this.copyTarget = freezeSnapshotTarget({ ...this.resource, ...record }, { zoneId: record.zoneid || null })
      this.copyScope = JSON.stringify([this.$route.path, this.resource.id, snapshotSecurityScope(this.$store)])
      this.copySafetyError = ''; this.copySafetyContext = null
      this.form.zoneid = []
      this.form.storageid = []
      this.fetchZoneData()
      if (isAdmin()) {
        this.fetchStoragePoolData()
      }
      this.showCopyActionForm = true
      this.refreshCopySafety()
    },
    onShowDeleteModal (record, bulk = false) {
      if (!record?.zoneid || this.listRefreshFailed) return
      const targets = bulk ? this.selectedItems : [record]
      this.deleteGeneration++
      this.safetyDeleteAction = { api: 'deleteSnapshot', label: 'label.action.delete.snapshot', message: 'message.action.delete.snapshot', zoneScope: true, storageSnapshotTargets: targets.map(row => Object.freeze({ ...this.resource, ...row })) }
    },
    onCloseModal () {
      this.copySequence++; this.copySafetySequence++
      this.copySafetyContext = null
      this.copyTarget = null
      this.currentRecord = {}
      this.showCopyActionForm = false
      this.showDeleteSnapshot = false
      this.showConfirmationAction = false
      this.showTable = false
      this.selectedRowKeys = []
    },
    copyCurrent () { return this.showCopyActionForm && this.copyTarget && this.copyScope === JSON.stringify([this.$route.path, this.resource.id, snapshotSecurityScope(this.$store)]) },
    async refreshCopySafety () {
      if (!this.copyCurrent()) return
      const sequence = ++this.copySafetySequence; this.copySafetyLoading = true; this.copySafetyError = ''
      try {
        const context = await freshStorageSnapshotContext(this.copyTarget, this.copyApi, () => this.copyCurrent() && sequence === this.copySafetySequence)
        if (!this.copyCurrent() || sequence !== this.copySafetySequence) return
        this.copySafetyContext = context; this.copySafetyFingerprint = context.fingerprint
      } catch (error) { if (this.copyCurrent()) this.copySafetyError = this.$t(error.message) } finally { if (sequence === this.copySafetySequence) this.copySafetyLoading = false }
    },
    async handleCopySnapshotSubmit (e) {
      if (e?.preventDefault) e.preventDefault()
      if (this.isCopySnapshotSubmitDisabled) return
      this.copyLoading = true
      const store = this.$store; const scope = snapshotSecurityScope(store); const target = this.copyTarget; const fingerprint = this.copySafetyFingerprint
      try {
        await this.formRef.value.validate()
        const values = copyTarget(toRaw(this.form))
        const params = { useStorageReplication: values.useStorageReplication, destzoneids: values.zoneid.join(), storageids: values.storageid.join() }
        const operation = await submitSnapshotOperation({
          api: this.copyApi,
          scope,
          target: { id: target.id, name: target.record.name, zoneid: target.zoneId },
          keys: snapshotResourceKeys(target, this.copySafetyContext),
          current: () => this.copyCurrent() && snapshotSecurityScope(store) === scope,
          securityCurrent: () => snapshotSecurityScope(store) === scope,
          validate: async () => {
            const latest = await freshStorageSnapshotContext(target, this.copyApi, () => this.copyCurrent())
            if (latest.reason || latest.fingerprint !== fingerprint) throw new Error('message.snapshot.action.changed')
            await Promise.all(values.zoneid.map(async id => {
              const zones = (await getAPI('listZones', { id }, { preserveOnFailure: true })).listzonesresponse.zone || []
              if (zones.length !== 1 || zones[0].id !== id || id === target.zoneId || zones[0].allocationstate !== 'Enabled') throw new Error('message.snapshot.action.destination.changed')
            }))
            await Promise.all(values.storageid.map(async id => {
              const pools = (await getAPI('listStoragePools', { id }, { preserveOnFailure: true })).liststoragepoolsresponse.storagepool || []
              const pool = pools[0]; const capability = pool?.storagecapabilities?.CAN_COPY_SNAPSHOT_BETWEEN_ZONES_AND_SAME_POOL_TYPE
              if (pools.length !== 1 || pool.id !== id || pool.state !== 'Up' || pool.zoneid === target.zoneId || ![true, 'true'].includes(capability)) throw new Error('message.snapshot.action.destination.changed')
            }))
            return snapshotActionParams(target, this.copyApi, params, latest)
          }
        })
        if (operation.jobid && this.copyCurrent()) this.onCloseModal()
        else if (this.copyCurrent()) this.copySafetyError = this.$t(operation.error)
      } catch (error) {
        if (this.copyCurrent()) {
          this.copySafetyError = this.$t(error.message || 'message.snapshot.action.validation.failed')
          if (error.errorFields?.length) this.formRef.value.scrollToField(error.errorFields[0].name)
        }
      } finally { this.copyLoading = false }
    }
  }
}
</script>

<style lang="less" scoped>
.snapshot-copy-caption { color: var(--ui-text-secondary); margin-bottom: 16px; }
.snapshot-copy-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin: 12px 0; }
.snapshot-copy-tools > span { flex: 1; }
.snapshot-copy-error { color: var(--ui-warning-text); margin: 6px 0 0; overflow-wrap: anywhere; }
.snapshot-copy-form :deep(.ant-form-item) { margin-bottom: 24px; }
.snapshot-copy-table { overflow-wrap: anywhere; }
.row-element {
  margin-top: 15px;
  margin-bottom: 15px;
}
</style>
