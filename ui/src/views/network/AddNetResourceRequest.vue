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
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'AddNetResourceRequest',
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
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        quantity: '1',
        network: 'ISOLATED'
      })
      this.rules = reactive({
        title: [{ required: true, message: this.$t('message.error.required.input') }],
        purpose: [{ required: true, message: this.$t('message.error.required.input') }],
        quantity: [{ required: true, message: this.$t('message.error.required.input') }],
        network: [{ required: true, message: this.$t('message.error.required.input') }]
      })
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
          item: 'CREATE NETWORK',
          network: this.selectedNetwork
        }
        // console.log('params :>> ', params)
        api('addResourceRequest', params).then(json => {
          this.$notification.success({
            message: this.$t('label.add.network.request'),
            description: `${this.$t('message.success.add.network.request')}`
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
