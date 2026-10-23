<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License. You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<template>
  <a-collapse v-if="entries.length" class="snapshot-operations" data-testid="storage-snapshot-operations" :default-active-key="['operations']">
    <a-collapse-panel key="operations" :header="$t('label.snapshot.action.operations')">
      <article v-for="entry in entries" :key="entry.id" class="snapshot-operation">
        <strong>{{ entry.api }} · {{ entry.target.name || entry.target.id }}</strong>
        <p>{{ entry.target.id }} · {{ $t('label.snapshot.action.status.' + entry.status) }} · {{ entry.created }}</p>
        <router-link v-if="resultPath(entry)" :to="resultPath(entry)">{{ $t('label.details') }}</router-link>
        <p v-if="entry.requestid && !entry.jobid">{{ $t('label.snapshot.action.request.id') }}: {{ entry.requestid }}</p>
        <p v-if="entry.jobid">{{ $t('label.snapshot.recovery.jobid') }}: {{ entry.jobid }}</p>
        <a-alert v-if="entry.error" :type="entry.status === 'unknown' ? 'warning' : 'error'" show-icon :message="$t(entry.error)" />
        <a-button v-if="entry.jobid || (entry.requestid && entry.status === 'unknown')" :loading="checking[entry.id]" @click="check(entry)">{{ $t('label.snapshot.recovery.check') }}</a-button>
        <router-link v-if="entry.status === 'unknown'" :to="{path: '/event', query: {resourceid: entry.target.id}}">{{ $t('label.snapshot.action.history') }}</router-link>
        <div v-if="entry.status === 'unknown' && !entry.jobid">
          <a-input v-model:value="jobIds[entry.id]" :placeholder="$t('label.snapshot.action.job.id')" style="max-width: 390px" />
          <a-checkbox v-model:checked="jobConfirmed[entry.id]">{{ $t('message.snapshot.action.job.acknowledge') }}</a-checkbox>
          <a-button :disabled="!jobConfirmed[entry.id] || !jobIds[entry.id]" :loading="checking[entry.id]" @click="attach(entry)">{{ $t('label.snapshot.action.job.attach') }}</a-button>
          <a-alert v-if="errors[entry.id]" type="error" :message="errors[entry.id]" />
          <a-checkbox v-model:checked="confirmed[entry.id]">{{ $t('message.snapshot.action.release.acknowledge') }}</a-checkbox>
          <a-button :disabled="!confirmed[entry.id]" @click="release(entry)">{{ $t('label.snapshot.action.release') }}</a-button>
        </div>
      </article>
    </a-collapse-panel>
  </a-collapse>
</template>
<script>
import { storageSnapshotOperations, operationScopeKey, querySnapshotOperation, releaseUnknownSnapshotSubmission, attachSnapshotOperationJob } from '@/utils/storageSnapshotOperations'
import { snapshotSecurityScope } from '@/utils/storageSnapshotContext'
export default {
  name: 'StorageSnapshotOperations',
  emits: ['refresh-data'],
  data () { return { checking: {}, confirmed: {}, jobIds: {}, jobConfirmed: {}, errors: {} } },
  computed: {
    scope () { return snapshotSecurityScope(this.$store) },
    entries () { const scope = operationScopeKey(this.scope); const all = Object.values(storageSnapshotOperations).filter(e => e.scope === scope).reverse(); return [...all.filter(e => ['checking', 'submitting', 'accepted', 'unknown'].includes(e.status)), ...all.filter(e => !['checking', 'submitting', 'accepted', 'unknown'].includes(e.status)).slice(0, 10)] },
    terminal () { return this.entries.filter(e => ['complete', 'failed'].includes(e.status)).map(e => e.id + e.status).join('|') }
  },
  watch: { terminal () { this.$emit('refresh-data') } },
  methods: {
    resultPath (entry) {
      const result = entry.result?.jobresult
      const body = result?.[entry.api.toLowerCase() + 'response'] || result
      const volume = body?.volume; const template = body?.template?.[0] || body?.template; const snapshot = body?.snapshot?.[0] || body?.snapshot
      if (volume?.id) return '/volume/' + volume.id
      if (template?.id) return '/template/' + template.id
      if (snapshot?.id) return '/snapshot/' + snapshot.id
      return ''
    },
    async check (entry) {
      if (this.checking[entry.id]) return
      const scope = this.scope; this.checking[entry.id] = true
      try { await querySnapshotOperation(entry, () => this.scope === scope) } finally { this.checking[entry.id] = false }
    },
    async attach (entry) {
      if (this.checking[entry.id]) return
      const scope = this.scope; this.checking[entry.id] = true; this.errors[entry.id] = ''
      try { await attachSnapshotOperationJob(entry, this.jobIds[entry.id], this.jobConfirmed[entry.id], () => this.scope === scope) } catch (error) { this.errors[entry.id] = this.$t(error.message) } finally { this.checking[entry.id] = false }
    },
    release (entry) { releaseUnknownSnapshotSubmission(entry, this.confirmed[entry.id]); this.confirmed[entry.id] = false }
  }
}
</script>
<style scoped>
.snapshot-operations { margin: 12px 0; }
.snapshot-operation { border-bottom: 1px solid var(--border-color-split, #777); padding: 12px 0; overflow-wrap: anywhere; }
.snapshot-operation .ant-btn, .snapshot-operation a { margin-right: 12px; margin-top: 8px; }
.snapshot-operation p { margin: 4px 0; }
</style>
