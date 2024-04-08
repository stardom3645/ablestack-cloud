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
  <a-alert type="info" :message="$t('message.info.for.dr')" showIcon />
  <br>
  <a-table
    size="small"
    :columns="drColumns"
    :dataSource="this.drCluster"
    :rowKey="item => item.id"
    :pagination="false"
  >
    <template #expandedRowRender="{ record, text }">
      <slot name="actions" :nic="record" />
      <a-descriptions style="margin-top: 10px" layout="vertical" :column="1" :bordered="false" size="small">
        <a-descriptions-item :label="$t('label.id')">
          {{ record.id }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.volume.root.disk.status')">
          <status :text="text ? text : ''" displayText />
          {{ record.mirroredVmRootDisk }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.volume.data.disk.status')">
          <status :text="text ? text : ''" displayText />
          {{ record.mirroredVmDataDisk }}<br>
        </a-descriptions-item>
      </a-descriptions>
    </template>
    <template #bodyCell="{ column, text }">
      <template v-if="column.key === 'state'">
        <status :text="text ? text : ''" displayText />
      </template>
    </template>
  </a-table>
</template>

<script>
import { api } from '@/api'
import ResourceIcon from '@/components/view/ResourceIcon.vue'
import Status from '@/components/widgets/Status.vue'

export default {
  name: 'DrTable',
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
  components: {
    Status,
    ResourceIcon
  },
  inject: ['parentFetchData'],
  data () {
    return {
      drColumns: [
        {
          key: 'name',
          title: this.$t('label.dr.mirrored.vm.name'),
          dataIndex: 'mirroredVm'
        },
        {
          key: 'state',
          title: this.$t('label.dr.mirrored.vm.status'),
          dataIndex: 'drclusterstatus'
        },
        {
          key: 'name',
          title: this.$t('label.dr.mirrored.cluster.name'),
          dataIndex: 'name'
        }
      ],
      drCluster: [],
      drVm: [],
      combinedArray: []
    }
  },
  watch: {
    resource: {
      deep: true,
      handler (newData, oldData) {
        if (newData !== oldData) {
          this.dataResource = newData
          this.vm = this.resource
          this.fetchData()
        }
      }
    }
  },
  items: {
    deep: true,
    handler (newItem) {
      if (newItem) {
        this.dataSource = newItem
      }
    }
  },
  '$i18n.global.locale' (to, from) {
    if (to !== from) {
      this.fetchData()
    }
  },
  created () {
    this.dataResource = this.resource
    this.vm = this.dataResource
    this.fetchData()
  },
  methods: {
    fetchData () {
      api('getDisasterRecoveryClusterList', { name: 'test-sec-cluster-01' }).then(json => {
        this.drCluster = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster || []
        this.drVm = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster[0].disasterrecoveryclustervmlist[3].name || []
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVm: 'mirrored-vm-001' }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVmRootDisk: 'ROOT-673' }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVmDataDisk: 'DATA-678' }))
      }).finally(() => {
      })
    }
  }
}
</script>
