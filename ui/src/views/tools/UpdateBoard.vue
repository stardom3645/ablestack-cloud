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
        <a-form-item ref="type" name="type">
          <template #label>
            <tooltip-label :title="$t('label.type')" :tooltip="$t('placeholder.type')"/>
          </template>
          <a-radio-group v-model:value="form.type" button-style="solid" @change="selected => { changeOption(selected.target.value) }">
            <a-radio-button value="NEWS">공지사항</a-radio-button>
            <a-radio-button value="SHARE">자료공유</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item ref="content" name="content">
          <template #label>
            <tooltip-label :title="$t('label.content')" :tooltip="$t('placeholder.content')"/>
          </template>
          <a-textarea v-model:value="form.content" :placeholder="$t('placeholder.content')" :rows="4" />
        </a-form-item>
        <a-form-item ref="file" name="file" :label="$t('label.file')">
          <a-upload-dragger
            v-model:fileList="fileList"
            name="file"
            :multiple="true"
            action=""
            @change="handleChange"
          >
            <p class="ant-upload-drag-icon">
              <inbox-outlined />
            </p>
            <p class="ant-upload-text">파일을 끌어오거나 클릭하여 파일을 선태해주세요.</p>
          </a-upload-dragger>
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
  name: 'UpdateResourceRequest',
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      selectedType: 'NEWS'
    }
  },
  created () {
    this.initForm()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        title: this.resource.name,
        content: this.resource.content,
        type: this.resource.type
      })
      this.rules = reactive({
        title: [{ required: true, message: this.$t('message.error.required.input') }],
        type: [{ required: true, message: this.$t('message.error.required.input') }]
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
          id: this.resource.id,
          title: values.title,
          type: this.selectedType,
          content: values.content
        }
        // console.log('params :>> ', params)
        api('updateBoard', params).then(json => {
          this.$notification.success({
            message: this.$t('label.board.update'),
            description: `${this.$t('message.success.update.board')}`
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
