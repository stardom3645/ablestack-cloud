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
  <div>
    <p v-if="listRefreshFailed" role="status">{{ $t('message.list.refresh.stale') }}</p>
    <a-button
      v-if="((deleteApi in $store.getters.apis) && this.selectedRowKeys.length > 0)"
      type="primary"
      danger
      style="width: 100%; margin-bottom: 15px"
      @click="bulkActionConfirmation()">
      <template #icon><delete-outlined /></template>
      {{ $t('label.action.bulk.delete.snapshots') }}
    </a-button>
    <a-table
      size="small"
      style="overflow-y: auto"
      :loading="loading || fetchLoading"
      :columns="columns"
      :dataSource="dataSource"
      :pagination="false"
      :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
      :rowKey="record => record.zoneid">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'zonename'">
          <span v-if="record.datastoreid">
            <router-link :to="{ path: (record.datastoretype === 'Primary' ? '/storagepool/' : '/imagestore/') + encodeURIComponent(record.datastoreid) }">
              <span v-if="fetchZoneIcon(record.zoneid)">
                <resource-icon :image="zoneIcon" size="1x" style="margin-right: 5px"/>
              </span>
              <global-outlined v-else style="margin-right: 5px" />
              <span> {{ record.zonename }} </span>
            </router-link>
          </span>
          <span v-else>
            <span v-if="fetchZoneIcon(record.zoneid)">
              <resource-icon :image="zoneIcon" size="1x" style="margin-right: 5px"/>
            </span>
            <global-outlined v-else style="margin-right: 5px" />
            <span> {{ record.zonename }} </span>
          </span>
        </template>
        <template v-if="column.key === 'isready'">
          <span v-if="record.datastorestate==='Ready'">{{ $t('label.yes') }}</span>
          <span v-else>{{ $t('label.no') }}</span>
        </template>
        <template v-if="column.key === 'actions'">
          <tooltip-button
            v-if="record.state==='BackedUp'"
            style="margin-right: 5px"
            :disabled="!(copyApi in $store.getters.apis)"
            :title="$t('label.action.copy.snapshot')"
            icon="copy-outlined"
            :loading="copyLoading"
            @onClick="showCopySnapshot(record)" />
          <tooltip-button
            v-if="record.state==='BackedUp'"
            style="margin-right: 5px"
            :disabled="!(deleteApi in $store.getters.apis)"
            :title="$t('label.action.delete.snapshot')"
            type="primary"
            :danger="true"
            icon="delete-outlined"
            @onClick="onShowDeleteModal(record)"/>
        </template>
      </template>
    </a-table>
    <a-pagination
      class="row-element"
      size="small"
      :current="page"
      :pageSize="pageSize"
      :total="itemCount"
      :showTotal="total => `${$t('label.total')} ${total} ${$t('label.items')}`"
      :pageSizeOptions="['10', '20', '40', '80', '100']"
      @change="handleChangePage"
      @showSizeChange="handleChangePageSize"
      showSizeChanger>
      <template #buildOptionText="props">
        <span>{{ props.value }} / {{ $t('label.page') }}</span>
      </template>
    </a-pagination>

    <a-modal
      v-if="copyApi in $store.getters.apis"
      style="top: 20px;"
      :title="$t('label.action.copy.snapshot')"
      :visible="showCopyActionForm"
      :closable="true"
      :maskClosable="false"
      :footer="null"
      :confirmLoading="copyLoading"
      @cancel="onCloseModal"
      centered>
      <a-spin :spinning="copyLoading" v-ctrl-enter="handleCopySnapshotSubmit">
        <StorageSnapshotSafety :context="copySafetyContext" :loading="copySafetyLoading" :error="copySafetyError" @refresh="refreshCopySafety" />
        <a-form
          :ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
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
          <div :span="24" class="action-button">
            <a-button @click="onCloseModal">{{ $t('label.cancel') }}</a-button>
            <a-button
              type="primary"
              ref="submit"
              :disabled="isCopySnapshotSubmitDisabled"
              @click="handleCopySnapshotSubmit">
                {{ $t('label.ok') }}
              </a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <StorageSnapshotActionDialog v-if="safetyDeleteAction" :key="deleteGeneration" :resource="resource" :current-action="safetyDeleteAction" @refresh-data="fetchData" @close-action="safetyDeleteAction = null; resetSelection(); fetchData()" />
  </div>
</template>

<script>
import StorageSnapshotActionDialog from '@/views/storage/StorageSnapshotActionDialog.vue'
import StorageSnapshotSafety from '@/components/view/StorageSnapshotSafety.vue'
import { freezeSnapshotTarget, freshStorageSnapshotContext, snapshotSecurityScope, snapshotActionParams, snapshotResourceKeys, copyTarget } from '@/utils/storageSnapshotContext'
import { submitSnapshotOperation, snapshotOperationBusy } from '@/utils/storageSnapshotOperations'
import { listRefreshMixin } from '@/utils/listRefreshMixin'

import { ref, reactive, toRaw } from 'vue'
import { getAPI } from '@/api'
import { isAdmin } from '@/role'
import OsLogo from '@/components/widgets/OsLogo'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipButton from '@/components/widgets/TooltipButton'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import Status from '@/components/widgets/Status'

export default {
  mixins: [listRefreshMixin(['fetchData'])],
  name: 'SnapshotZones',
  components: {
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
      columns: [],
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
    this.columns = [
      {
        key: 'zonename',
        title: this.$t('label.zonename'),
        dataIndex: 'zonename'
      },
      {
        title: this.$t('label.status'),
        dataIndex: 'status'
      },
      {
        key: 'isready',
        title: this.$t('label.isready'),
        dataIndex: 'isready'
      }
    ]
    if (this.isActionPermitted()) {
      this.columns.push({
        key: 'actions',
        title: '',
        dataIndex: 'actions',
        width: 100
      })
    }

    this.initForm()
    this.fetchData()
  },
  watch: {
    loading (newData, oldData) {
      if (!newData && !this.showGroupActionModal) {
        this.fetchData()
      }
    }
  },
  computed: {
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
    fetchData () {
      const listRequest = this.listRequestToken('fetchData')
      const params = {}
      params.id = this.resource.id
      params.showunique = false
      params.listall = true
      params.page = this.page
      params.pagesize = this.pageSize

      this.fetchLoading = !listRequest.loaded
      if (!listRequest.loaded) {
        this.fetchZoneData()
      }
      return getAPI('listSnapshots', params).then(json => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        this.dataSource = []
        this.itemCount = 0
        this.dataSource = json.listsnapshotsresponse.snapshot || []
        this.itemCount = json.listsnapshotsresponse.count || 0
        if (this.itemCount > 0) {
          this.dataSource = this.dataSource.filter((obj, index) => this.dataSource.findIndex((item) => item.zoneid === obj.zoneid) === index)
        }
      }).catch(error => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true
        this.listRefreshFailed = true
        if (listRequest.loaded) return

        this.$notifyError(error)
      }).finally(() => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return

        this.fetchLoading = false
      })
    },
    fetchZoneIcon (zoneid) {
      const zoneItem = this.zones.filter(zone => zone.id === zoneid)
      if (zoneItem?.[0]?.icon?.base64image) {
        this.zoneIcon = zoneItem[0].icon.base64image
        return true
      }
      return false
    },
    handleChangePage (page, pageSize) {
      this.page = page
      this.pageSize = pageSize
      this.fetchData()
    },
    handleChangePageSize (currentPage, pageSize) {
      this.page = currentPage
      this.pageSize = pageSize
      this.fetchData()
    },
    isActionPermitted () {
      return this.resource.state && this.resource.state === 'BackedUp'
    },
    setSelection (selection) {
      this.selectedRowKeys = selection
      if (selection?.length > 0) {
        this.modalWidth = '50vw'
        this.$emit('selection-change', this.selectedRowKeys)
        this.selectedItems = (this.dataSource.filter(function (item) {
          return selection.indexOf(item.zoneid) !== -1
        }))
      } else {
        this.modalWidth = '30vw'
        this.selectedItems = []
      }
    },
    resetSelection () {
      this.setSelection([])
    },
    onSelectChange (selectedRowKeys, selectedRows) {
      this.setSelection(selectedRowKeys)
    },
    bulkActionConfirmation () {
      this.showConfirmationAction = true
      this.selectedColumns = this.columns.filter(column => {
        return !this.filterColumns.includes(column.title)
      })
      this.selectedItems = this.selectedItems.map(v => ({ ...v, status: 'InProgress' }))
      this.onShowDeleteModal(this.selectedItems[0])
    },
    fetchZoneData () {
      const origin = JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]); const sequence = this.copySequence
      this.zones = []
      this.zoneLoading = true
      getAPI('listZones', { showicon: true }).then(json => {
        if (origin !== JSON.stringify([this.resource.id, snapshotSecurityScope(this.$store)]) || sequence !== this.copySequence) return
        const zones = json.listzonesresponse.zone || []
        this.zones = [...zones.filter((zone) => this.currentRecord.zoneid !== zone.id)]
      }).finally(() => {
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
    onShowDeleteModal (record) {
      const targets = this.showConfirmationAction ? this.selectedItems : [record]
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
.row-element {
  margin-top: 15px;
  margin-bottom: 15px;
}
</style>
