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
  <a-card class="autoscale-operational-summary" size="small" :title="$t('label.autoscale.operational.summary')">
    <p>{{ $t('message.autoscale.count.meaning') }}</p>
    <dl v-if="summary" class="state-summary">
      <div v-for="(count, name) in summary" :key="name"><dt>{{ $t('label.autoscale.count.' + name) }}</dt><dd>{{ count }}</dd></div>
    </dl>
    <p v-else role="status">{{ $t('message.autoscale.count.unavailable') }}</p>
    <dl class="state-times">
      <div><dt>{{ $t('label.autoscale.status.collected') }}</dt><dd>{{ time(resource.statuscollectedat) }}</dd></div>
      <div><dt>{{ $t('label.autoscale.last.checked') }}</dt><dd>{{ time(resource.lastcheckedat) }}</dd></div>
      <div><dt>{{ $t('label.autoscale.last.scaled') }}</dt><dd>{{ time(resource.lastscaledat) }}</dd></div>
      <div v-if="resource.operationdeadline"><dt>{{ $t('label.autoscale.drain.deadline') }}</dt><dd>{{ time(resource.operationdeadline) }}</dd></div>
    </dl>
    <a-alert v-if="resource.state === 'DISABLED'" type="info" show-icon :message="$t('message.autoscale.operational.disabled')" />
    <a-alert
v-if="resource.operationinprogress"
:type="retry ? 'warning' : 'info'"
show-icon
role="status"
      :message="$t('label.autoscale.operation.' + (resource.scalingaction || 'RECOVERY')) + ' · ' + $t('label.autoscale.phase.' + (resource.scalingphase || 'RECOVERY'))"
      :description="$t(retry ? 'message.autoscale.operational.retry' : 'message.autoscale.operation.wait')" />
    <p v-if="summary?.cleanup || summary?.error" role="status">{{ $t('message.autoscale.operational.inspect') }}</p>
  </a-card>
</template>
<script>
import { autoScaleStateSummary, autoScaleTime } from '@/utils/autoScaleOperations'
export default {
  name: 'AutoScaleOperationalSummary',
  props: { resource: { type: Object, required: true } },
  computed: {
    summary () { return autoScaleStateSummary(this.resource.memberstatecounts) },
    retry () { return (this.resource.scalingphase || '').endsWith('_RETRY') }
  },
  methods: { time (value) { return autoScaleTime(value, this.$toLocaleDate, this.$t) } }
}
</script>
<style scoped lang="scss">
.autoscale-operational-summary {
  margin-bottom: 16px; color: var(--ui-text-primary); background: var(--ui-bg-surface); overflow-wrap: anywhere;
  dl { display: flex; gap: 16px; flex-wrap: wrap; margin: 12px 0; }
  dt { font-weight: 600; } dd { margin: 0; }
  .state-summary > div { min-width: 90px; }
  .state-summary dd { font-size: 20px; }
  .ant-alert { margin-bottom: 8px; }
}
</style>
