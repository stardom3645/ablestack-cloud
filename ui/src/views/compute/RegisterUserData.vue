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
  <MoldDialog
    class="userdata-registration-dialog"
    size="md"
    :closable="!loading"
    :keyboard="!loading"
    @cancel="closeAction">
    <template #title>
      {{ $t(isUserData ? 'label.register.user.data' : 'label.register.cni.config') }}
      <a v-if="currentAction.docHelp && $config?.docBase" :href="$config.docBase + '/' + currentAction.docHelp" :aria-label="$t('label.help')" target="_blank" rel="noopener noreferrer"><question-circle-outlined /></a>
    </template>
    <div ref="content" class="userdata-registration-content" :aria-busy="loading">
      <p v-if="loading" role="status">{{ $t('message.userdata.submitting') }}</p>
      <a-alert v-if="error" class="mold-dialog-section" role="alert" type="error" show-icon :message="error" />
    <a-spin :spinning="loading">
      <p v-html="$route.name === 'userdata' ? $t('message.desc.register.user.data') : $t('message.desc.register.cni.config')"></p>
      <a-form
        id="userdata-registration-form"
        @keydown.ctrl.enter.prevent="handleSubmit"
        :ref="formRef"
        :model="form"
        :rules="rules"
        @finish="handleSubmit"
        layout="vertical">
        <a-form-item name="name" ref="name">
          <template #label>
            <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
          </template>
          <a-input
            ref="nameInput"
            v-model:value="form.name"
            :disabled="loading"
            :placeholder="apiParams.name.description"
            v-focus="true" />
        </a-form-item>
        <div v-if="$route.name === 'userdata'">
          <a-form-item name="userdata" ref="userdata">
            <template #label>
              <tooltip-label :title="$t('label.user.data')" :tooltip="$t('label.register.user.data.details')"/>
            </template>
            <a-textarea
              ref="userdataInput"
              v-model:value="form.userdata"
              :disabled="loading"
              :placeholder="$t('label.register.user.data.details')"/>
          </a-form-item>
        </div>
        <div v-else>
          <a-form-item name="cniconfig" ref="cniconfig">
            <template #label>
              <tooltip-label :title="$t('label.cniconfiguration')" :tooltip="apiParams.cniconfig.description"/>
            </template>
            <a-textarea
              ref="cniconfigInput"
              v-model:value="form.cniconfig"
              :disabled="loading"
              :placeholder="apiParams.cniconfig.description"/>
          </a-form-item>
        </div>
        <a-form-item name="isbase64" ref="isbase64" :label="$t('label.is.base64.encoded')">
          <a-checkbox v-model:checked="form.isbase64" :disabled="loading"></a-checkbox>
        </a-form-item>
        <a-form-item name="params" ref="params">
          <template #label>
            <tooltip-label
              :title="$route.name === 'userdata' ? $t('label.userdataparams') : $t('label.cniconfigparams')"
              :tooltip="$route.name === 'userdata' ? $t('message.userdata.validation.help') : apiParams.params.description"/>
          </template>
          <a-select
            ref="paramsInput"
            mode="tags"
            v-model:value="form.params"
            :disabled="loading"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :placeholder="apiParams.params.description">
            <a-select-option v-for="opt in params" :key="opt">
              {{ opt }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="domainid" ref="domainid" v-if="isAdminOrDomainAdmin()">
          <template #label>
            <tooltip-label :title="$t('label.domainid')" :tooltip="apiParams.domainid.description"/>
          </template>
          <a-select
            id="domain-selection"
            v-model:value="form.domainid"
            :disabled="loading"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
              :loading="domainLoading"
            :placeholder="apiParams.domainid.description"
            @change="val => { handleDomainChanged(domains[val]) }">
            <a-select-option
              v-for="(opt, optIndex) in domains"
              :key="optIndex"
              :label="opt.path || opt.name || opt.description || ''">
              {{ opt.path || opt.name || opt.description }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="account" ref="account" v-if="isAdminOrDomainAdmin() && !isObjectEmpty(selectedDomain) && selectedDomain.id !== null">
          <template #label>
            <tooltip-label :title="$t('label.account')" :tooltip="apiParams.account.description"/>
          </template>
          <a-input
            v-model:value="form.account"
            :disabled="loading"
            :placeholder="apiParams.account.description"/>
        </a-form-item>

        <!-- Native Enter submits; textarea and select keep their own Enter behavior. -->
        <button type="submit" hidden aria-hidden="true" tabindex="-1"></button>
      </a-form>
    </a-spin>
    </div>
    <template #footer>
      <a-button :disabled="loading" @click="closeAction">{{ $t('label.cancel') }}</a-button>
      <a-button ref="submit" form="userdata-registration-form" html-type="submit" :loading="loading" :disabled="!allowedApi" type="primary" @keydown.ctrl.enter.prevent="handleSubmit">{{ $t('label.register.userdata.action') }}</a-button>
    </template>
  </MoldDialog>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import MoldDialog from '@/components/view/MoldDialog'
import { canonicalUserData, userDataName, userDataParams } from '@/utils/userDataRegistration'

export default {
  name: 'registerUserData',
  props: { currentAction: { type: Object, default: () => ({}) } },
  emits: ['refresh-data', 'close-action'],
  components: {
    TooltipLabel, MoldDialog
  },
  data () {
    return {
      domains: [],
      domainLoading: false,
      selectedDomain: {},
      loading: false,
      error: '',
      disposed: false,
      maxEncodedLength: 1048576
    }
  },
  computed: {
    isUserData () { return this.$route.name === 'userdata' },
    apiName () { return this.isUserData ? 'registerUserData' : 'registerCniConfiguration' },
    allowedApi () { return this.apiName in this.$store.getters.apis },
    blocksNavigation () { return this.loading }
  },
  beforeCreate () {
    this.apiParams = this.$route.name === 'userdata' ? this.$getApiParams('registerUserData') : this.$getApiParams('registerCniConfiguration')
  },
  created () {
    this.initForm()
    this.domains = [
      {
        id: null,
        name: ''
      }
    ]
    this.fetchData()
  },
  mounted () { window.addEventListener('beforeunload', this.beforeUnload) },
  beforeUnmount () { this.disposed = true; window.removeEventListener('beforeunload', this.beforeUnload) },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        isbase64: false
      })
      this.rules = reactive({
        name: [{ required: true, message: this.$t('message.error.name') }],
        userdata: [{ required: true, message: this.$t('message.error.user.data') }],
        cniconfig: [{ required: true, message: this.$t('message.error.user.data') }]
      })
      if (this.$route.name === 'userdata') {
        const validate = fn => async () => {
          try { fn() } catch (error) { throw new Error(this.$t(error.message)) }
        }
        this.rules.name.push({ validator: validate(() => userDataName(this.form.name)) })
        this.rules.userdata.push({ validator: validate(() => canonicalUserData(this.form.userdata, this.form.isbase64, this.maxEncodedLength)) })
        this.rules.params = [{ validator: validate(() => userDataParams(this.form.params)) }]
      }
    },
    fetchData () {
      if (this.$route.name === 'userdata') {
        getAPI('listCapabilities', {}, { preserveOnFailure: true }).then(json => {
          const max = json.listcapabilitiesresponse?.capability?.userdatamaxlength
          if (!this.disposed && Number.isInteger(max) && max > 0) this.maxEncodedLength = Math.min(max, 1048576)
        }).catch(() => { /* The server still enforces its current configured limit. */ })
      }
      if (this.isAdminOrDomainAdmin()) {
        this.fetchDomainData()
      }
    },
    isAdminOrDomainAdmin () {
      return ['Admin', 'DomainAdmin'].includes(this.$store.getters.userInfo.roletype)
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
    fetchDomainData () {
      const params = {}
      this.domainLoading = true
      getAPI('listDomains', params, { preserveOnFailure: true }).then(json => {
        if (!this.disposed) this.domains = this.domains.concat(json.listdomainsresponse.domain || [])
      }).catch(error => {
        if (!this.disposed) this.error = this.errorMessage(error)
      }).finally(() => {
        if (this.disposed) return
        this.domainLoading = false
        if (this.arrayHasItems(this.domains)) {
          this.form.domainid = 0
          this.handleDomainChanged(this.domains[0])
        }
      })
    },
    handleDomainChanged (domain) {
      this.selectedDomain = domain
    },
    formPosition () {
      const body = this.$refs.content?.closest('.ant-modal-body')
      return { focus: document.activeElement, body, top: body?.scrollTop || 0 }
    },
    restoreFormPosition (position) {
      this.$nextTick(() => {
        if (this.disposed) return
        if (position.focus?.isConnected) position.focus.focus({ preventScroll: true })
        if (position.body) position.body.scrollTop = position.top
      })
    },
    errorMessage (error) {
      const response = Object.values(error?.response?.data || {}).find(value => value?.errortext)
      return response?.errortext || error?.message || this.$t('message.network.error')
    },
    handleSubmit (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault()
      if (this.loading || this.disposed) return
      if (!this.allowedApi) { this.error = this.$t('label.forbidden'); return }
      const position = this.formPosition()
      let invalidField
      this.loading = true
      this.error = ''
      return this.formRef.value.validate().then(() => {
        if (this.disposed || !this.allowedApi) return
        const values = toRaw(this.form)
        const params = {
          name: values.name
        }
        if (this.isValidValueForKey(values, 'domainid') &&
          this.arrayHasItems(this.domains) &&
          this.domains[values.domainid].id !== null) {
          params.domainid = this.domains[values.domainid].id
        }
        if (this.isValidValueForKey(values, 'account') && values.account.length > 0) {
          params.account = values.account
        }

        let apiName = 'registerUserData'
        if (this.$route.name === 'cniconfiguration') {
          apiName = 'registerCniConfiguration'
        }

        if (apiName === 'registerUserData') {
          params.name = userDataName(values.name)
          params.userdata = canonicalUserData(values.userdata, values.isbase64, this.maxEncodedLength)
        } else {
          params.cniconfig = values.isbase64 ? values.cniconfig : this.$toBase64AndURIEncoded(values.cniconfig)
        }
        if (values.params != null && values.params.length > 0) {
          const userdataparams = apiName === 'registerUserData' ? userDataParams(values.params) : values.params.join(',')
          params.params = userdataparams
        }

        return postAPI(apiName, params, { preserveOnFailure: true }).then(json => {
          if (this.disposed) return
          this.$message.success(this.$t('message.success.register.user.data') + ' ' + values.name)
          this.$emit('refresh-data')
          this.$emit('close-action')
        }).catch(error => {
          if (!this.disposed) this.error = this.errorMessage(error)
        })
      }).catch(error => {
        if (error?.errorFields?.length) invalidField = error.errorFields[0].name
        else if (!this.disposed) this.error = this.$t(error.message || 'message.network.error')
      }).finally(() => {
        this.loading = false
        if (this.disposed) return
        if (invalidField) this.$nextTick(() => {
          this.formRef.value.scrollToField(invalidField)
          this.$refs[invalidField[0] + 'Input']?.focus()
        })
        else this.restoreFormPosition(position)
      })
    },
    beforeUnload (event) { if (this.loading) { event.preventDefault(); event.returnValue = '' } },
    closeAction () {
      if (this.loading) return
      this.$emit('close-action')
    }
  }
}
</script>

<style scoped lang="less">
  .userdata-registration-content { min-width: 0; overflow-wrap: anywhere; }
</style>
