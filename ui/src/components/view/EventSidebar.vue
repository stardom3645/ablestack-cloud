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
  <a-drawer
    :visible="isVisible"
    :mask="false"
    :maskClosable="false"
    placement="bottom"
    :height="200"
    :closable="false"
    @close="closeSidebar"
  >
    <div class="sidebar-header">
      <h3><schedule-outlined /> {{ $t('label.events') }}</h3>
      <a-button @click="closeSidebar">{{ $t('label.close') }}</a-button>
    </div>

    <a-col :xs="{ span: 24 }" :lg="{ span: 24 }">
      <chart-card :loading="loading" class="dashboard-card dashboard-event">
        <a-divider style="margin: 6px 0px; border-width: 0px" />
        <a-timeline>
          <a-timeline-item
            v-for="event in events"
            :key="event.id"
            :color="getEventColour(event)"
            style="display: block;"
          >
            <span :style="{ color: '#999' }">
              <small>{{ $toLocaleDate(event.created) }}</small>
            </span>
            <br>
            <span :style="{ color: '#666' }">
              <small>
                <router-link :to="{ path: '/event/' + event.id }">{{ event.level }} {{ event.type }} {{ event.state }} {{ event.resourceType }} </router-link>
              </small>
            </span>
            <span>
              <resource-label :resourceType="event.resourcetype" :resourceId="event.resourceid" :resourceName="event.resourcename" />
            </span>
            <span :style="{ color: '#aaa' }">{{ event.resourcename }} ({{ event.username }}) {{ event.description }}</span>
          </a-timeline-item>
        </a-timeline>
        <router-link :to="{ path: '/event' }">
          <a-button>
            {{ $t('label.view') }} {{ $t('label.events') }}
          </a-button>
        </router-link>
      </chart-card>
    </a-col>
  </a-drawer>
</template>

<script>
import { api } from '@/api'

export default {
  name: 'EventSidebar',
  props: {
    isVisible: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      events: [],
      loading: false,
      refreshInterval: null
    }
  },
  created () {
    this.fetchEvents()
    this.refreshInterval = setInterval(this.fetchEvents, 5000)
  },
  unmounted () {
    clearInterval(this.refreshInterval)
  },
  methods: {
    closeSidebar () {
      this.$emit('update:isVisible', false)
    },
    async fetchEvents () {
      if (!('listEvents' in this.$store.getters.apis)) {
        return
      }
      this.loading = true
      const params = {
        page: 1,
        pagesize: 8,
        listall: true
      }
      try {
        const response = await api('listEvents', params)
        this.events = response.listeventsresponse?.event || []
      } catch (error) {
        console.error('Error getting event list:', error)
      } finally {
        this.loading = false
      }
    },
    getEventColour (event) {
      return 'blue'
    }
  }
}
</script>

<style scoped>
.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.event-list {
  padding: 16px;
}
</style>
