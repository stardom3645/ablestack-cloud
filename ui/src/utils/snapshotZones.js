// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with this
// work for additional information regarding copyright ownership. The ASF
// licenses this file to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the License.
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations.
import { getAPI } from '@/utils/storageApi'
import { readAllocationList } from '@/utils/volumeAllocation'

export default {
  data: () => ({ zones: [], zoneLoading: false, zoneError: false, zoneVersion: 0, zonesDisposed: false }),
  computed: {
    snapshotDefaultZone () {
      return this.resource.zoneid ? { id: this.resource.zoneid, name: this.resource.zonename || this.resource.zoneid } : null
    },
    snapshotZoneIds: {
      get () { return [...new Set([this.resource.zoneid, ...(this.form?.zoneids || [])].filter(Boolean))] },
      set (ids) { this.form.zoneids = [...new Set(ids)].filter(id => id !== this.resource.zoneid) }
    },
    snapshotZoneContext () {
      return JSON.stringify([this.resource.id, this.resource.zoneid, this.$route?.path,
        this.$store.getters.userInfo?.id, this.$store.state?.user?.token, this.$store.getters.project?.id])
    }
  },
  watch: {
    snapshotZoneContext () {
      this.zoneVersion++; this.zones = this.snapshotDefaultZone ? [this.snapshotDefaultZone] : []; this.zoneLoading = false; this.zoneError = false
      if (this.form) this.form.zoneids = []
    }
  },
  beforeUnmount () { this.zonesDisposed = true; this.zoneVersion++ },
  methods: {
    async fetchZoneData () {
      const version = ++this.zoneVersion; const context = this.snapshotZoneContext; const zoneid = this.resource.zoneid
      const current = () => !this.zonesDisposed && version === this.zoneVersion && context === this.snapshotZoneContext
      this.zoneLoading = true; this.zoneError = false; this.zones = this.snapshotDefaultZone ? [this.snapshotDefaultZone] : []
      try {
        const request = (command, params, options) => getAPI(command, params, { ...options, timeout: 20000 })
        const rows = await readAllocationList(request, 'listZones', 'zone', { showicon: true }, current)
        if (current()) {
          const native = rows.find(zone => zone.id === zoneid) || this.snapshotDefaultZone
          this.zones = [...(native ? [native] : []), ...rows.filter(zone => zone.type !== 'Edge' && zone.id !== zoneid)]
        }
      } catch (error) {
        if (current()) {
          this.zoneError = true
          this.form.zoneids = []
        }
      } finally { if (current()) this.zoneLoading = false }
    }
  }
}
