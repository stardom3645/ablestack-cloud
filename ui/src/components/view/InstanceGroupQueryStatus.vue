<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->
<template>
  <a-alert class="vm-group-query-status" :type="['error', 'forbidden'].includes(status) ? 'error' : 'info'" show-icon>
    <template #message><span role="status" aria-live="polite">{{ $t(messageKey) }}</span></template>
    <template #description>
      <span v-if="updated">{{ $t('message.vm.group.query.updated', { time: new Date(updated).toLocaleString($i18n.locale?.replace('_', '-')) }) }}</span>
      <span v-if="sorted"> {{ $t('message.vm.group.query.page.sort') }}</span>
    </template>
    <template #action><a-button v-if="['error', 'forbidden', 'missing'].includes(status)" size="small" @click="$emit('retry')">{{ $t('label.retry') }}</a-button></template>
  </a-alert>
</template>
<script>
import { vmGroupQueryMessage } from '@/utils/vmGroupQuery'
export default {
  props: {
    status: { type: String, default: 'loading' },
    updated: { type: Number, default: null },
    retained: { type: Boolean, default: false },
    sorted: { type: Boolean, default: false }
  },
  emits: ['retry'],
  computed: { messageKey () { return vmGroupQueryMessage(this.status, this.retained) } }
}
</script>
<style scoped>
.vm-group-query-status { margin-bottom: 12px; }
</style>
