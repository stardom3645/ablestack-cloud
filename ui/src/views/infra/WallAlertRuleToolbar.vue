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
  <div class="rule-toolbar">
    <a-space wrap>
      <span role="status">{{ $t('message.wall.rules.selection', { count: selectedItems.length }) }}</span>
      <a-button
        v-for="action in availableActions"
        :key="action.wallRuleAction"
        :disabled="loading || !canApply(action)"
        @click="$emit('exec-action', { ...action, wallRuleScope: 'selected', resource: undefined })"
      >
        <template #icon><render-icon :icon="action.icon" /></template>
        {{ $t(action.label) }}
      </a-button>
    </a-space>
    <a-dropdown v-if="availableActions.length" :trigger="['click']" :disabled="loading">
      <a-button :disabled="loading">
        {{ $t('label.wall.rules.all.actions') }} <down-outlined />
      </a-button>
      <template #overlay>
        <a-menu>
          <a-menu-item
            v-for="action in availableActions"
            :key="action.wallRuleAction"
            @click="$emit('exec-action', { ...action, wallRuleScope: 'all', resource: undefined })"
          >
            {{ $t(action.label) }}
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
    <div class="rule-toolbar-help">{{ $t('message.wall.rules.selection.help') }}</div>
  </div>
</template>

<script>
import { eligibleRules } from '@/utils/wallAlertRules'

export default {
  name: 'WallAlertRuleToolbar',
  emits: ['exec-action'],
  props: {
    actions: { type: Array, default: () => [] },
    selectedItems: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  computed: {
    availableActions () {
      return this.actions.filter(action => action.wallRuleAction && action.api in this.$store.getters.apis)
    }
  },
  methods: {
    canApply (action) {
      return eligibleRules(this.selectedItems, action.wallRuleAction).length > 0
    }
  }
}
</script>

<style scoped>
.rule-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.rule-toolbar-help { flex-basis: 100%; color: var(--ui-text-secondary); font-size: 12px; }
</style>
