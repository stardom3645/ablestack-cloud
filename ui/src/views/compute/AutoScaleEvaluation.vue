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
  <a-card class="autoscale-evaluation" size="small" :title="$t('label.autoscale.evaluation')">
    <a-alert v-if="resource.state === 'DISABLED'" type="info" show-icon :message="$t('message.autoscale.evaluation.disabled')" />
    <p v-if="!evaluation" role="status">{{ $t('message.autoscale.evaluation.none') }}</p>
    <template v-else>
      <a-alert
        :type="['WAITING', 'SCALEUP_UNCERTAIN'].includes(evaluation.status) ? 'warning' : 'info'"
        show-icon
        role="status"
        :message="$t('label.autoscale.evaluation.' + evaluation.status)" />
      <dl class="evaluation-times">
        <div><dt>{{ $t('label.autoscale.evaluated.at') }}</dt><dd>{{ formatTime(evaluation.evaluatedat) }}</dd></div>
        <div><dt>{{ $t('label.autoscale.collected.at') }}</dt><dd>{{ formatTime(evaluation.collectedat) }}</dd></div>
        <div v-if="evaluation.status === 'COOLDOWN'"><dt>{{ $t('label.autoscale.cooldown.until') }}</dt><dd>{{ formatTime(evaluation.cooldownuntil) }}</dd></div>
      </dl>
      <div v-for="policy in evaluation.policies || []" :key="policy.id" class="evaluation-policy">
        <strong>{{ policyName(policy) }} · {{ $t('label.autoscale.evaluation.' + policy.status) }}</strong>
        <p v-if="policy.reason">{{ $t('message.autoscale.evaluation.' + policy.reason) }}</p>
        <p>{{ $t('label.duration') }}: {{ policy.duration }} {{ $t('label.seconds') }}</p>
        <ul>
          <li v-for="(metric, index) in policy.conditions || []" :key="metric.id || index">
            <strong>{{ counterLabel(metric.countername) }}</strong>
            <div>{{ $t('label.autoscale.observed.value') }}: {{ formatValue(metric.value) }} {{ metric.unit === 'connections/VM' ? $t('label.autoscale.connections.per.vm') : metric.unit }} · {{ metric.operator }} {{ metric.threshold }}</div>
            <div>{{ $t('label.autoscale.samples') }}: {{ metric.samples }} · {{ $t('label.autoscale.resources') }}: {{ metric.resources }} · {{ formatTime(metric.collectedat) }}</div>
            <div>{{ metric.reason ? $t('message.autoscale.evaluation.' + metric.reason) : $t(metric.matched ? 'label.autoscale.evaluation.MATCHED' : 'label.autoscale.evaluation.NOT_MATCHED') }}</div>
          </li>
        </ul>
      </div>
      <a-alert v-if="hasNetworkMetric" type="info" show-icon :message="$t('message.autoscale.evaluation.network.scope')" />
      <a-alert v-if="hasMemoryMetric" type="info" show-icon :message="$t('message.autoscale.evaluation.memory.requirements')" />
    </template>
  </a-card>
</template>

<script>
import { autoScaleEvaluationValue, autoScaleCounterLabel } from '@/utils/autoScalePolicyEvaluation'

export default {
  name: 'AutoScaleEvaluation',
  props: { resource: { type: Object, required: true } },
  computed: {
    evaluation () { return this.resource.evaluation || null },
    metrics () { return (this.evaluation?.policies || []).flatMap(policy => policy.conditions || []) },
    hasNetworkMetric () { return this.metrics.some(metric => metric.unit === 'Mbps/VM') },
    hasMemoryMetric () { return this.metrics.some(metric => metric.source === 'MEMORY') }
  },
  methods: {
    formatValue: autoScaleEvaluationValue,
    counterLabel (name) { return autoScaleCounterLabel(name, this.$t) },
    formatTime (time) { return time ? this.$toLocaleDate(new Date(time).toISOString()) : this.$t('label.autoscale.not.collected') },
    policyName (policy) {
      const configured = [...(this.resource.scaleuppolicies || []), ...(this.resource.scaledownpolicies || [])].find(item => item.id === policy.id)
      return configured?.name || this.$t('label.autoscale.operation.' + policy.action)
    }
  }
}
</script>

<style lang="scss" scoped>
.autoscale-evaluation {
  margin-bottom: 16px;
  overflow-wrap: anywhere;
  .evaluation-times { display: flex; flex-wrap: wrap; gap: 16px; margin: 12px 0; }
  dt { font-weight: 600; }
  dd { margin: 0; }
  .evaluation-policy { margin: 16px 0; }
  ul { padding-left: 20px; }
  li { margin-bottom: 12px; }
  .ant-alert { margin-bottom: 8px; }
}
</style>
