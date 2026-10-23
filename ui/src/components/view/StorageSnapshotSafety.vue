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
  <section class="snapshot-safety-summary" data-testid="snapshot-safety-summary" aria-live="polite">
    <a-spin :spinning="loading">
      <a-alert v-if="error || context?.reason" type="error" show-icon :message="error || $t('message.snapshot.action.reason.' + context.reason)" />
      <a-descriptions v-if="context" bordered size="small" :column="1">
        <a-descriptions-item :label="$t('label.name')">{{ context.record.name }} · {{ context.record.id }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.state')">{{ context.record.state }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.volume')">{{ context.source.volumename || context.record.volumename || $t('message.snapshot.action.source.absent') }} · {{ context.record.volumeid || context.source.volumeid }} · {{ context.source.volumestate || $t('message.snapshot.action.source.absent') }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.virtualmachine')">{{ context.source.virtualmachinename || $t('label.none') }} {{ context.source.virtualmachineid }} {{ context.source.virtualmachinestate }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.account')">{{ context.record.account }} · {{ context.record.project || context.record.projectid || context.record.domain }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.zone')">{{ context.target.zoneId === 'all' ? $t('message.snapshot.action.all.zones') : context.target.zoneId || $t('message.snapshot.action.reason.UNKNOWN_REFERENCE_ZONE') }}</a-descriptions-item>
      </a-descriptions>
      <p v-if="context && !context.source.sourceavailable">{{ $t('message.snapshot.action.source.recovery') }}</p>
      <a-button size="small" :disabled="loading" @click="$emit('refresh')">{{ $t('label.snapshot.action.refresh') }}</a-button>
    </a-spin>
  </section>
</template>
<script>
export default { name: 'StorageSnapshotSafety', emits: ['refresh'], props: { context: Object, loading: Boolean, error: String } }
</script>
<style scoped>
.snapshot-safety-summary { margin-bottom: 16px; }
.snapshot-safety-summary .ant-btn { margin-top: 8px; }
</style>
