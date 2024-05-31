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
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical">
        <a-form-item name="name" ref="name" :label="$t('label.name')">
          <a-input
            :placeholder="apiParams.name.description"
            v-model:value="form.name"
          />
        </a-form-item>
        <a-form-item name="displaytext" ref="displaytext" :label="$t('label.displaytext')">
          <a-input
            :placeholder="apiParams.description.description"
            v-model:value="form.description"
          />
        </a-form-item>
        <a-form-item name="url" ref="url" :label="$t('label.url')">
          <a-input
            :placeholder="apiParams.drclusterurl.description"
            v-model:value="form.drclusterurl"
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
          <a-button @click="testConnCode" :disabled="buttonDisabled" type="primary">{{ $t('label.disaster.recovery.cluster.start.connection.test.description') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>
<script>
import { ref, reactive, toRaw } from 'vue'
import { api } from '@/api'
import TooltipLabel from '@/components/widgets/TooltipLabel.vue'

export default {
  name: 'updateDisasterRecoveryCluster',
  components: { TooltipLabel },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
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
      states: [],
      stateLoading: false,
      loading: false,
      spinTemplate: false,
      showCode: false,
      buttonDisabled: false,
      alertDescription: this.$t('message.disaster.recovery.cluster.connection.test.update.description'),
      fileList: []
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('updateDisasterRecoveryCluster')
  },
  created () {
    this.states = [
      {
        id: 'Enabled',
        name: this.$t('state.enabled')
      },
      {
        id: 'Disabled',
        name: this.$t('state.disabled')
      }
    ]
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
    },
    fetchData () {
      var selectedState = 0
      if (!this.isObjectEmpty(this.resource)) {
        for (var i = 0; i < this.states.length; ++i) {
          if (this.states[i].id === this.resource.state) {
            selectedState = i
            break
          }
        }
      }
      this.form.state = selectedState
      this.fillEditFormFieldValues()
    },
    fillEditFormFieldValues () {
      const form = this.form
      console.log('formformformformformform')
      console.log(form)
      this.loading = true
      Object.keys(this.apiParams).forEach(item => {
        const field = this.apiParams[item]
        let fieldValue = null
        let fieldName = null
        if (field.type === 'list' || field.name === 'account') {
          fieldName = field.name.replace('ids', 'name').replace('id', 'name')
        } else {
          fieldName = field.name
        }
        fieldValue = this.resource[fieldName] ? this.resource[fieldName] : null
        if (fieldValue) {
          form[field.name] = fieldValue
        }
      })
      this.loading = false
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    arrayHasItems (array) {
      return array !== null && array !== undefined && Array.isArray(array) && array.length > 0
    },
    isObjectEmpty (obj) {
      return !(obj !== null && obj !== undefined && Object.keys(obj).length > 0 && obj.constructor === Object)
    },
    beforeUpload (file) {
      this.fileList = [file]
      this.form.file = file
      return false
    },
    testConnCode () {
      this.showCode = !this.showCode
      this.buttonDisabled = true
    },
    handleKeyPress (event) {
      // Check if the button is disabled and if a keyboard key is pressed
      if (this.buttonDisabled && event.key !== 'Tab') {
        this.buttonDisabled = false
        this.alertDescription = this.testDescText === 'Disappear Code' ? this.$t('message.waiting.dr.simulation.test') : this.$t('message.disaster.recovery.cluster.connection.test.update.description')
        this.spinTemplate = true
      }
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
          id: this.resource.id,
          drclusterstatus: this.resource.drclusterstatus,
          mirroringagentstatus: this.resource.mirroringagentstatus
        }
        if (this.isValidValueForKey(values, 'state') && this.arrayHasItems(this.states)) {
          params.state = this.states[values.state].id
        }
        api('updateDisasterRecoveryCluster', params).then(json => {
          this.$message.success(`${this.$t('message.success.update.disaster.recovery.cluster')}: ${this.resource.name}`)
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
    width: 60vw;

    @media (min-width: 500px) {
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
