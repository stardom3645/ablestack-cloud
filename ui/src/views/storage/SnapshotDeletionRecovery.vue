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
  <div class="snapshot-deletion-recovery">
    <a-alert type="warning" :message="$t('message.snapshot.recovery.scope')" show-icon />
    <a-form layout="vertical" style="margin-top: 16px">
      <a-form-item :label="$t('label.snapshot.id')">
        <a-input v-model:value="snapshotId" :disabled="busy || !!pendingJob" data-testid="recovery-snapshot-id" />
      </a-form-item>
      <a-form-item :label="$t('label.snapshot.recovery.zone')">
        <a-input v-model:value="zoneId" :disabled="busy || !!pendingJob" data-testid="recovery-zone-id" />
      </a-form-item>
      <a-button :loading="busy" :disabled="busy || !!pendingJob" @click="diagnose">{{ $t('label.snapshot.recovery.diagnose') }}</a-button>
      <a-alert v-if="error" type="error" :message="error" show-icon style="margin-top: 16px" />
      <a-alert v-if="completed" type="success" :message="$t('message.snapshot.recovery.complete')" style="margin-top: 16px" />
      <div v-if="preview" class="recovery-preview" style="margin-top: 16px">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item :label="$t('label.name')">{{ preview.name }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.snapshot.id')">{{ preview.snapshotid }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.state')">{{ preview.state }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.zone')">{{ preview.zoneid || $t('label.snapshot.recovery.allzones') }}</a-descriptions-item>
        </a-descriptions>
        <ul data-testid="recovery-references">
          <li v-for="ref in (preview.reference || [])" :key="ref.referenceid">
            #{{ ref.referenceid }} · {{ ref.storename }} · {{ ref.role }} · {{ ref.state }} · {{ ref.zoneid }}
          </li>
        </ul>
        <p v-if="!(preview.reference || []).length">{{ $t('message.snapshot.recovery.no.references') }}</p>
        <a-checkbox v-model:checked="acknowledged" :disabled="busy || !!pendingJob || completed">
          {{ $t('message.snapshot.recovery.acknowledge') }}
        </a-checkbox>
      </div>
      <p v-if="pendingJob">{{ $t('label.job.id') }}: {{ pendingJob }}</p>
      <a-button v-if="pendingJob && !busy" @click="checkResult">{{ $t('label.snapshot.recovery.check.result') }}</a-button>
      <MoldDialogActions :busy="busy">
        <a-button :disabled="busy" @click="$emit('close-action')">{{ $t('label.close') }}</a-button>
        <a-button type="primary" danger :loading="busy" :disabled="!canRecover" @click="recover">
          {{ $t('label.snapshot.recovery.execute') }}
        </a-button>
      </MoldDialogActions>
    </a-form>
  </div>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import storageDialogContext from '@/utils/storageDialogContext'
import MoldDialogActions from '@/components/view/MoldDialogActions'

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export default {
  name: 'SnapshotDeletionRecovery',
  components: { MoldDialogActions },
  mixins: [storageDialogContext],
  emits: ['close-action', 'refresh-data'],
  props: { resource: { type: Object, default: () => ({}) } },
  data () {
    return { snapshotId: '', zoneId: '', preview: null, previewScope: null, acknowledged: false, busy: false, error: '', completed: false, pendingJob: '', blockedSubmission: false, sequence: 0 }
  },
  computed: {
    canRecover () { return !!this.preview && this.acknowledged && !this.busy && !this.pendingJob && !this.blockedSubmission && !this.completed && this.dialogCurrent() }
  },
  watch: {
    snapshotId () { this.invalidate() },
    zoneId () { this.invalidate() }
  },
  methods: {
    invalidate () { this.sequence++; this.preview = null; this.previewScope = null; this.acknowledged = false; this.completed = false },
    async waitForJob (jobId, sequence) {
      for (let i = 0; i < 120; i++) {
        if (!this.dialogCurrent() || sequence !== this.sequence) throw new Error(this.$t('message.snapshot.recovery.context.changed'))
        const json = await getAPI('queryAsyncJobResult', { jobid: jobId }, { preserveOnFailure: true, timeout: 20000 })
        const job = json.queryasyncjobresultresponse
        if (job.jobstatus === 1) return job.jobresult?.deletesnapshotresponse || job.jobresult
        if (job.jobstatus === 2) {
          const error = new Error(job.jobresult?.errortext || this.$t('message.snapshot.recovery.failed'))
          error.terminal = true
          throw error
        }
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      throw new Error(this.$t('message.snapshot.recovery.unknown'))
    },
    showError (error) { this.error = error?.response?.data?.errorresponse?.errortext || error.message || this.$t('message.snapshot.recovery.failed') },
    async diagnose () {
      if (this.busy || this.pendingJob || !this.dialogCurrent()) return
      this.invalidate()
      this.error = ''
      const scope = { id: this.snapshotId.trim().toLowerCase() }
      if (this.zoneId.trim()) scope.zoneid = this.zoneId.trim().toLowerCase()
      if (!uuid.test(scope.id) || (scope.zoneid && !uuid.test(scope.zoneid))) {
        this.error = this.$t('message.snapshot.recovery.invalid.uuid'); return
      }
      const sequence = this.sequence
      this.busy = true
      try {
        const json = await postAPI('deleteSnapshot', { ...scope, dryrun: true })
        const response = json.deletesnapshotresponse
        const result = response.jobid ? await this.waitForJob(response.jobid, sequence) : response
        if (!this.dialogCurrent() || sequence !== this.sequence) return
        if (result.snapshotid !== scope.id || !result.scopehash) throw new Error(this.$t('message.snapshot.recovery.failed'))
        this.preview = result
        this.previewScope = scope
      } catch (error) { if (this.dialogCurrent() && sequence === this.sequence) this.showError(error) } finally { this.busy = false }
    },
    async recover () {
      if (!this.canRecover || !this.dialogCurrent()) return
      this.busy = true
      this.error = ''
      try {
        this.blockedSubmission = true
        const json = await postAPI('deleteSnapshot', { ...this.previewScope, expectedscopehash: this.preview.scopehash })
        const response = json.deletesnapshotresponse
        if (!response.jobid) throw new Error(this.$t('message.snapshot.recovery.unknown'))
        this.pendingJob = response.jobid
        this.blockedSubmission = false
        await this.finishJob()
      } catch (error) {
        if (error?.response?.status >= 400 && error?.response?.status < 500) {
          this.blockedSubmission = false
          this.preview = null
          this.acknowledged = false
        }
        this.showError(error)
      } finally { this.busy = false }
    },
    async finishJob () {
      try {
        await this.waitForJob(this.pendingJob, this.sequence)
        if (!this.dialogCurrent()) return
        this.pendingJob = ''
        this.completed = true
        this.acknowledged = false
        this.$emit('refresh-data')
      } catch (error) {
        if (error.terminal) {
          this.pendingJob = ''
          this.preview = null
          this.acknowledged = false
        }
        // Unknown results retain the accepted job ID and cannot resubmit deletion.
        throw error
      }
    },
    async checkResult () {
      if (this.busy || !this.pendingJob) return
      this.busy = true
      this.error = ''
      try { await this.finishJob() } catch (error) { this.showError(error) } finally { this.busy = false }
    }
  }
}
</script>

<style lang="less" scoped>
.snapshot-deletion-recovery { width: 85vw; max-width: 680px; }
.recovery-preview { overflow-wrap: anywhere; }
</style>
