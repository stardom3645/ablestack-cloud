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
  <a
    v-if="['vm'].includes($route.meta.name) && 'updateVirtualMachine' in $store.getters.apis"
    :href="urlAction($store.getters, resource.id)"
    target="_blank" >
    <a-button style="margin-left: 5px" shape="circle" type="" :size="size" :disabled="['Stopped', 'Error', 'Destroyed'].includes(resource.state)" >
      <AreaChartOutlined />
    </a-button>
  </a>
</template>
<script>
export default {
  name: 'WallLinkUrl',
  props: {
    resource: {
      type: Object,
      required: true
    },
    size: {
      type: String,
      default: 'small'
    }
  },
  methods: {
    urlAction (storeGetters, resourceId) {
      var uri = ''
      const host = storeGetters.features.host
      const wallPortalProtocol = storeGetters.features.wallportalprotocol
      const wallPortalDomain = storeGetters.features.wallportaldomain
      const wallPortalPort = storeGetters.features.wallportalport
      if (wallPortalProtocol === null || wallPortalProtocol === '') {
        uri += 'http://'
      } else {
        uri += wallPortalProtocol + '://'
      }
      if (wallPortalDomain === null || wallPortalDomain === '') {
        uri += host
      } else {
        uri += wallPortalDomain
      }
      if (typeof wallPortalPort !== 'undefined') {
        uri += ':' + wallPortalPort
      }
      uri += storeGetters.features.wallportalvmuri + '?kiosk&orgId=2&var-vm_uuid=' + resourceId
      return uri
    }
  }
}
</script>
