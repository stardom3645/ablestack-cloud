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
  <section class="allocation-summary" :class="{ compact }" data-testid="allocation-summary">
    <template v-if="compact">
      <a-tag>{{ allocationLabel(resource, $t) }}</a-tag>
      <span v-if="inheritance" class="allocation-note">{{ $t('message.allocation.inheritance') }}</span>
    </template>
    <template v-else>
      <a-descriptions :title="$t('label.allocation.title')" size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
        <a-descriptions-item :label="$t('label.allocation.requested')">{{ allocationLabel(resource, $t) }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.fixed')">{{ verified ? policy(a.fixedpolicy) : unknown }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.applied')">{{ verified ? policy(a.effectivepolicy) : unknown }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.method')">{{ verified ? a.appliedmethod : unknown }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.actual.format')">{{ a.actualformat && a.actualformat !== 'unknown' ? a.actualformat.toUpperCase() : unknown }}</a-descriptions-item>
        <a-descriptions-item v-if="volume" :label="$t('label.allocation.logical')">{{ resource.size == null ? unknown : $bytesToHumanReadableSize(resource.size) }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.observed')">{{ verified && a.observedallocatedbytes != null ? $bytesToHumanReadableSize(Number(a.observedallocatedbytes)) : unknown }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.observed.at')">{{ verified ? a.observedat || unknown : unknown }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.allocation.evidence')">{{ $t('label.allocation.evidence.' + (a.evidence || 'unverified')) }}</a-descriptions-item>
      </a-descriptions>
      <p class="allocation-note">{{ $t(volume ? 'message.allocation.observation' : 'message.allocation.offering.conditions') }}</p>
      <p v-if="inheritance" class="allocation-note">{{ $t('message.allocation.inheritance') }}</p>
    </template>
  </section>
</template>
<script>
import { allocationLabel } from '@/utils/volumeAllocation'
export default {
  name: 'AllocationSummary',
  props: { resource: { type: Object, default: () => ({}) }, volume: Boolean, compact: Boolean, inheritance: Boolean },
  computed: {
    a () { return this.resource?.allocation || {} },
    verified () { return this.a.evidence === 'verified' },
    unknown () { return this.$t('label.allocation.unknown') }
  },
  methods: { allocationLabel, policy (p) { return p && p !== 'unknown' ? this.$t('label.allocation.policy.' + p) : this.unknown } }
}
</script>
<style scoped>
.allocation-summary { margin-bottom: 16px; overflow-wrap: anywhere; }
.allocation-summary.compact { margin: 4px 0; }
.allocation-note { font-size: 12px; color: var(--ui-text-secondary, #666); margin: 4px 0; }
</style>
