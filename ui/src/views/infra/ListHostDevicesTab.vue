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
    <a-table
      :columns="columns"
      :dataSource="tableSource"
      :pagination="false"
      size="middle"
      :scroll="{ y: 1000 }">
      <template #headerCell="{ column }">
        <template v-if="column.key === 'pcitext'">
          {{ $t('label.details') }}
        </template>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'pciname'">{{ record.pciname }}</template>
        <template v-if="column.key === 'pcitext'">{{ record.pcitext }}</template>
        <template v-if="column.key === 'action'">
          <a-button
            type="primary"
            size="medium"
            shape="circle"
            :tooltip="$t('label.create')"
            @click="openModal(record)"
            :loading="loading">
            <template #icon><plus-outlined /></template>
          </a-button>
        </template>
      </template>
    </a-table>

    <a-modal
      :visible="showAddModal"
      :title="$t('label.create.host.devices')"
      :v-html="$t('message.restart.vm.host.update.settings')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="closeModals">
      <HostDevicesTransfer :resource="selectedResource" @close-action="closeModals" />
    </a-modal>
  </div>
</template>

<script>
import { api } from '@/api'
import { IdcardOutlined } from '@ant-design/icons-vue'
import HostDevicesTransfer from '@/views/storage/HostDevicesTransfer'

export default {
  name: 'ListHostDevices',
  components: {
    IdcardOutlined,
    HostDevicesTransfer
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      columns: [
        {
          key: 'pciname',
          dataIndex: 'pciname',
          title: this.$t('label.name'),
          width: '30%'
        },
        {
          key: 'pcitext',
          dataIndex: 'pcitext',
          title: this.$t('label.text'),
          width: '50%'
        },
        {
          key: 'action',
          dataIndex: 'action',
          title: this.$t('label.action'),
          width: '20%'
        }
      ],
      dataItems: [],
      loading: false,
      showAddModal: false,
      selectedResource: null
    }
  },
  computed: {
    tableSource () {
      return this.dataItems.map((item, index) => {
        return {
          key: index,
          pciname: item.pciname,
          pcitext: item.pcitext,
          action: 'action'
        }
      })
    }
  },
  created () {
    this.fetchData()
  },
  methods: {
    openModal (record) {
      this.selectedResource = { ...this.resource, pciname: record.pciname }
      this.showAddModal = true
    },
    closeModals () {
      this.showAddModal = false
      this.selectedResource = null
      this.fetchData()
    },
    fetchData () {
      this.loading = true
      api('listHostDevices', {
        id: this.resource.id
      }).then(json => {
        const response = json.listhostdevicesresponse
        if (response && response.listhostdevices && response.listhostdevices.length > 0) {
          const data = response.listhostdevices[0]
          const pcinames = data.pciname || []
          const pcitexts = data.pcitext || []
          this.dataItems = pcinames.map((pciname, index) => ({
            pciname: pciname,
            pcitext: pcitexts[index] || ''
          }))
        } else {
          this.dataItems = []
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style lang="less" scoped>
  .ant-table-wrapper {
    margin: 2rem 0;
  }

  @media (max-width: 600px) {
    position: relative;
    width: 100%;
    top: 0;
    right: 0;
  }

  :deep(.ant-table-tbody) > tr > td {
    cursor: pointer;
  }
</style>
