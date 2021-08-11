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
    v-if="['desktopcluster'].includes($route.meta.name) && 'listDesktopClusters' in $store.getters.apis"
    :href="protocol + resource.worksvmip + port"
    target="_blank">
    <a-button style="margin-left: 5px" shape="circle" type="" :size="size" :disabled="['Stopped', 'Error', 'Destroyed'].includes(resource.state)" >
      <a-icon type="link" />
    </a-button>
  </a>
</template>

<script>
export default {
  name: 'Liknurl',
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
  data () {
    return {
      protocol: 'https://',
      port: ':10000'// 실제 works포털 포트로 변경 필요
    }
  }
}
</script>
