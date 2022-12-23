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
  <div class="form-layout">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        @submit="handleSubmit"
        layout="vertical">
        <a-form-item ref="title" name="title">
          <template #label>
            <tooltip-label :title="$t('label.title')" :tooltip="$t('placeholder.title')"/>
          </template>
          <a-input
            v-model:value="form.title"
            :placeholder="$t('placeholder.title')"/>
        </a-form-item>
        <a-form-item ref="purpose" name="purpose">
          <template #label>
            <tooltip-label :title="$t('label.purpose')" :tooltip="$t('placeholder.purpose')"/>
          </template>
          <a-input
            v-model:value="form.purpose"
            :placeholder="$t('placeholder.purpose')"/>
        </a-form-item>
        <a-form-item ref="quantity" name="quantity">
          <template #label>
            <tooltip-label :title="$t('label.quantity')" :tooltip="$t('placeholder.quantity')"/>
          </template>
          <a-input-number v-model:value="form.quantity" :min="1" :max="10" style="width: 100%" />
        </a-form-item>
        <a-form-item ref="cpu" name="cpu">
          <template #label>
            <tooltip-label :title="$t('label.cpu')" :tooltip="$t('placeholder.cpu')"/>
          </template>
          <a-select v-model:value="form.cpu" :placeholder="$t('placeholder.cpu')">
            <a-select-option value="16">16 Core</a-select-option>
            <a-select-option value="12">12 Core</a-select-option>
            <a-select-option value="8">8 Core</a-select-option>
            <a-select-option value="6">6 Core</a-select-option>
            <a-select-option value="4">4 Core</a-select-option>
            <a-select-option value="2">2 Core</a-select-option>
            <a-select-option value="1">1 Core</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item ref="memory" name="memory">
          <template #label>
            <tooltip-label :title="$t('label.memory')" :tooltip="$t('placeholder.memory')"/>
          </template>
          <a-select v-model:value="form.memory" :placeholder="$t('placeholder.memory')">
            <a-select-option value="32768">32G</a-select-option>
            <a-select-option value="16384">16G</a-select-option>
            <a-select-option value="8192">8G</a-select-option>
            <a-select-option value="4096">4G</a-select-option>
            <a-select-option value="2048">2G</a-select-option>
            <a-select-option value="1024">1G</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item ref="disksize" name="disksize">
          <template #label>
            <tooltip-label :title="$t('label.disksize')" :tooltip="$t('placeholder.disksize')"/>
          </template>
          <a-input-number v-model:value="form.disksize" :min="1" :max="1000" style="width: 100%" />
        </a-form-item>
        <a-form-item ref="network"  name="network">
          <template #label>
            <tooltip-label :title="$t('label.network')" :tooltip="$t('placeholder.network')"/>
          </template>
          <a-radio-group v-model:value="form.network" button-style="solid" @change="selected => { changeNetwork(selected.target.value) }">
            <a-radio-button value="ISOLATED">격리 네트워크</a-radio-button>
            <a-radio-button value="L2">L2 네트워크</a-radio-button>
            <a-radio-button value="SHARED">공유 네트워크</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button :loading="loading" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>
<script>
import { ref, reactive, toRaw } from 'vue'
import { api } from '@/api'
import store from '@/store'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'AddResourceRequest',
  components: {
    TooltipLabel
  },
  props: {},
  data () {
    return {
      loading: false,
      selectedNetwork: 'ISOLATED'
    }
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        quantity: '1',
        cpu: '1',
        memory: '1',
        disksize: '50',
        network: 'ISOLATED'
      })
      this.rules = reactive({
        title: [{ required: true, message: this.$t('message.error.required.input') }],
        purpose: [{ required: true, message: this.$t('message.error.required.input') }],
        quantity: [{ required: true, message: this.$t('message.error.required.input') }],
        cpu: [{ required: true, message: this.$t('message.error.required.input') }],
        memory: [{ required: true, message: this.$t('message.error.required.input') }],
        network: [{ required: true, message: this.$t('message.error.select') }],
        disksize: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      // this.fetchClusterData()
      // this.fetchTemplateVersionData()
      // this.fetchServiceOfferingData()
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    arrayHasItems (array) {
      return array !== null && array !== undefined && Array.isArray(array) && array.length > 0
    },
    handleAccessTypeChange (pvlan) {
      this.accessType = pvlan
      this.fetchNetworkData()
    },
    // validateConfirmPassword (rule, value, callback) {
    //   if (!value || value.length === 0) {
    //     callback()
    //   } else if (rule.field === 'confirmpassword') {
    //     const form = this.form
    //     const messageConfirm = this.$t('error.password.not.match')
    //     const passwordVal = form.getFieldValue('password')
    //     if (passwordVal && passwordVal !== value) {
    //       callback(messageConfirm)
    //     } else {
    //       callback()
    //     }
    //   } else {
    //     callback()
    //   }
    //
    // },
    fetchTemplateVersionData () {
      this.controllerVersions = []
      const params = {}
      this.controllerVersionLoading = true
      api('listDesktopControllerVersions', params).then(json => {
        var items = json.listdesktopcontrollerversionsresponse.desktopcontrollerversion
        if (items != null) {
          this.controllerVersions = items.filter(it => it.state === 'Enabled')
          // console.log('this.controllerVersions :>> ', this.controllerVersions)
        }
      }).finally(() => {
        this.controllerVersionLoading = false
        if (this.arrayHasItems(this.controllerVersions)) {
          // console.log('123 :>> ')
          this.form.controllerversionid = 0
        }
      })
    },
    fetchServiceOfferingData () {
      this.serviceOfferings = []
      const params = {}
      this.serviceOfferingLoading = true
      api('listServiceOfferings', params).then(json => {
        var items = json.listserviceofferingsresponse.serviceoffering
        if (items != null) {
          this.serviceOfferings = items.filter(it => it.iscustomized === false)
        }
      }).finally(() => {
        this.serviceOfferingLoading = false
        if (this.arrayHasItems(this.serviceOfferings)) {
          this.form.serviceofferingid = 0
        }
      })
    },
    fetchClusterData () {
      this.clusters = []
      const params = {
        domainid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainid,
        account: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.account,
        listall: true
      }
      api('listDesktopClusters', params).then(json => {
        var items = json.listdesktopclustersresponse.desktopcluster
        if (items != null) {
          this.clusters.push(items)
        }
      }).finally(() => {
        this.fetchNetworkData()
      })
    },
    fetchNetworkData () {
      this.networks = []
      const params = {
        domainid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainid,
        account: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.account
      }
      this.networkLoading = true
      this.handleNetworkChange(null)
      api('listNetworks', params).then(json => {
        var items = json.listnetworksresponse.network
        if (items !== null) {
          if (this.accessType === 'internal') this.networks = items.filter(it => it.type.includes('L2'))
          if (this.accessType === 'external') this.networks = items.filter(it => it.type.includes('Isolated'))
          if (this.accessType === 'mixed') this.networks = items.filter(it => it.type.includes('Shared'))

          // console.log(this.accessType, this.networks)
          this.handleNetworkChange(this.networks[0])

          // for (var i = 0; i < items.length; i++) {
          //   if (this.accessType === 'internal' && items[i].type === 'L2') {
          //     this.networks.push(items[i])
          //     this.handleNetworkChange(this.networks[0])
          //   }
          //   if (this.accessType === 'external' && items[i].type === 'Isolated') {
          //     this.networks.push(items[i])
          //     if (this.clusters.length !== 0) {
          //       for (var j = 0; j < this.clusters[0].length; j++) {
          //         if ([this.clusters[0][j].networkid].includes(items[i].id)) {
          //           this.networks.pop(items[i])
          //         }
          //         this.handleNetworkChange(this.networks[0])
          //       }
          //     } else {
          //       this.handleNetworkChange(this.networks[0])
          //     }
          //   }
          //   if (this.accessType === 'mixed' && items[i].type === 'Shared') {
          //     this.networks.push(items[i])
          //     this.handleNetworkChange(this.networks[0])
          //   }
          // }
        }
      }).finally(() => {
        this.networkLoading = false
        if (this.arrayHasItems(this.networks)) {
          this.form.networkid = 0
        } else {
          this.form.networkid = null
        }
      })
    },
    changeNetwork (network) {
      this.selectedNetwork = network
    },
    handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        this.loading = true
        const params = {
          title: values.title,
          quantity: values.quantity,
          purpose: values.purpose,
          item: 'CREATE VM',
          cpunumber: values.cpu,
          memory: values.memory,
          network: this.selectedNetwork,
          disksize: values.disksize
        }
        // console.log('params :>> ', params)
        api('addResourceRequest', params).then(json => {
          this.$notification.success({
            message: this.$t('label.resource.request.create'),
            description: `${this.$t('message.success.create.resource.request')}`
          })
          this.$emit('refresh-data')
          this.closeAction()
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    closeAction () {
      this.$emit('close-action')
    }
  }
}
</script>
<style scoped lang="less">
  .form-layout {
    width: 80vw;
    @media (min-width: 600px) {
      width: 450px;
    }
  }
  .action-button {
    text-align: right;
    button {
      margin-right: 5px;
    }
  }
</style>
