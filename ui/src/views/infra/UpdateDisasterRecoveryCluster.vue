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
        <a-form-item name="url" ref="url" :label="$t('label.url')">
          <a-input
            :placeholder="'http://10.10.1.10:8080'"
            v-model:value="form.url"
          />
        </a-form-item>
        <a-form-item name="apikey" ref="apikey" :label="$t('label.apikey')">
          <!--          <span>-->
          <!--            <a-alert type="warning">-->
          <!--              <template #message>-->
          <!--                <span v-html="ipv6NetworkOfferingEnabled ? $t('message.offering.internet.protocol.warning') : $t('message.offering.ipv6.warning')" />-->
          <!--              </template>-->
          <!--            </a-alert>-->
          <!--            <br/>-->
          <!--          </span>-->
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

        <a-divider />

        <a-spin :spinning="spinning">
          <a-alert :message="$t('message.disaster.recovery.cluster.connection.test.title')" :description="$t('message.disaster.recovery.cluster.connection.test.description')">
          </a-alert>
        </a-spin>
        <div class="spin-state" style="margin-top: 16px">
          <tooltip-label :title="$t('label.disaster.recovery.cluster.start.connection.test.description')"/>
          <a-switch v-model:checked="spinning" style="margin-left: 10px"/>
        </div>

        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button :loading="loading" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
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
  name: 'updateAutomationControllerVersion',
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
      fileList: []
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('updateAutomationControllerVersion')
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
          id: this.resource.id
        }
        if (this.isValidValueForKey(values, 'state') && this.arrayHasItems(this.states)) {
          params.state = this.states[values.state].id
        }
        api('updateAutomationControllerVersion', params).then(json => {
          this.$message.success(`${this.$t('message.success.update.automation.controller.template.version')}: ${this.resource.name}`)
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
