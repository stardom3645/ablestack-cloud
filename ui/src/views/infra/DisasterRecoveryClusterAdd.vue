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
          <span v-html="$t('message.add.disaster.recovery.cluster')" />
        </template>
      </a-alert>
      <a-form-item name="name" ref="name" :label="$t('label.name')">
        <a-input
          :placeholder="'temp'"
          v-model:value="form.name"
        />
      </a-form-item>

      <a-form-item name="displaytext" ref="displaytext" :label="$t('label.displaytext')">
        <a-input
          v-model:value="form.displaytext"
          :placeholder="'temp'"
        />
      </a-form-item>

      <a-card size="small" :title="$t('label.add.disaster.recovery.cluster.info')" style="margin-top: 15px">
        <a-form-item name="url" ref="url" :label="$t('label.url')">
          <a-input
            :placeholder="'http://10.10.1.10:8080'"
            v-model:value="form.url"
          />
        </a-form-item>
        <a-form-item name="apikey" ref="apikey" :label="$t('label.apikey')">
          <a-input
            :placeholder="temp"
            v-model:value="form.apikey"
          />
        </a-form-item>
        <a-form-item name="secretkey" ref="secretkey" :label="$t('label.secret.key')">
          <a-input
            :placeholder="temp"
            v-model:value="form.secretkey"
          />
        </a-form-item>

      <a-form-item name="file" ref="file" :label="$t('label.add.disaster.recovery.cluster.info.glue.pri.key')">
          <a-upload-dragger
            :multiple="false"
            :fileList="fileList"
            @remove="handleRemove"
            :beforeUpload="beforeUpload"
            v-model:value="form.file">
            <p class="ant-upload-drag-icon">
              <cloud-upload-outlined />
            </p>
            <p class="ant-upload-text" v-if="fileList.length === 0">
              {{ $t('label.volume.volumefileupload.description') }}
            </p>
          </a-upload-dragger>
        </a-form-item>
      </a-card>

      <a-divider v-if="showCode" />

      <div v-if="showCode">
        <template v-if="!spinTemplate">
          <a-spin :tip="loading">
            <a-alert :message="$t('message.disaster.recovery.cluster.connection.test.title')" :description="alertDescription" :type="info">
            </a-alert>
          </a-spin>
        </template>
        <template v-else>
          <a-alert :message="$t('message.disaster.recovery.cluster.connection.test.title')" :description="alertDescription" :type="info">
          </a-alert>
        </template>
      </div>

      <div :span="24" class="action-button">
        <a-button @click="() => $emit('close-action')">{{ $t('label.cancel') }}</a-button>
        <a-button @click="testConnCode" :disabled="buttonDisabled" v-if="!testConnResult" type="primary">{{ $t('label.disaster.recovery.cluster.start.connection.test.description') }}</a-button>
        <a-button @click="handleSubmit" :disabled="!buttonDisabled" v-if="testConnResult" ref="submit" type="primary">{{ $t('label.ok') }}</a-button>
      </div>

    </a-form>
  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { api } from '@/api'
import DedicateDomain from '../../components/view/DedicateDomain'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipLabel from '@/components/widgets/TooltipLabel.vue'
import { axios } from '@/utils/request'
import store from '@/store'
import { Spin, Alert } from 'ant-design-vue'

export default {
  name: 'ClusterAdd',
  components: {
    TooltipLabel,
    DedicateDomain,
    ResourceIcon,
    Spin,
    Alert
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
      spinTemplate: false,
      showCode: false,
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
      buttonDisabled: false,
      alertDescription: this.$t('message.disaster.recovery.cluster.connection.test.description'),
      testConnResult: false
    }
  },
  computed: {
    testDescText () {
      return this.showCode ? 'Disappear Code' : 'Show Code'
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
        secretkey: [{ required: true, message: this.$t('label.required') }]
        // file: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      this.fetchZones()
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
    handleRemove (file) {
      const index = this.fileList.indexOf(file)
      const newFileList = this.fileList.slice()
      newFileList.splice(index, 1)
      this.fileList = newFileList
      this.form.file = undefined
    },
    // 재난복구클러스터 연결 테스트
    testConnCode () {
      this.showCode = !this.showCode
      this.buttonDisabled = true
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        const params = {
          name: values.name,
          description: values.description,
          drClusterUrl: values.url,
          apiKey: values.apikey,
          usersecretkey: values.secretkey
        }
        console.log(params)
        api('connectivityTestsDisasterRecovery', params).then(json => {
          this.testConnResult = json.connectivitytestsdisasterrecoveryresponse
          if (this.testConnResult === false) {
            alert('sdfsdfsd')
            return
          } else {
            this.showCode = false
            this.spinTemplate = false
          }
          this.$emit('refresh-data')
        }).catch(error => {
          this.showCode = !this.showCode
          this.buttonDisabled = false
          this.$notification.error({
            message: this.$t('message.request.failed'),
            description: (error.response && error.response.headers && error.response.headers['x-description']) || error.message
          })
        })
        this.closeAction()
      })
    },
    handleKeyPress (event) {
      // Check if the button is disabled and if a keyboard key is pressed
      if (this.buttonDisabled && event.key !== 'Tab') {
        this.buttonDisabled = false
        this.alertDescription = this.testDescText === 'Disappear Code' ? this.$t('message.waiting.dr.simulation.test') : this.$t('message.disaster.recovery.cluster.connection.test.description')
        this.spinTemplate = true
      }
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
        this.buttonDisabled = false
        setTimeout(() => {
          this.showCode = false
        }, 3000)
        // api('addAutomationController', params).then(json => {
        //   const jobId = json.addautomationcontrollerresponse.jobid
        //   this.$pollJob({
        //     jobId,
        //     title: this.$t('label.automation.controller.deploy'),
        //     description: values.name,
        //     successMethod: () => {
        //       this.$notification.success({
        //         message: this.$t('message.success.create.automation.controller'),
        //         duration: 0
        //       })
        //       eventBus.emit('automation-controller-refresh-data')
        //     },
        //     loadingMessage: `${this.$t('label.automation.controller.deploy')} ${values.name} ${this.$t('label.in.progress')}`,
        //     catchMessage: this.$t('error.fetching.async.job.result'),
        //     catchMethod: () => {
        //       eventBus.emit('automation-controller-refresh-data')
        //     },
        //     action: {
        //       isFetchData: false
        //     }
        //   })
        //   this.closeAction()
        // }).catch(error => {
        //   this.$notifyError(error)
        // }).finally(() => {
        //   this.loading = false
        // })
        this.handleUpload()
        // api('getUploadParamsForTemplate', params).then(json => {
        //   this.uploadParams = (json.postuploadtemplateresponse && json.postuploadtemplateresponse.getuploadparams) ? json.postuploadtemplateresponse.getuploadparams : ''
        //   this.handleUpload()
        //   if (this.userdataid !== null) {
        //     this.linkUserdataToTemplate(this.userdataid, json.postuploadtemplateresponse.template[0].id)
        //   }
        // }).catch(error => {
        //   this.$notifyError(error)
        // }).finally(() => {
        //   this.loading = false
        // })
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
  },
  mounted () {
    document.body.addEventListener('keydown', this.handleKeyPress)
  },
  beforeUnmount () {
    document.body.removeEventListener('keydown', this.handleKeyPress)
  }
}
</script>

<style scoped lang="scss">
.form {

  width: 80vw;

  @media (min-width: 700px) {
    width: 550px;
  }
}

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

.test-con-content {
  padding: 50px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
}
</style>
