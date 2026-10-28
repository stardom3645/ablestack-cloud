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
  <a-modal
    :visible="true"
    :title="$t(currentAction.label)"
    :footer="null"
    :mask-closable="false"
    :closable="!running"
    :keyboard="!running"
    :width="600"
    centered
    @cancel="close"
  >
    <a-spin :spinning="fetching">
      <a-alert
        v-if="loadFailed"
        type="error"
        show-icon
        :message="$t('message.wall.rules.load.failed')"
      >
        <template #action><a-button size="small" @click="loadTargets">{{ $t('label.refresh') }}</a-button></template>
      </a-alert>
      <template v-else>
        <a-alert
          :type="isAll ? 'warning' : 'info'"
          show-icon
          :message="$t(isAll ? 'message.wall.rules.scope.all' : 'message.wall.rules.scope.selected', { count: rules.length, targets: targets.length })"
          :description="$t('message.wall.rules.effect.' + kind)"
        />
        <p v-if="rules.length > targets.length" class="rule-note">
          {{ $t('message.wall.rules.skipped', { count: rules.length - targets.length }) }}
        </p>
        <p v-if="!fetching && !targets.length" role="status">{{ $t('message.wall.rules.no.targets') }}</p>
        <ul class="rule-targets">
          <li v-for="rule in targets" :key="ruleUid(rule)">{{ rule.name || ruleUid(rule) }}</li>
        </ul>
        <a-form layout="vertical">
          <a-form-item v-if="kind === 'silence'" :label="$t('label.silenceperiod.select')">
            <a-select v-model:value="duration" :disabled="running || attempted" style="width: 100%">
              <a-select-option v-for="option in durations" :key="option.value" :value="option.value">
                {{ $t(option.label, [option.amount]) }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-checkbox v-model:checked="acknowledged" :disabled="running || !targets.length">
            {{ $t('message.wall.rules.confirm') }}
          </a-checkbox>
        </a-form>
        <a-progress v-if="running" :percent="progress" :status="'active'" />
        <a-alert
          v-if="failures.length"
          class="rule-result"
          type="error"
          show-icon
          :message="$t('message.wall.rules.partial.failure', { success: successCount, failed: failures.length })"
        >
          <template #description>
            <ul class="rule-failures">
              <li v-for="failure in failures" :key="ruleUid(failure.rule)">
                {{ failure.rule.name || ruleUid(failure.rule) }}: {{ errorText(failure.error) }}
              </li>
            </ul>
          </template>
        </a-alert>
      </template>
      <div class="rule-actions">
        <a-button :disabled="running" @click="close">{{ $t('label.close') }}</a-button>
        <a-button
          type="primary"
          :danger="kind === 'pause'"
          :loading="running"
          :disabled="fetching || loadFailed || !acknowledged || !targets.length"
          @click="submit"
        >
          {{ $t(attempted ? 'label.wall.rules.retry.failed' : currentAction.label) }}
        </a-button>
      </div>
    </a-spin>
  </a-modal>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import { applyRuleAction, eligibleRules, fetchAllRules, ruleUid, uniqueRules } from '@/utils/wallAlertRules'

export default {
  name: 'WallAlertRuleActionModal',
  emits: ['refresh-data', 'close-action'],
  props: {
    resource: { type: Object, default: () => ({}) },
    selectedItems: { type: Array, default: () => [] },
    currentAction: { type: Object, required: true }
  },
  data () {
    return {
      fetching: false,
      loadFailed: false,
      running: false,
      acknowledged: false,
      attempted: false,
      rules: [],
      failures: [],
      successCount: 0,
      completed: 0,
      batchSize: 0,
      duration: 30,
      durations: [
        { value: 30, label: 'label.silence.duration.minutes', amount: 30 },
        ...[1, 2, 6, 12, 24].map(n => ({ value: n * 60, label: 'label.silence.duration.hours', amount: n })),
        { value: 4320, label: 'label.silence.duration.days', amount: 3 },
        ...[1, 2].map(n => ({ value: n * 10080, label: 'label.silence.duration.weeks', amount: n })),
        { value: 43200, label: 'label.silence.duration.months', amount: 1 }
      ]
    }
  },
  computed: {
    kind () { return this.currentAction.wallRuleAction },
    isAll () { return this.currentAction.wallRuleScope === 'all' },
    targets () {
      return this.attempted ? this.failures.map(failure => failure.rule) : eligibleRules(this.rules, this.kind)
    },
    progress () { return this.batchSize ? Math.round(this.completed / this.batchSize * 100) : 0 }
  },
  created () { this.loadTargets() },
  methods: {
    ruleUid,
    errorText (error) {
      return error?.response?.data?.errorresponse?.errortext || error?.message || this.$t('error.execute.api.failed')
    },
    async loadTargets () {
      this.fetching = true
      this.loadFailed = false
      try {
        const rules = this.isAll
          ? await fetchAllRules(getAPI)
          : (this.currentAction.invokedAsGroupAction ? this.selectedItems : [this.resource])
        // Snapshot the targets so automatic list refreshes cannot change a confirmation.
        this.rules = uniqueRules(rules).map(rule => ({ ...rule }))
      } catch (error) {
        this.rules = []
        this.loadFailed = true
      } finally {
        this.fetching = false
      }
    },
    async submit () {
      if (this.running || this.fetching || this.loadFailed || !this.acknowledged || !this.targets.length) return
      this.running = true
      const targets = [...this.targets]
      this.completed = 0
      this.batchSize = targets.length
      try {
        const result = await applyRuleAction(postAPI, targets, this.kind, this.duration, count => { this.completed = count })
        this.successCount += result.succeeded.length
        this.failures = result.failed
        this.attempted = true
        if (result.succeeded.length) this.$emit('refresh-data')
        if (!this.failures.length) {
          this.$message.success(this.$t('message.wall.rules.success', { count: this.successCount }))
          this.$emit('close-action')
        }
      } finally {
        this.running = false
      }
    },
    close () {
      if (!this.running) this.$emit('close-action')
    }
  }
}
</script>

<style scoped>
.rule-note { margin-top: 12px; color: var(--ui-text-secondary); }
.rule-targets { max-height: 180px; overflow: auto; margin: 16px 0; padding-left: 22px; }
.rule-result { margin-top: 16px; }
.rule-failures { max-height: 140px; overflow: auto; padding-left: 18px; margin: 0; }
.rule-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
</style>
