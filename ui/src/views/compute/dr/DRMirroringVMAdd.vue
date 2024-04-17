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
    <a-form
      :ref="formRef"
      :model="form"
      :rules="rules"
      layout="vertical"
      class="form"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit"
    >
      <a-alert style="margin-bottom: 5px" type="warning" show-icon>
        <template #message>
          <span v-html="$t('message.add.dr.mirroring.vm')" />
        </template>
      </a-alert>
      <a-form-item
          ref="drCluster"
          name="drCluster"
          :label="$t('label.cluster')">
        <a-select
            v-model:value="form.drCluster"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :placeholder="$t('placeholder.dr.cluster.cluster.selection')"
            >
          <a-select-option v-for="(opt, optIndex) in this.drCluster" :key="optIndex">
            {{ opt.name }} {{ opt.version }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item
          ref="drCluster"
          name="drCluster"
          :label="$t('label.compute.offerings')">
        <a-select
            v-model:value="form.drCluster"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :placeholder="$t('placeholder.dr.cluster.compute.offering.selection')"
        >
          <a-select-option v-for="(opt, optIndex) in this.fakeOfferings" :key="optIndex">
            {{ opt.name }} {{ opt.version }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item
          ref="drCluster"
          name="drCluster"
          :label="$t('label.network.name')">
        <a-select
            v-model:value="form.drCluster"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :placeholder="$t('placeholder.dr.cluster.network.selection')"
        >
          <a-select-option v-for="(opt, optIndex) in this.fakeNetworks" :key="optIndex">
            {{ opt.name }} {{ opt.version }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <div :span="24" class="action-button">
        <a-button @click="cancel">{{ $t('label.cancel') }}</a-button>
        <a-button @click="handleSubmit" ref="submit" type="primary">{{ $t('label.ok') }}</a-button>
      </div>

    </a-form>
  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { api } from '@/api'
import DedicateDomain from '@comp/view/DedicateDomain.vue'
import ResourceIcon from '@/components/view/ResourceIcon.vue'
import TooltipLabel from '@/components/widgets/TooltipLabel.vue'
import { axios } from '@/utils/request'
import store from '@/store'

export default {
  name: 'ClusterAdd',
  components: {
    TooltipLabel,
    DedicateDomain,
    ResourceIcon
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  inject: ['parentFetchData'],
  setup () {
    const spinning = ref(false)
    const changeSpinning = () => {
      spinning.value = !spinning.value
    }
    return {
      spinning,
      changeSpinning
    }
  },
  data () {
    return {
      loading: false,
      zonesList: [],
      showDedicated: false,
      dedicatedDomainId: null,
      dedicatedAccount: null,
      domainError: false,
      params: [],
      placeholder: {
        name: null,
        gateway: null,
        netmask: null,
        startip: null,
        endip: null
      },
      fileList: [],
      drCluster: [],
      fakeOfferings: [
        { id: 1, name: 'Offering 1', price: 10 },
        { id: 2, name: 'Offering 2', price: 20 },
        { id: 3, name: 'Offering 3', price: 30 }
      ],
      fakeNetworks: [
        { id: 1, name: 'Network 1', price: 10 },
        { id: 2, name: 'Network 2', price: 20 },
        { id: 3, name: 'Network 3', price: 30 }
      ]
    }
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        name: [{ required: true, message: this.$t('label.required') }],
        url: [{ required: true, message: this.$t('label.required') }],
        apikey: [{ required: true, message: this.$t('label.required') }],
        secretkey: [{ required: true, message: this.$t('label.required') }],
        file: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      this.fetchZones()
      this.fetchDRClusterList()
    },
    fetchDRClusterList () {
      api('getDisasterRecoveryClusterList', { name: 'test-sec-cluster-01' }).then(json => {
        this.drCluster = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster || []
        console.log(this.drCluster)
      }).finally(() => {
      })
    },
    fetchZones () {
      this.loading = true
      api('listZones', { showicon: true }).then(response => {
        this.zonesList = response.listzonesresponse.zone || []
        this.form.zoneid = this.zonesList[0].id
        this.params = this.$store.getters.apis.createPod.params
        Object.keys(this.placeholder).forEach(item => { this.returnPlaceholder(item) })
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    },
    toggleDedicate () {
      this.dedicatedDomainId = null
      this.dedicatedAccount = null
      this.showDedicated = !this.showDedicated
    },
    beforeUpload (file) {
      this.fileList = [file]
      this.form.file = file
      return false
    },
    handleUpload () {
      const { fileList } = this
      const formData = new FormData()
      fileList.forEach(file => {
        formData.append('files[]', file)
      })
      this.uploadPercentage = 0
      axios.post(this.uploadParams.postURL,
        formData,
        {
          headers: {
            'content-type': 'multipart/form-data',
            'x-signature': this.uploadParams.signature,
            'x-expires': this.uploadParams.expires,
            'x-metadata': this.uploadParams.metadata,
            'x-host': this.uploadParams.postURL.split('/')[2]
          },
          onUploadProgress: (progressEvent) => {
            this.uploadPercentage = Number(parseFloat(100 * progressEvent.loaded / progressEvent.total).toFixed(1))
          },
          timeout: 86400000
        }).then((json) => {
        this.$notification.success({
          message: this.$t('message.success.upload'),
          description: this.$t('message.success.upload.template.description')
        })
        this.$emit('refresh-data')
        this.closeAction()
      }).catch(e => {
        this.$notification.error({
          message: this.$t('message.upload.failed'),
          description: `${this.$t('message.upload.template.failed.description')} -  ${e}`,
          duration: 0
        })
      })
    },
    cancel () {
      this.$emit('cancel')
    },
    handleRemove (file) {
      const index = this.fileList.indexOf(file)
      const newFileList = this.fileList.slice()
      newFileList.splice(index, 1)
      this.fileList = newFileList
      this.form.file = undefined
    },
    handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        this.loading = true
        const params = {
          name: values.name,
          description: values.description,
          domainid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainid,
          domainname: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainname,
          account: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.account,
          accountid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.accountid
        }
        console.log(params)
        this.loading = false
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    returnPlaceholder (field) {
      this.params.find(i => {
        if (i.name === field) this.placeholder[field] = i.description
      })
    }
  }
}
</script>

<style scoped lang="scss">

.required {
  color: #ff0000;

  &-label {
    display: none;

    &--visible {
      display: block;
    }

  }
}

.ant-alert-warning {
  border: 1px solid #ffe58f;
  background-color: #fffbe6;
}
</style>
