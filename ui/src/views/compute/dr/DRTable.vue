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
  <a-spin :spinning="loading">
    <a-table
      size="small"
      :columns="drColumns"
      :dataSource="this.drCluster"
      :rowKey="item => item.id"
      :pagination="false"
    >
  <!--    펼쳐졌을 때 정보-->
      <template #expandedRowRender="{ record, text }">
        <a-descriptions style="margin-top: 10px" layout="vertical" :column="1" :bordered="false" size="small">
          <a-descriptions-item :label="$t('label.id')" class="bold-label">
            {{ record.mirroredVmId }}
          </a-descriptions-item>
          <a-descriptions-item :label="$t('label.dr.volume.root.disk.status')">
            <status :text="text ? text : ''" displayText />
            {{ record.mirroredVmVolStatus }}
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <template #bodyCell="{ column, record, text }">
        <template v-if="column.key === 'state'">
          <status :text="text ? text : ''" displayText />
        </template>
         <template v-if="column.key === 'name'">
          <router-link :to="{ path: '/disasterrecoverycluster/' + record.id }" >{{ text }}</router-link>
        </template>
        <template v-if="column.key === 'actions'">
        <slot name="actions"/>
        </template>
      </template>
    </a-table>
  </a-spin>
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
    }
  },
  components: {
    Status,
    ResourceIcon
  },
  inject: ['parentFetchData'],
  data () {
    return {
      loading: false,
      drColumns: [
        {
          key: 'mirroredVmname',
          title: this.$t('label.dr.mirrored.vm.name'),
          dataIndex: 'mirroredVm'
        },
        {
          key: 'state',
          title: this.$t('label.dr.mirrored.vm.status'),
          dataIndex: 'mirroredStatus'
        },
        {
          key: 'name',
          title: this.$t('label.dr.mirrored.cluster.name'),
          dataIndex: 'drName'
        },
        {
          key: 'actions',
          title: '',
          width: 100
        }
      ],
      drClusterList: [],
      drCluster: [],
      drVm: [],
      drVmName: '',
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
    this.drVmName = this.dataResource.name
    this.getDrClusterList()
  },
  methods: {
    getDrClusterList () {
      this.loading = true
      api('getDisasterRecoveryClusterList').then(json => {
        this.drClusterList = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster || []
        for (const cluster of this.drClusterList) {
          const vmList = cluster.drclustervmmap
          if (vmList.some(vm => vm.drclustermirrorvmname === this.drVmName)) {
            this.clusterName = cluster.drclustermirrorvmname
            break
          }
        }
        this.getDrClusterVm()
      }).finally(() => {
        this.loading = false
      })
    },
    getDrClusterVm () {
      this.loading = true
      api('getDisasterRecoveryClusterList', { name: this.clusterName }).then(json => {
        this.drCluster = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster[0].drclustervmmap || []
        this.drVm = this.clusterName
        this.drCluster = this.drCluster.map(item => ({ ...item, drName: item.drclustername }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVm: item.drclustermirrorvmname }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredStatus: item.drclustermirrorvmstatus }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVmId: item.drclustermirrorvmid }))
        this.drCluster = this.drCluster.map(item => ({ ...item, mirroredVmVolStatus: item.drclustermirrorvmvolstatus }))
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>
