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
  <a-spin :spinning="loading">
    <a-tabs
      :activeKey="currentTab"
      :tabPosition="device === 'mobile' ? 'top' : 'left'"
      :animated="false"
      @change="handleChangeTab">
      <a-tab-pane :tab="$t('label.details')" key="details">
        <DetailsTab :resource="resource" :loading="loading" />
      </a-tab-pane>
      <a-tab-pane :tab="'Networks'" key="desktopnics">
        <DesktopNicsTable :resource="vm" :loading="loading" />
      </a-tab-pane>
      <a-tab-pane :tab="'IP Range'" key="iprange">
        <a-button
          type="dashed"
          style="width: 100%; margin-bottom: 10px"
          @click="showAddModal"
          :loading="loadingNic"
          :disabled="!('addNicToVirtualMachine' in $store.getters.apis)">
          <a-icon type="plus"></a-icon> {{ 'Add IP Range' }}
        </a-button>
        <a-table
          class="table"
          size="small"
          :columns="iprangeColumns"
          :dataSource="iprange"
          :rowKey="item => item.id"
          :pagination="false"
        >
        </a-table>
      </a-tab-pane>
      <a-tab-pane :tab="'Control VM'" key="instances">
        <a-table
          class="table"
          size="small"
          :columns="vmColumns"
          :dataSource="this.virtualmachines"
          :rowKey="item => item.id"
          :pagination="false"
        >
          <template slot="name" slot-scope="text, record">
            <router-link :to="{ path: '/vm/' + record.id }">{{ record.name }}</router-link>
          </template>
          <template slot="state" slot-scope="text">
            <status :text="text ? text : ''" displayText />
          </template>
          <template slot="instancename" slot-scope="text">
            <status :text="text ? text : ''" />{{ text }}
          </template>
          <template slot="ipaddress" slot-scope="text">
            <status :text="text ? text : ''" />{{ text }}
          </template>
          <template slot="hostname" slot-scope="text, record">
            <router-link :to="{ path: '/host/' + record.hostid }">{{ record.hostname }}</router-link>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane :tab="'Desktop VM'" key="desktops">
        <a-table
          class="table"
          size="small"
          :columns="desktopColumns"
          :dataSource="desktops"
          :rowKey="item => item.id"
          :pagination="false"
        >
          <template slot="name" slot-scope="text, record">
            <router-link :to="{ path: '/vm/' + record.id }">{{ record.name }}</router-link>
          </template>
          <template slot="state" slot-scope="text">
            <status :text="text ? text : ''" displayText />
          </template>
          <template slot="instancename" slot-scope="text">
            <status :text="text ? text : ''" />{{ text }}
          </template>
          <template slot="ipaddress" slot-scope="text">
            <status :text="text ? text : ''" />{{ text }}
          </template>
          <template slot="hostname" slot-scope="text, record">
            <router-link :to="{ path: '/host/' + record.hostid }">{{ record.hostname }}</router-link>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      :visible="showAddNetworkModal"
      :title="'ADD IP Range to Desktop'"
      :maskClosable="false"
      :okText="$t('label.ok')"
      :cancelText="$t('label.cancel')"
      @cancel="closeModals"
      @ok="submitAddNetwork">
      {{ '이 Desktop 에 추가할 IP 범위를 입력하십시오.' }}
      <div class="modal-form">
        <p class="modal-form__label">{{ 'Gateway' }}:</p>
        <a-input v-model="addNetworkData.gateway"></a-input>
        <p class="modal-form__label">{{ 'Netmask' }}:</p>
        <a-input v-model="addNetworkData.netmask"></a-input>
        <p class="modal-form__label">{{ 'Start IP' }}:</p>
        <a-input v-model="addNetworkData.startip"></a-input>
        <p class="modal-form__label">{{ 'End IP' }}:</p>
        <a-input v-model="addNetworkData.endip"></a-input>
      </div>
    </a-modal>

  </a-spin>
</template>

<script>

import { api } from '@/api'
import { mixinDevice } from '@/utils/mixin.js'
import ResourceLayout from '@/layouts/ResourceLayout'
import Status from '@/components/widgets/Status'
import DetailsTab from '@/components/view/DetailsTab'
import DesktopNicsTable from '@/views/network/DesktopNicsTable'
import ListResourceTable from '@/components/view/ListResourceTable'
import TooltipButton from '@/components/widgets/TooltipButton'

export default {
  name: 'DesktopTab',
  components: {
    ResourceLayout,
    DetailsTab,
    DesktopNicsTable,
    Status,
    ListResourceTable,
    TooltipButton
  },
  mixins: [mixinDevice],
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
  inject: ['parentFetchData'],
  data () {
    return {
      vm: {},
      networks: [],
      instances: [],
      desktops: [],
      iprange: [],
      totalStorage: 0,
      currentTab: 'details',
      showAddNetworkModal: false,
      addNetworkData: {
        allNetworks: [],
        network: '',
        ip: ''
      },
      loadingNic: false,
      secondaryIPs: [],
      selectedNicId: '',
      vmColumns: [
        {
          title: this.$t('label.name'),
          dataIndex: 'name',
          scopedSlots: { customRender: 'name' }
        },
        {
          title: this.$t('label.state'),
          dataIndex: 'state',
          scopedSlots: { customRender: 'state' }
        },
        {
          title: this.$t('label.instancename'),
          dataIndex: 'instancename'
        },
        {
          title: this.$t('label.ip'),
          dataIndex: 'ipaddress'
        },
        {
          title: this.$t('label.hostid'),
          dataIndex: 'hostname',
          scopedSlots: { customRender: 'hostname' }
        }
      ],
      editNicResource: {},
      desktopColumns: [
        {
          title: this.$t('label.name'),
          dataIndex: 'name',
          scopedSlots: { customRender: 'name' }
        },
        {
          title: this.$t('label.state'),
          dataIndex: 'state',
          scopedSlots: { customRender: 'state' }
        },
        {
          title: this.$t('label.instancename'),
          dataIndex: 'type'
        },
        {
          title: this.$t('label.ip'),
          dataIndex: 'path',
          scopedSlots: { customRender: 'path' }
        },
        {
          title: this.$t('label.hostid'),
          dataIndex: 'hypervisor',
          scopedSlots: { customRender: 'hypervisor' }
        }
      ],
      iprangeColumns: [
        {
          title: this.$t('label.gateway'),
          dataIndex: 'gateway',
          scopedSlots: { customRender: 'gateway' }
        },
        {
          title: this.$t('label.netmask'),
          dataIndex: 'netmask'
        },
        {
          title: this.$t('label.startip'),
          dataIndex: 'startip'
        },
        {
          title: this.$t('label.endip'),
          dataIndex: 'endip'
        }
      ]
    }
  },
  created () {
    this.vm = this.resource
    this.fetchData()
  },
  watch: {
    resource: function (newItem, oldItem) {
      this.vm = newItem
      this.fetchData()
    },
    $route: function (newItem, oldItem) {
      this.setCurrentTab()
    }
  },
  mounted () {
    this.setCurrentTab()
  },
  methods: {
    setCurrentTab () {
      this.currentTab = this.$route.query.tab ? this.$route.query.tab : 'details'
    },
    handleChangeTab (e) {
      this.currentTab = e
      const query = Object.assign({}, this.$route.query)
      query.tab = e
      history.replaceState(
        {},
        null,
        '#' + this.$route.path + '?' + Object.keys(query).map(key => {
          return (
            encodeURIComponent(key) + '=' + encodeURIComponent(query[key])
          )
        }).join('&')
      )
    },
    fetchData () {
      this.instanceLoading = true
      this.virtualmachines = this.resource.virtualmachines || []
      this.virtualmachines.map(x => { x.ipaddress = x.nic[0].ipaddress })
      this.instanceLoading = false
      this.networks = []
      if (!this.vm || !this.vm.id) {
        return
      }
      api('listNetworks', { listall: true, networkid: this.resource.networkid }).then(json => {
        this.networks = json.listnetworksresponse.network
        if (this.networks) {
          console.log(this.networks)
          this.networks.sort((a, b) => { return a.deviceid - b.deviceid })
        }
        this.$set(this.resource, 'networks', this.networks)
      })
    },
    showAddModal () {
      this.showAddNetworkModal = true
    },
    closeModals () {
      this.showAddNetworkModal = false
      this.addNetworkData.network = ''
      this.addNetworkData.ip = ''
    },
    submitAddNetwork () {
      const params = {}
      params.virtualmachineid = this.vm.id
      params.networkid = this.addNetworkData.network
      if (this.addNetworkData.ip) {
        params.ipaddress = this.addNetworkData.ip
      }
      this.showAddNetworkModal = false
      this.loadingNic = true
      api('addNicToVirtualMachine', params).then(response => {
        this.$pollJob({
          jobId: response.addnictovirtualmachineresponse.jobid,
          successMessage: this.$t('message.success.add.network'),
          successMethod: () => {
            this.loadingNic = false
            this.closeModals()
            this.parentFetchData()
          },
          errorMessage: this.$t('message.add.network.failed'),
          errorMethod: () => {
            this.loadingNic = false
            this.closeModals()
            this.parentFetchData()
          },
          loadingMessage: this.$t('message.add.network.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.closeModals()
            this.parentFetchData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loadingNic = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
  .page-header-wrapper-grid-content-main {
    width: 100%;
    height: 100%;
    min-height: 100%;
    transition: 0.3s;
    .vm-detail {
      .svg-inline--fa {
        margin-left: -1px;
        margin-right: 8px;
      }
      span {
        margin-left: 10px;
      }
      margin-bottom: 8px;
    }
  }

  .list {
    margin-top: 20px;

    &__item {
      display: flex;
      flex-direction: column;
      align-items: flex-start;

      @media (min-width: 760px) {
        flex-direction: row;
        align-items: center;
      }
    }
  }

  .modal-form {
    display: flex;
    flex-direction: column;

    &__label {
      margin-top: 20px;
      margin-bottom: 5px;
      font-weight: bold;

      &--no-margin {
        margin-top: 0;
      }
    }
  }

  .actions {
    display: flex;
    flex-wrap: wrap;

    button {
      padding: 5px;
      height: auto;
      margin-bottom: 10px;
      align-self: flex-start;

      &:not(:last-child) {
        margin-right: 10px;
      }
    }

  }

  .label {
    font-weight: bold;
  }

  .attribute {
    margin-bottom: 10px;
  }

  .ant-tag {
    padding: 4px 10px;
    height: auto;
  }

  .title {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;

    a {
      margin-right: 30px;
      margin-bottom: 10px;
    }

    .ant-tag {
      margin-bottom: 10px;
    }

    &__details {
      display: flex;
    }

    .tags {
      margin-left: 10px;
    }

  }

  .ant-list-item-meta-title {
    margin-bottom: -10px;
  }

  .divider-small {
    margin-top: 20px;
    margin-bottom: 20px;
  }

  .list-item {

    &:not(:first-child) {
      padding-top: 25px;
    }

  }
</style>

<style scoped>
.wide-modal {
  min-width: 50vw;
}

/deep/ .ant-list-item {
  padding-top: 12px;
  padding-bottom: 12px;
}
</style>
