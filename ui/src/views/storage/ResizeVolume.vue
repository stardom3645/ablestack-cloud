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
    <a-alert v-if="!current()" type="error" show-icon :message="$t('message.volume.resize.context')" style="margin-bottom: 16px" />
    <a-alert v-if="errorText" type="error" show-icon :message="errorText" style="margin-bottom: 16px" />
    <a-button v-if="errorText && !contract && !jobId" :loading="loading" style="margin-bottom: 16px" @click="fetchData">{{ $t('label.retry') }}</a-button>
    <a-form :ref="formRef" :model="form" layout="vertical" @finish="handleSubmit">
      <AllocationSummary :resource="target" volume />
      <p v-if="contract">{{ $t(contract.kind === 'compute' ? 'label.compute.offerings' : 'label.diskoffering') }}: {{ contract.offering.name }}</p>
      <a-alert v-if="sizeReason" type="info" show-icon :message="$t(sizeReason)" style="margin-bottom: 16px" />
      <a-alert v-else-if="rootFixed" type="info" show-icon :message="$t('message.volume.resize.root')" style="margin-bottom: 16px" />
      <a-form-item v-if="apiParams.size" name="size" :label="$t('label.sizegb')">
        <a-input v-model:value="form.size" :disabled="disabled || !!sizeReason" :placeholder="$t('label.disksize')" />
      </a-form-item>
      <template v-if="customIops">
        <a-form-item v-if="apiParams.miniops" name="miniops" :label="$t('label.miniops')">
          <a-input v-model:value="form.miniops" :disabled="disabled" :placeholder="$t('label.miniops')" />
        </a-form-item>
        <a-form-item v-if="apiParams.maxiops" name="maxiops" :label="$t('label.maxiops')">
          <a-input v-model:value="form.maxiops" :disabled="disabled" :placeholder="$t('label.maxiops')" />
        </a-form-item>
        <p class="form-help">{{ $t('message.volume.resize.omitted') }}</p>
        <p v-if="target.type === 'ROOT'" class="form-help">{{ $t('message.volume.resize.root.iops') }}</p>
      </template>
      <p v-else-if="contract">{{ $t('message.volume.resize.fixed.iops') }}: {{ target.miniops ?? '—' }} / {{ target.maxiops ?? '—' }}</p>
      <a-form-item v-if="apiParams.shrinkok && !sizeReason && target.hypervisor !== 'XenServer'" name="shrinkok" :label="$t('label.shrinkok')">
        <a-switch v-model:checked="form.shrinkok" :disabled="disabled" />
      </a-form-item>
      <a-form-item v-if="apiParams.automigrate && !sizeReason" name="automigrate" :label="$t('label.automigrate.volume')">
        <a-switch v-model:checked="form.automigrate" :disabled="disabled" />
      </a-form-item>
      <div class="action-button">
        <a-button :disabled="loading" @click="closeModal">{{ $t('label.cancel') }}</a-button>
        <a-button v-if="jobId" :loading="loading" type="primary" @click="resumeJob">{{ $t('message.volume.edit.check.result') }}</a-button>
        <a-button v-else :loading="loading" :disabled="!contract || !current() || submissionUnknown" type="primary" ref="submit" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-form>
  </div>
</template>
<script>
import { ref, reactive } from 'vue'
import { getAPI, postAPI } from '@/api'
import AllocationSummary from '@/components/view/AllocationSummary'
import { readVolumeResizeContract, volumeResizeSizeReason, volumeResizeIops, volumeResizeParams, validateVolumeResizeSnapshot } from '@/utils/volumeResize'

export default {
  name: 'ResizeVolume',
  components: { AllocationSummary },
  props: { resource: { type: Object, required: true } },
  emits: ['refresh-data', 'close-action'],
  data () { return { loading: false, contract: null, errorText: '', jobId: null, submissionUnknown: false, active: true } },
  beforeCreate () { this.apiParams = this.$getApiParams('resizeVolume') || {} },
  created () {
    this.target = Object.freeze({ ...this.resource })
    this.originalScope = this.scope()
    this.formRef = ref()
    this.form = reactive({ size: String(this.target.size / 1073741824), miniops: this.target.miniops == null ? '' : String(this.target.miniops), maxiops: this.target.maxiops == null ? '' : String(this.target.maxiops), shrinkok: false, automigrate: false })
    this.fetchData()
  },
  beforeUnmount () { this.active = false },
  computed: {
    sizeReason () { return volumeResizeSizeReason(this.target, this.contract) },
    rootFixed () { return this.contract?.kind === 'compute' && Number(this.contract.offering.rootdisksize) > 0 },
    customIops () { return volumeResizeIops(this.target, this.contract) },
    disabled () { return this.loading || !!this.jobId || !this.contract || !this.current() }
  },
  methods: {
    scope () {
      const s = this.$store.getters
      return JSON.stringify([this.resource.id, s.userInfo?.id, s.userInfo?.roletype, s.project?.id, this.$route.path])
    },
    current () { return this.active && this.originalScope === this.scope() },
    failure (error) {
      const body = error?.response?.data
      const text = body?.errorresponse?.errortext || Object.values(body || {}).find(value => value?.errortext)?.errortext || error?.errortext || error?.message
      this.errorText = text?.startsWith('message.') ? this.$t(text) : text || this.$t('message.request.failed')
    },
    async fetchData () {
      if (this.loading) return
      this.loading = true
      this.errorText = ''
      this.contract = null
      try {
        if (!this.current()) throw new Error('message.volume.resize.context')
        this.contract = await readVolumeResizeContract(getAPI, this.target, () => this.current())
      } catch (error) { if (this.active) this.failure(error) } finally { this.loading = false }
    },
    async handleSubmit () {
      if (this.loading || this.jobId || !this.contract || this.submissionUnknown) return
      this.loading = true
      this.errorText = ''
      try {
        if (!this.current()) throw new Error('message.volume.resize.context')
        const params = volumeResizeParams(this.target, { ...this.form }, this.contract, this.apiParams)
        if (Object.keys(params).length === 1) { this.closeModal(); return }
        const response = await getAPI('listVolumes', { id: this.target.id, listall: true, ...(this.target.projectid ? { projectid: this.target.projectid } : {}) }, { preserveOnFailure: true })
        if (!this.current()) throw new Error('message.volume.resize.context')
        const rows = response?.listvolumesresponse?.volume
        const fresh = Array.isArray(rows) && rows.length === 1 ? rows[0] : null
        if (!fresh || fresh.id !== this.target.id) throw new Error('message.volume.resize.changed')
        const contract = await readVolumeResizeContract(getAPI, fresh, () => this.current())
        validateVolumeResizeSnapshot(this.target, fresh, this.contract, contract)
        this.submissionUnknown = true
        const result = (await postAPI('resizeVolume', params, { preserveOnFailure: true }))?.resizevolumeresponse
        if (!result?.jobid) throw new Error('message.volume.resize.unknown')
        if (!this.current()) return
        this.submissionUnknown = false
        this.jobId = result.jobid
        await this.poll(false)
      } catch (error) {
        if (error?.response?.status >= 400 && error?.response?.data) this.submissionUnknown = false
        if (this.active) {
          if (this.submissionUnknown) this.failure(new Error('message.volume.resize.unknown'))
          else this.failure(error)
        }
      } finally { this.loading = false }
    },
    async poll (retry) {
      const result = await this.$pollJob({
        jobId: this.jobId,
        batchKey: this.jobId,
        resourceId: this.target.id,
        retry,
        title: this.$t('label.action.resize.volume'),
        description: this.target.name,
        successMessage: this.$t('message.success.resize.volume'),
        errorMessage: this.$t('message.resize.volume.failed')
      })
      if (!this.current()) return
      if (result.jobstatus === 1) { this.jobId = null; this.$emit('refresh-data'); this.$emit('close-action') } else if (result.jobstatus === 2) { this.jobId = null; this.failure(result.jobresult) } else this.errorText = this.$t('message.volume.resize.unknown')
    },
    async resumeJob () {
      if (this.loading || !this.jobId) return
      if (!this.current()) { this.failure(new Error('message.volume.resize.context')); return }
      this.loading = true
      try { await this.poll(true) } catch (error) { if (this.active) this.failure(error) } finally { this.loading = false }
    },
    closeModal () { this.$emit('close-action') }
  }
}
</script>
<style lang="scss" scoped>
.form-layout {
  width: 85vw;
  @media (min-width: 760px) { width: 500px; }
}
.form-help { margin: 8px 0; opacity: .8; }
</style>
