// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
  <section class="userdata-content" :aria-label="$t('label.userdata.content')" :aria-busy="pending">
    <div class="content-toolbar">
      <a-button :disabled="!exportable" @click="copy"><template #icon><copy-outlined /></template>{{ $t('label.copy.clipboard') }}</a-button>
      <a-button :disabled="!exportable" @click="download"><template #icon><download-outlined /></template>{{ $t('label.download') }}</a-button>
      <a-radio-group v-model:value="mode" :aria-label="$t('label.userdata.representation')" @change="feedback = ''">
        <a-radio-button value="raw" :disabled="content?.text === null">{{ $t('label.userdata.raw') }}</a-radio-button>
        <a-radio-button value="base64">Base64</a-radio-button>
      </a-radio-group>
      <a-tooltip :title="$t('label.refresh')"><a-button class="content-refresh" :aria-label="$t('label.refresh')" :loading="pending" :disabled="!canRead" @click="fetchData"><template #icon><reload-outlined /></template></a-button></a-tooltip>
    </div>
    <p class="content-note">{{ $t('message.userdata.content.contract') }}</p>
    <p v-if="content" class="content-note">{{ $t('message.userdata.content.representation', { mode: mode === 'raw' ? $t('label.userdata.raw') : 'Base64', size: content.bytes.length }) }}</p>
    <a-alert v-if="['error', 'forbidden', 'missing', 'mismatch', 'invalid', 'unsupported'].includes(status)" type="error" show-icon :message="$t('message.userdata.content.' + status)" role="status">
      <template #description><span v-if="content">{{ $t('message.userdata.content.stale') }}</span><a-button v-if="canRead" type="link" :loading="pending" @click="fetchData">{{ $t('label.retry') }}</a-button></template>
    </a-alert>
    <a-alert v-if="content?.text === null" type="warning" show-icon :message="$t('message.userdata.content.binary')" />
    <a-alert v-if="feedback" :type="feedbackError ? 'error' : 'success'" show-icon :message="$t(feedback)" role="status" />
    <a-spin :spinning="pending && !content">
      <a-empty v-if="content?.empty" :description="$t('message.userdata.content.empty')" />
      <pre v-else-if="content" class="content-code" tabindex="0" :aria-label="mode === 'raw' ? $t('label.userdata.raw') : 'Base64'"><code>{{ display }}</code></pre>
    </a-spin>
  </section>
</template>
<script>
import { getAPI } from '@/api'
import { userDataContent } from '@/utils/userDataContent'
import { userDataDeleteLookup, userDataDeleteIdentity, userDataDeleteError } from '@/utils/userDataDeletion'

export default {
  name: 'UserDataContent',
  props: { resource: { type: Object, required: true }, tab: { type: String, default: '' }, loading: Boolean },
  data () { return { version: 0, disposed: false, pending: false, status: 'loading', content: null, mode: 'raw', feedback: '', feedbackError: false } },
  computed: {
    active () { return this.tab === 'userdata.content' },
    canRead () { return !!this.$store.getters.apis?.listUserData },
    scope () { return JSON.stringify([this.resource.id, this.resource.name, this.resource.account, this.resource.domainid, this.resource.projectid, this.$route.path, this.$store.getters.project?.id, this.$store.getters.listAllProjects, this.$store.getters.userInfo?.id, this.$store.state?.user?.token, this.canRead]) },
    display () { return this.mode === 'base64' ? this.content?.encoded : this.content?.text },
    exportable () { return this.status === 'ready' && !this.pending && this.content && !this.content.empty }
  },
  watch: {
    scope () { this.reset(); this.fetchData() },
    active (value) { this.version++; this.pending = false; this.feedback = ''; if (value) this.fetchData() },
    loading (value) { if (!value) this.fetchData() }
  },
  created () { this.fetchData() },
  beforeUnmount () { this.disposed = true; this.reset() },
  methods: {
    reset () { this.version++; this.pending = false; this.content = null; this.status = 'loading'; this.mode = 'raw'; this.feedback = '' },
    async fetchData () {
      if (!this.active || this.disposed) return
      if (!this.canRead) { this.reset(); this.status = 'unsupported'; return }
      const source = Object.freeze({ ...this.resource }); const lookup = userDataDeleteLookup(source)
      if (!lookup) { this.reset(); this.status = 'mismatch'; return }
      const version = ++this.version; const scope = this.scope
      const current = () => !this.disposed && this.active && version === this.version && scope === this.scope
      this.pending = true; this.feedback = ''
      try {
        const json = await getAPI('listUserData', lookup, { preserveOnFailure: true, timeout: 20000 })
        if (!current()) return
        const identity = userDataDeleteIdentity(json, source)
        if (identity !== 'ready') throw Object.assign(new Error(identity), { contentState: identity })
        let content
        try { content = userDataContent(json.listuserdataresponse.userdata[0].userdata) } catch (error) { throw Object.assign(error, { contentState: 'invalid' }) }
        this.content = content; this.status = 'ready'
        if (content.text === null) this.mode = 'base64'
      } catch (error) {
        if (!current()) return
        const code = userDataDeleteError(error).code
        this.status = error.contentState || ([401, 403, 432, 531].includes(code) ? 'forbidden' : code === 431 ? 'missing' : 'error')
        if (this.status !== 'error') this.content = null
      } finally { if (current()) this.pending = false }
    },
    async copy () {
      if (!this.exportable) return
      const text = this.display
      const scope = this.scope
      const version = this.version
      const notify = (error = false) => {
        if (this.disposed || !this.active || scope !== this.scope || version !== this.version) return
        this.feedbackError = error
        this.feedback = error ? 'message.userdata.content.copy.failed' : 'message.success.copy.clipboard'
      }
      if (navigator.clipboard?.writeText) {
        try { await navigator.clipboard.writeText(text); notify(); return } catch (error) { /* HTTP/older browsers use the selected-text fallback below. */ }
      }
      if (this.disposed || !this.active || scope !== this.scope || version !== this.version) return
      // Use the browser's copy event for exact text (including CRLF/BOM).
      // A textarea's value/selection would normalize line endings on HTTP sites.
      const focus = document.activeElement
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.readOnly = true
      textarea.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0'
      const listener = event => { event.clipboardData.setData('text/plain', text); event.preventDefault() }
      document.addEventListener('copy', listener)
      try {
        document.body.appendChild(textarea)
        textarea.select()
        if (!document.execCommand('copy')) throw new Error('Copy unavailable')
        notify()
      } catch (error) { notify(true) } finally {
        document.removeEventListener('copy', listener)
        textarea.remove()
        if (focus?.isConnected) focus.focus({ preventScroll: true })
      }
    },
    download () {
      if (!this.exportable) return
      let url
      try {
        const raw = this.mode === 'raw'
        url = URL.createObjectURL(new Blob([raw ? this.content.bytes : this.content.encoded], { type: raw ? 'application/octet-stream' : 'text/plain;charset=us-ascii' }))
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = 'userdata-' + this.resource.id + (raw ? '.txt' : '.base64.txt')
        anchor.click()
        this.feedbackError = false; this.feedback = 'message.userdata.content.download.started'
      } catch (error) { this.feedbackError = true; this.feedback = 'message.userdata.content.download.failed' } finally { if (url) setTimeout(() => URL.revokeObjectURL(url), 1000) }
    }
  }
}
</script>
<style lang="less" scoped>
.userdata-content { min-width: 0; color: var(--ui-text-primary); }
.content-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 20px;
  .ant-btn { height: 32px; padding: 4px 15px; }
}
.content-refresh { margin-left: auto; }
.content-note { color: var(--ui-text-secondary); margin-bottom: 8px; overflow-wrap: anywhere; }
.ant-alert { margin-bottom: 12px; }
.content-code { margin: 20px 0 0; padding: 12px; max-width: 100%; max-height: 60vh; overflow: auto; white-space: pre; tab-size: 4; font-family: monospace; color: var(--ui-text-primary); background: var(--ui-bg-input); border: 1px solid var(--ui-border); border-radius: 2px; }
.content-code:focus-visible { outline: 2px solid var(--ui-focus); outline-offset: 2px; }
</style>
