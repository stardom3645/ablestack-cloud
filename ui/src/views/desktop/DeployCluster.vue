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
        <a-form-item>
          <span slot="label">
            {{ $t('label.name') }}
            <a-tooltip :title="$t('label.name')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-input
            v-decorator="['name', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('label.name')" />
        </a-form-item>
        <a-form-item>
          <span slot="label">
            {{ $t('label.description') }}
            <a-tooltip :title="$t('label.description')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-input
            v-decorator="['description', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('label.description')" />
        </a-form-item>
        <a-form-item>
          <span slot="label">
            {{ $t('label.addomainname') }}
            <a-tooltip :title="$t('label.addomainname')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-input
            v-decorator="['addomainname', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('placeholder.addomainname')" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item>
              <span slot="label">
                {{ $t('label.password') }}
                <a-tooltip :title="$t('placeholder.password')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
              <a-input-password
                v-decorator="['password', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.password')"/>
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item>
              <span slot="label">
                {{ $t('label.confirmpassword') }}
                <a-tooltip :title="$t('placeholder.confirmpassword')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
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
        </a-row>
        <a-form-item>
          <span slot="label">
            {{ $t('label.desktop.controller.template.version') }}
            <a-tooltip :title="$t('placeholder.desktop.controller.template.version')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-select
            showSearch
            v-decorator="['timezone', {
              rules: [{ required: true, message: $t('message.error.required.input') }]
            }]"
            :loading="timeZoneLoading"
            :placeholder="$t('placeholder.desktop.controller.template.version')">
            <a-select-option v-for="opt in timeZoneMap" :key="opt.id">
              {{ opt.name || opt.description }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <span slot="label">
            {{ $t('label.compute.offerings') }}
            <a-tooltip :title="$t('placeholder.compute.offering')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-select
            id="offering-selection"
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
        <a-form-item>
          <span slot="label">
            {{ $t('label.access.type') }}
            <a-tooltip :title="$t('placeholder.accesstype')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-radio-group
            v-decorator="['accesstype', {
              initialValue: this.accessType,
              rules: [{ required: true, message: $t('message.error.select') }]
            }]"
            buttonStyle="solid"
            @change="selected => { this.handleAccessTypeChange(selected.target.value) }">
            <a-radio-button value="internal">
              {{ $t('label.access.internal') }}
            </a-radio-button>
            <a-radio-button value="external">
              {{ $t('label.access.external') }}
            </a-radio-button>
            <a-radio-button value="mixed">
              {{ $t('label.access.mixed') }}
            </a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="this.isAdminOrDomainAdmin()">
          <span slot="label">
            {{ $t('label.network') }}
            <a-tooltip :title="$t('placeholder.network')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-select
            :loading="domainLoading"
            v-decorator="['domainid', {
              initialValue: selectedDomain,
              rules: [{ required: true, message: $t('message.error.select') }] }]"
            :placeholder="$t('placeholder.network')">
            <a-select-option v-for="domain in domainsList" :key="domain.id">
              {{ domain.path || domain.name || domain.description }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item v-if="this.accessType=='internal'">
              <span slot="label">
                {{ $t('label.gateway') }}
                <a-tooltip :title="$t('label.gateway')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
              <a-input
                v-decorator="['account', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('label.gateway')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item v-if="this.accessType=='internal'">
              <span slot="label">
                {{ $t('label.netmask') }}
                <a-tooltip :title="$t('placeholder.netmask')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
              <a-input
                v-decorator="['account', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.netmask')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item v-if="this.accessType=='internal'">
              <span slot="label">
                {{ $t('label.startip') }}
                <a-tooltip :title="$t('placeholder.startip')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
              <a-input
                v-decorator="['startip', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.startip')" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item v-if="this.accessType=='internal'">
              <span slot="label">
                {{ $t('label.endip') }}
                <a-tooltip :title="$t('placeholder.endip')">
                  <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                </a-tooltip>
              </span>
              <a-input
                v-decorator="['endip', {
                  rules: [{ required: true, message: $t('message.error.required.input') }]
                }]"
                :placeholder="$t('placeholder.endip')" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item>
          <span slot="label">
            {{ $t('label.worksvmip') }}
            <a-tooltip :title="$t('placeholder.worksvmip')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-input
            v-decorator="['email', {
              rules: [{ message: $t('message.error.required.input') }]
            }]"
            :placeholder="$t('placeholder.worksvmip')" />
        </a-form-item>
        <a-form-item>
          <span slot="label">
            {{ $t('label.dcvmip') }}
            <a-tooltip :title="$t('placeholder.dcvmip')">
              <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
            </a-tooltip>
          </span>
          <a-input
            v-decorator="['email', {
              rules: [{ message: $t('message.error.required.input') }]
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
import { timeZone } from '@/utils/timezone'
import debounce from 'lodash/debounce'

export default {
  name: 'AddAccountForm',
  data () {
    this.fetchTimeZone = debounce(this.fetchTimeZone, 800)
    return {
      loading: false,
      domainLoading: false,
      domainsList: [],
      selectedDomain: '',
      roleLoading: false,
      roles: [],
      accessType: 'internal',
      selectedRole: '',
      timeZoneLoading: false,
      timeZoneMap: [],
      externalEnabled: false,
      serviceOfferings: [],
      serviceOfferingLoading: false
    }
  },
  beforeCreate () {
    this.form = this.$form.createForm(this)
    this.apiConfig = this.$store.getters.apis.createAccount || {}
    this.apiParams = {}
    if (this.apiConfig.params) {
      this.apiConfig.params.forEach(param => {
        this.apiParams[param.name] = param
      })
    }
    this.apiConfig = this.$store.getters.apis.authorizeSamlSso || {}
    if (this.apiConfig.params) {
      this.apiConfig.params.forEach(param => {
        this.apiParams[param.name] = param
      })
    }
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      this.fetchDomains()
      this.fetchRoles()
      this.fetchTimeZone()
      this.fetchServiceOfferingData()
      if ('listIdps' in this.$store.getters.apis) {
        this.fetchIdps()
      }
    },
    isAdminOrDomainAdmin () {
      return ['Admin', 'DomainAdmin'].includes(this.$store.getters.userInfo.roletype)
    },
    isDomainAdmin () {
      return this.$store.getters.userInfo.roletype === 'DomainAdmin'
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    handleAccessTypeChange (pvlan) {
      this.accessType = pvlan
    },
    validateConfirmPassword (rule, value, callback) {
      if (!value || value.length === 0) {
        callback()
      } else if (rule.field === 'confirmpassword') {
        const form = this.form
        const messageConfirm = this.$t('error.password.not.match')
        const passwordVal = form.getFieldValue('password')
        if (passwordVal && passwordVal !== value) {
          callback(messageConfirm)
        } else {
          callback()
        }
      } else {
        callback()
      }
    },
    fetchDomains () {
      this.domainLoading = true
      api('listDomains', {
        listAll: true,
        details: 'min'
      }).then(response => {
        this.domainsList = response.listdomainsresponse.domain || []
        this.selectedDomain = this.domainsList[0].id || ''
      }).catch(error => {
        this.$notification.error({
          message: `${this.$t('label.error')} ${error.response.status}`,
          description: error.response.data.errorresponse.errortext
        })
      }).finally(() => {
        this.domainLoading = false
      })
    },
    fetchRoles () {
      this.roleLoading = true
      api('listRoles').then(response => {
        this.roles = response.listrolesresponse.role || []
        this.selectedRole = this.roles[0].id
        if (this.isDomainAdmin()) {
          const userRole = this.roles.filter(role => role.type === 'User')
          if (userRole.length > 0) {
            this.selectedRole = userRole[0].id
          }
        }
      }).finally(() => {
        this.roleLoading = false
      })
    },
    fetchTimeZone (value) {
      this.timeZoneMap = []
      this.timeZoneLoading = true

      timeZone(value).then(json => {
        this.timeZoneMap = json
        this.timeZoneLoading = false
      })
    },
    fetchIdps () {
      this.idpLoading = true
      api('listIdps').then(response => {
        this.idps = response.listidpsresponse.idp || []
        this.selectedIdp = this.idps[0].id || ''
      }).finally(() => {
        this.idpLoading = false
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
            if (items[i].iscustomized === false &&
                items[i].cpunumber >= this.minCpu && items[i].memory >= this.minMemory) {
              this.serviceOfferings.push(items[i])
            }
          }
        }
      }).finally(() => {
        this.serviceOfferingLoading = false
        if (this.arrayHasItems(this.serviceOfferings)) {
          for (var i = 0; i < this.serviceOfferings.length; i++) {
            if (this.serviceOfferings[i].id === this.resource.serviceofferingid) {
              this.form.setFieldsValue({
                serviceofferingid: i
              })
              break
            }
          }
        }
      })
    },
    handleSubmit (e) {
      e.preventDefault()
      this.form.validateFields((err, values) => {
        if (err) {
          return
        }
        this.loading = true
        const params = {
          roleid: values.roleid,
          username: values.username,
          password: values.password,
          email: values.email,
          domainid: values.domainid
        }
        if (this.isValidValueForKey(values, 'account') && values.account.length > 0) {
          params.account = values.account
        }
        if (this.isValidValueForKey(values, 'timezone') && values.timezone.length > 0) {
          params.timezone = values.timezone
        }
        if (this.isValidValueForKey(values, 'serviceofferingid') && this.arrayHasItems(this.serviceOfferings)) {
          params.serviceofferingid = this.serviceOfferings[values.serviceofferingid].id
        }
        api('createAccount', {}, 'POST', params).then(response => {
          this.$emit('refresh-data')
          this.$notification.success({
            message: this.$t('label.create.account'),
            description: `${this.$t('message.success.create.account')} ${params.username}`
          })
          this.closeAction()
        }).catch(error => {
          this.$notification.error({
            message: this.$t('message.request.failed'),
            description: (error.response && error.response.headers && error.response.headers['x-description']) || error.message,
            duration: 0
          })
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
