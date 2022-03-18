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
  <div class="form-layout">
    <a-spin :spinning="loading">
      <a-form :form="form" :loading="loading" @submit="handleSubmit" layout="vertical">
        <a-form-item ref="name" name="name">
          <template #label>
            <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
          </template>
          <a-input
            v-decorator="['name', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('label.name')" />
        </a-form-item>
        <a-form-item ref="description" name="description">
          <template #label>
            <tooltip-label :title="$t('label.description')" :tooltip="apiParams.description.description"/>
          </template>
          <a-input
            v-decorator="['description', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('label.description')" />
        </a-form-item>
        <a-form-item ref="addomainname" name="addomainname">
          <template #label>
            <tooltip-label :title="$t('label.addomainname')" :tooltip="apiParams.addomainname.description"/>
          </template>
          <a-input
            v-decorator="['addomainname', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('placeholder.addomainname')" />
        </a-form-item>
        <!-- <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item name="password" ref="password">
              <template #label>
                <tooltip-label :title="$t('label.password')" :tooltip="apiParams.password.description"/>
              </template>
              <a-input-password
                v-decorator="['password', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.password')"/>
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="confirmpassword" ref="confirmpassword">
              <template #label>
                <tooltip-label :title="$t('label.confirmpassword')" :tooltip="apiParams.confirmpassword.description"/>
              </template>
              <a-input-password
                v-decorator="['confirmpassword', {
                  rules: [
                    { required: true, message: $t('message.error.required.input') },
                    { validator: validateConfirmPassword }
                  ]
                }]"
                :placeholder="$t('placeholder.confirmpassword')"/>
            </a-form-item>
          </a-col>
        </a-row> -->
        <a-form-item name="version" ref="version">
          <template #label>
            <tooltip-label :title="$t('label.desktop.controller.template.version')" :tooltip="apiParams.version.description"/>
          </template>
          <a-select
            v-decorator="['templateversion', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :loading="templateVersionLoading"
            :placeholder="$t('placeholder.desktop.controller.template.version')">
            <a-select-option v-for="(opt, optIndex) in this.templateVersions" :key="optIndex">
              {{ opt.name }} {{ opt.version }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="offerings" ref="offerings">
          <template #label>
            <tooltip-label :title="$t('abel.compute.offerings')" :tooltip="apiParams.offerings.description"/>
          </template>
          <a-select
            v-decorator="['serviceofferingid', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            showSearch
            optionFilterProp="children"
            :filterOption="(input, option) => {
              return option.componentOptions.children[0].text.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :loading="serviceOfferingLoading"
            :placeholder="$t('placeholder.compute.offering')">
            <a-select-option v-for="(opt, optIndex) in this.serviceOfferings" :key="optIndex">
              {{ opt.name || opt.description }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <!-- <a-form-item>
          <template #label>
            <a-tooltip :title="$t('label.access.type')">
                <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
              </a-tooltip>
          </template>
          <a-radio-group
            v-decorator="['accesstype', {
              initialValue: this.accessType,
              rules: [{ required: true, message: $t('message.error.select') }]
            }]"
            buttonStyle="solid"
            @change="selected => { this.handleAccessTypeChange(selected.target.value) }">
            <a-radio-button value="external">
              {{ $t('label.access.external') }}
            </a-radio-button>
            <a-radio-button value="internal">
              {{ $t('label.access.internal') }}
            </a-radio-button>
            <a-radio-button value="mixed">
              {{ $t('label.access.mixed') }}
            </a-radio-button>
          </a-radio-group>
        </a-form-item> -->
        <a-form-item name="network" ref="network">
          <template #label>
            <tooltip-label :title="$t('label.network')" :tooltip="apiParams.network.description"/>
          </template>
          <a-select
            :loading="networkLoading"
            v-decorator="['networkid', {
              initialValue: this.networks,
              rules: [{ required: true, message: $t('message.error.select') }] }]"
            :placeholder="$t('placeholder.network')"
            showSearch
            @change="val => { this.handleNetworkChange(this.networks[val]) }">
            <a-select-option v-for="(opt, optIndex) in this.networks" :key="optIndex">
              <span v-if="opt.type!=='L2'">
                {{ opt.name || opt.description }} ({{ `${$t('label.cidr')}: ${opt.cidr}` }})
              </span>
              <span v-else>{{ opt.name || opt.description }}</span>
            </a-select-option>
          </a-select>
        </a-form-item>
        <!-- <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item name="gateway" ref="gateway" v-if="this.accessType=='internal'">
              <template #label>
                <tooltip-label :title="$t('label.gateway')" :tooltip="apiParams.gateway.description"/>
              </template>
              <a-input
                v-decorator="['gateway', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('label.gateway')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="netmask" ref="netmask" v-if="this.accessType=='internal'">
              <template #label>
                <tooltip-label :title="$t('label.netmask')" :tooltip="apiParams.netmask.description"/>
              </template>
              <a-input
                v-decorator="['netmask', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.netmask')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="startip" ref="startip" v-if="this.accessType=='internal'">
              <template #label>
                <tooltip-label :title="$t('label.startip')" :tooltip="apiParams.startip.description"/>
              </template>
              <a-input
                v-decorator="['startip', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.startip')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="endip" ref="endip" v-if="this.accessType=='internal'">
              <template #label>
                <tooltip-label :title="$t('label.endip')" :tooltip="apiParams.endip.description"/>
              </template>
              <a-input
                v-decorator="['endip', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.endip')" />
            </a-form-item>
          </a-col>
        </a-row> -->
        <a-form-item name="worksvmip" ref="worksvmip">
          <template #label>
            <tooltip-label :title="$t('label.worksvmip')" :tooltip="apiParams.worksvmip.description"/>
          </template>
          <a-input
            v-decorator="['worksip', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('placeholder.worksvmip')" />
        </a-form-item>
        <a-form-item name="dcvmip" ref="dcvmip">
          <template #label>
            <tooltip-label :title="$t('label.dcvmip')" :tooltip="apiParams.dcvmip.description"/>
          </template>
          <a-input
            v-decorator="['dcip', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('placeholder.dcvmip')" />
        </a-form-item>
        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button :loading="loading" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>
<script>
import { api } from '@/api'
import store from '@/store'
import eventBus from '@/config/eventBus'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'CreateDesktopCluster',
  components: {
    TooltipLabel
  },
  props: {},
  data () {
    return {
      loading: false,
      accessType: 'external',
      clusters: [],
      networks: [],
      networkLoading: false,
      templateVersions: [],
      templateVersionLoading: false,
      serviceOfferings: [],
      serviceOfferingLoading: false
    }
  },
  beforeCreate () {
    this.form = this.$form.createForm(this)
    this.apiParams = this.$getApiParams('createDesktopCluster')
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      this.fetchClusterData()
      this.fetchTemplateVersionData()
      this.fetchServiceOfferingData()
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    arrayHasItems (array) {
      return array !== null && array !== undefined && Array.isArray(array) && array.length > 0
    },
    handleAccessTypeChange (pvlan) {
      this.accessType = pvlan
      this.fetchNetworkData()
    },
    // validateConfirmPassword (rule, value, callback) {
    //   if (!value || value.length === 0) {
    //     callback()
    //   } else if (rule.field === 'confirmpassword') {
    //     const form = this.form
    //     const messageConfirm = this.$t('error.password.not.match')
    //     const passwordVal = form.getFieldValue('password')
    //     if (passwordVal && passwordVal !== value) {
    //       callback(messageConfirm)
    //     } else {
    //       callback()
    //     }
    //   } else {
    //     callback()
    //   }
    // },
    fetchTemplateVersionData () {
      this.templateVersions = []
      const params = {}
      this.templateVersionLoading = true
      api('listDesktopControllerVersions', params).then(json => {
        var items = json.listdesktopcontrollerversionsresponse.desktopcontrollerversion
        if (items != null) {
          for (var i = 0; i < items.length; i++) {
            if (items[i].state === 'Enabled') {
              this.templateVersions.push(items[i])
            }
          }
        }
      }).finally(() => {
        this.templateVersionLoading = false
        if (this.arrayHasItems(this.templateVersions)) {
          this.form.setFieldsValue({
            templateversion: 0
          })
        }
      })
    },
    fetchServiceOfferingData () {
      this.serviceOfferings = []
      const params = {}
      this.serviceOfferingLoading = true
      api('listServiceOfferings', params).then(json => {
        var items = json.listserviceofferingsresponse.serviceoffering
        if (items != null) {
          for (var i = 0; i < items.length; i++) {
            if (items[i].iscustomized === false) {
              this.serviceOfferings.push(items[i])
            }
          }
        }
      }).finally(() => {
        this.serviceOfferingLoading = false
        if (this.arrayHasItems(this.serviceOfferings)) {
          this.form.setFieldsValue({
            serviceofferingid: 0
          })
        }
      })
    },
    fetchClusterData () {
      this.clusters = []
      const params = {
        domainid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainid,
        account: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.account,
        listall: true
      }
      api('listDesktopClusters', params).then(json => {
        var items = json.listdesktopclustersresponse.desktopcluster
        if (items != null) {
          this.clusters.push(items)
        }
      }).finally(() => {
        this.fetchNetworkData()
      })
    },
    fetchNetworkData () {
      this.networks = []
      const params = {
        domainid: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.domainid,
        account: store.getters.project && store.getters.project.id ? null : store.getters.userInfo.account
      }
      this.networkLoading = true
      this.handleNetworkChange(null)
      api('listNetworks', params).then(json => {
        var items = json.listnetworksresponse.network
        if (items != null) {
          for (var i = 0; i < items.length; i++) {
            if (this.accessType === 'internal' && items[i].type === 'L2') {
              this.networks.push(items[i])
              this.handleNetworkChange(this.networks[0])
            }
            if (this.accessType === 'external' && items[i].type === 'Isolated') {
              this.networks.push(items[i])
              if (this.clusters.length !== 0) {
                for (var j = 0; j < this.clusters[0].length; j++) {
                  if ([this.clusters[0][j].networkid].includes(items[i].id)) {
                    this.networks.pop(items[i])
                  }
                  this.handleNetworkChange(this.networks[0])
                }
              } else {
                this.handleNetworkChange(this.networks[0])
              }
            }
            if (this.accessType === 'mixed' && items[i].type === 'Shared') {
              this.networks.push(items[i])
              this.handleNetworkChange(this.networks[0])
            }
          }
        }
      }).finally(() => {
        this.networkLoading = false
        if (this.arrayHasItems(this.networks)) {
          this.form.setFieldsValue({
            networkid: 0
          })
        } else {
          this.form.setFieldsValue({
            networkid: null
          })
        }
      })
    },
    handleNetworkChange (network) {
      this.selectedNetwork = network
    },
    handleSubmit (e) {
      e.preventDefault()
      this.form.validateFields((err, values) => {
        if (err) {
          return
        }
        this.loading = true
        const params = {
          name: values.name,
          description: values.description,
          addomainname: values.addomainname,
          // desktoppassword: values.password,
          controllerversion: this.templateVersions[values.templateversion].id,
          serviceofferingid: this.serviceOfferings[values.serviceofferingid].id,
          networkid: this.selectedNetwork.id,
          clustertype: this.accessType,
          worksip: values.worksip,
          dcip: values.dcip
        }
        // if (this.isValidValueForKey(values, 'gateway')) {
        //   params.gateway = values.gateway
        // }
        // if (this.isValidValueForKey(values, 'netmask')) {
        //   params.netmask = values.netmask
        // }
        // if (this.isValidValueForKey(values, 'startip')) {
        //   params.startip = values.startip
        // }
        // if (this.isValidValueForKey(values, 'endip')) {
        //   params.endip = values.endip
        // }
        // if (this.isValidValueForKey(values, 'worksip')) {
        //   params.worksip = values.worksip
        // }
        // if (this.isValidValueForKey(values, 'dcip')) {
        //   params.dcip = values.dcip
        // }
        api('createDesktopCluster', params).then(json => {
          const jobId = json.createdesktopclusterresponse.jobid
          this.$pollJob({
            jobId,
            title: this.$t('label.desktop.cluster.deploy'),
            description: values.name,
            successMethod: () => {
              this.$notification.success({
                message: this.$t('message.success.create.desktop.cluter'),
                duration: 0
              })
              eventBus.$emit('desktop-refresh-data')
            },
            loadingMessage: `${this.$t('label.desktop.cluster.deploy')} ${values.name} ${this.$t('label.in.progress')}`,
            catchMessage: this.$t('error.fetching.async.job.result'),
            catchMethod: () => {
              eventBus.$emit('desktop-refresh-data')
            },
            action: {
              isFetchData: false
            }
          })
          this.closeAction()
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      })
    },
    closeAction () {
      this.$emit('close-action')
    }
  }
}
</script>
<style scoped lang="less">
  .form-layout {
    width: 80vw;
    @media (min-width: 600px) {
      width: 450px;
    }
  }
  .action-button {
    text-align: right;
    button {
      margin-right: 5px;
    }
  }
</style>
