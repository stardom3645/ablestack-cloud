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
  <section class="autoscale-operations">
    <div class="operations-toolbar"><p>{{ $t('message.autoscale.operational.source') }}</p><a-button @click="fetchData" :loading="loading"><template #icon><reload-outlined /></template>{{ $t('label.autoscale.operational.refresh') }}</a-button></div>
    <div v-for="(part, key) in parts" :key="key" class="source-status" role="status">
      <strong>{{ $t('label.autoscale.source.' + key) }}</strong> · {{ time(part.time) }}
      <span v-if="part.denied"> · {{ $t('message.autoscale.source.denied') }}</span>
      <span v-else-if="part.failed"> · {{ $t(part.loaded ? 'message.autoscale.source.stale' : 'message.autoscale.source.failed') }}</span>
      <span v-else-if="part.loading"> · {{ $t('message.autoscale.source.loading') }}</span>
    </div>
    <auto-scale-operational-summary :resource="parts.group.rows" />
    <auto-scale-evaluation :resource="parts.group.rows" />
    <a-card size="small" :title="$t('label.autoscale.members')">
      <p>{{ $t('message.autoscale.lb.meaning') }}</p>
      <a-input-search class="member-search" v-model:value="keyword" :placeholder="$t('label.autoscale.member.search')" @search="searchMembers" allow-clear />
      <p v-if="parts.members.failed && parts.members.loaded">{{ $t('label.autoscale.members.last.query') }}: {{ parts.members.query?.keyword || $t('label.all') }} · {{ $t('label.autoscale.members.page') }} {{ parts.members.query?.page }}</p>
      <a-table
size="small"
row-key="id"
:columns="columns"
:data-source="parts.members.rows"
:loading="parts.members.loading && !parts.members.loaded"
        :scroll="{ x: 640 }"
:pagination="{ current: page, pageSize, total, showSizeChanger: false }"
@change="changePage">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'"><router-link :to="'/vm/' + record.id">{{ record.displayname || record.name }}</router-link></template>
          <template v-else-if="column.key === 'state'"><status :text="record.state" :display-text="true" /></template>
          <template v-else-if="column.key === 'lb'">{{ lbLabel(record) }}</template>
        </template>
        <template #emptyText>{{ $t(parts.members.denied ? 'message.autoscale.source.denied' : parts.members.failed ? 'message.autoscale.source.failed' : parts.members.loaded ? 'message.autoscale.members.empty' : 'message.autoscale.source.loading') }}</template>
      </a-table>
    </a-card>
    <a-card size="small" :title="$t('label.autoscale.recent.events')">
      <p>{{ $t('message.autoscale.events.meaning') }}</p>
      <p v-if="!parts.events.rows.length">{{ $t(parts.events.denied ? 'message.autoscale.source.denied' : parts.events.failed ? 'message.autoscale.source.failed' : parts.events.loaded ? 'message.autoscale.events.empty' : 'message.autoscale.source.loading') }}</p>
      <ul class="recent-events"><li v-for="event in parts.events.rows" :key="event.id">
        <strong>{{ time(event.created) }} · {{ $t('label.autoscale.event.level.' + (event.level || 'INFO')) }}</strong>
        <p>{{ eventLabel(event) }} · <status :text="event.state" :display-text="true" :display-label="eventState(event.state)" /></p>
        <router-link :to="'/event/' + event.id">{{ $t('label.autoscale.event.id') }}: {{ event.id }}</router-link>
        <p v-if="event.parentid"><router-link :to="'/event/' + event.parentid">{{ $t('label.autoscale.event.parent') }}: {{ event.parentid }}</router-link></p>
        <details><summary>{{ $t('label.autoscale.event.server.message') }}</summary>{{ event.description }}</details>
      </li></ul>
    </a-card>
  </section>
</template>
<script>
import '@/style/components/view/autoscale.less'
import { getAPI } from '@/api'
import Status from '@/components/widgets/Status'
import AutoScaleEvaluation from '@/views/compute/AutoScaleEvaluation'
import AutoScaleOperationalSummary from '@/views/compute/AutoScaleOperationalSummary'
import { autoScaleTime, autoScaleLbState } from '@/utils/autoScaleOperations'
const part = rows => ({ rows, loaded: false, loading: false, failed: false, denied: false, time: null, sequence: 0, query: null })
export default {
  name: 'AutoScaleOperations',
  components: { Status, AutoScaleEvaluation, AutoScaleOperationalSummary },
  props: { resource: { type: Object, required: true } },
  data () { return { parts: { group: part(this.resource), members: part([]), lb: part([]), events: part([]) }, page: 1, pageSize: 10, total: 0, keyword: '', active: true } },
  computed: {
    loading () { return Object.values(this.parts).some(item => item.loading) },
    columns () { return ['name', 'state', 'lb'].map(key => ({ key, dataIndex: key, title: this.$t('label.autoscale.member.' + key) })) }
  },
  watch: {
    resource: {
      handler (next, previous) {
        if (next.id !== previous.id) {
          Object.values(this.parts).forEach(item => item.sequence++)
          this.parts = { group: part(next), members: part([]), lb: part([]), events: part([]) }
          this.page = 1; this.total = 0; this.keyword = ''
        }
        // The standard ResourceLayout refresh/action completion also refreshes
        // these independent reads, without resetting the current member query.
        this.fetchData()
      }
    }
  },
  created () { this.fetchData() },
  beforeUnmount () { this.active = false; Object.values(this.parts).forEach(item => item.sequence++) },
  methods: {
    time (value) { return autoScaleTime(value, value => this.$toLocaleDate(value), key => this.$t(key)) },
    lbLabel (vm) {
      const state = autoScaleLbState(vm, this.parts.lb)
      const key = 'label.autoscale.lb.' + state
      const label = this.$t(key); return label !== key ? label : this.$t('label.autoscale.lb.REGISTERED') + ' (' + state + ')'
    },
    eventState (state) { const key = 'label.autoscale.event.state.' + state; const label = this.$t(key); return label !== key ? label : state || this.$t('label.autoscale.not.recorded') },
    eventLabel (event) {
      if ((event.description || '').includes('AutoScale lifecycle failed at')) return this.$t('message.autoscale.operational.retry')
      const key = 'label.autoscale.event.' + event.type
      const label = this.$t(key); return label !== key ? label : event.type
    },
    async readPart (name, api, params, extract) {
      const item = this.parts[name]; const sequence = ++item.sequence
      if (!(api in this.$store.getters.apis)) { item.denied = true; item.loading = false; item.rows = name === 'group' ? {} : []; item.loaded = false; if (name === 'members') this.total = 0; return }
      item.denied = false; item.loading = true
      try {
        const response = await getAPI(api, params)
        if (!this.active || sequence !== item.sequence || item !== this.parts[name]) return
        const result = extract(response)
        item.rows = result.rows; item.loaded = true; item.failed = false; item.time = Date.now(); item.query = { ...params }
        if (name === 'members') this.total = result.total
      } catch (error) {
        if (!this.active || sequence !== item.sequence || item !== this.parts[name]) return
        item.failed = true
      } finally {
        if (this.active && sequence === item.sequence && item === this.parts[name]) item.loading = false
      }
    },
    fetchMembers () {
      return this.readPart('members', 'listVirtualMachines', { autoscalevmgroupid: this.resource.id, projectid: this.resource.projectid, listall: true, page: this.page, pagesize: this.pageSize, keyword: this.keyword || undefined, details: 'min' },
        response => ({ rows: response.listvirtualmachinesresponse.virtualmachine || [], total: response.listvirtualmachinesresponse.count || 0 }))
    },
    fetchData () {
      return Promise.allSettled([
        this.readPart('group', 'listAutoScaleVmGroups', { id: this.resource.id, projectid: this.resource.projectid, listall: true }, response => {
          const rows = response.listautoscalevmgroupsresponse.autoscalevmgroup?.[0]
          if (!rows) throw new Error('Group is absent or inaccessible')
          return { rows }
        }),
        this.fetchMembers(),
        this.resource.lbruleid ? this.readPart('lb', 'listLoadBalancerRuleInstances', { id: this.resource.lbruleid }, response => ({ rows: response.listloadbalancerruleinstancesresponse.loadbalancerruleinstance || [] })) : Promise.resolve(),
        this.readPart('events', 'listEvents', { resourceid: this.resource.id, resourcetype: 'AutoScaleVmGroup', listall: true, page: 1, pagesize: 5 }, response => ({ rows: response.listeventsresponse.event || [] }))
      ])
    },
    searchMembers () { this.page = 1; return this.fetchMembers() },
    changePage (pagination) { this.page = pagination.current; return this.fetchMembers() }
  }
}
</script>
<style scoped lang="scss">
.autoscale-operations { color: var(--ui-text-primary); overflow-wrap: anywhere;
  .operations-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
  .operations-toolbar p { margin: 0; flex: 1; min-width: 220px; }
  .source-status { margin-bottom: 6px; }
  .ant-card { margin: 16px 0; background: var(--ui-bg-surface); }
  .member-search { width: min(100%, 320px); margin: 12px 0; }
  a { color: var(--ui-text-primary); text-decoration: underline; text-underline-offset: 2px; }
  .recent-events { padding-left: 20px; }
  .recent-events li { margin-bottom: 16px; }
  details { margin-top: 8px; } summary { cursor: pointer; }
}
</style>
