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
  <MoldDialog
    class="userdata-reset-dialog"
    size="md"
    :title="$t(isAutoScaleProfile ? 'label.reset.user.data.on.autoscale.vm.group' : 'label.reset.user.data.on.vm')"
    :closable="!loadingData"
    :keyboard="!loadingData"
    @cancel="closeAction">
  <a-alert v-if="templateError" type="error" show-icon role="alert" :message="$t('message.request.failed')">
    <template #description><a-button size="small" @click="fetchTemplateData">{{ $t('label.refresh') }}</a-button></template>
  </a-alert>
  <a-spin :spinning="loadingData || templateLoading">
    <a-form
            :ref="formRef"
            :model="form"
            :rules="rules"
            layout="vertical"
            class="form"
            :class="{ 'autoscale-userdata-form': isAutoScaleProfile }"
            v-ctrl-enter="handleSubmit"
            @finish="handleSubmit">
      <div v-if="template && template.userdataid">
        <a-typography-text>
            <template v-if="isAutoScaleProfile">{{ $t('message.autoscale.userdata.template', { template: template.name, userdata: template.userdataname, policy: template.userdatapolicy }) }}</template>
            <template v-else>The template "{{ $t(this.template.name) }}" is linked to Userdata "{{ $t(this.template.userdataname) }}" with override policy "{{ $t(this.template.userdatapolicy) }}"</template>
        </a-typography-text><br/><br/>
        <div v-if="templateUserDataParams.length > 0 && !doUserdataOverride">
          <a-typography-text v-if="this.template && this.template.userdataid && templateUserDataParams.length > 0">
              <template v-if="isAutoScaleProfile">{{ $t('message.autoscale.userdata.variables') }}</template>
              <template v-else>Enter the values for the variables in userdata</template>
          </a-typography-text>
          <a-input-group>
            <a-table
              size="small"
              style="overflow-y: auto"
              :columns="userDataParamCols"
              :dataSource="templateUserDataParams"
              :pagination="false"
              :rowKey="record => record.key">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'value'">
                  <a-input v-model:value="templateUserDataValues[record.key]" />
                </template>
              </template>
            </a-table>
          </a-input-group>
        </div><br/><br/>
      </div>
      <div v-if="userdataDefaultOverridePolicy === 'ALLOWOVERRIDE' || userdataDefaultOverridePolicy === 'APPEND' || !userdataDefaultOverridePolicy">
        <span v-if="userdataDefaultOverridePolicy === 'ALLOWOVERRIDE'" >
          {{ $t('label.user.data.do.override') }}
          <a-switch v-model:checked="doUserdataOverride" style="margin-left: 10px"/>
        </span>
        <span v-if="userdataDefaultOverridePolicy === 'APPEND'">
          {{ $t('label.user.data.do.append') }}
          <a-switch v-model:checked="doUserdataAppend" style="margin-left: 10px"/>
        </span>
        <a-step>
          <template #description>
            <div v-if="doUserdataOverride || doUserdataAppend || !userdataDefaultOverridePolicy" style="margin-top: 15px">
              <a-card
                :tabList="userdataTabList"
                :activeTabKey="userdataTabKey"
                @tabChange="key => onUserdataTabChange(key, 'userdataTabKey')">
                <div v-if="userdataTabKey === 'userdataregistered'">
                  <a-step>
                    <template #description>
                      <div>
                        <user-data-selection
                          :scope="userDataOwner"
                          :value="form.userdataid"
                          :disabled="template.userdatapolicy === 'DENYOVERRIDE'"
                          :preFillContent="dataPreFill"
                          @select-user-data-item="updateUserData"
                          @ready="userDataSelectionReady = $event"
                        />
                        <div v-if="userDataParams.length > 0">
                          <a-input-group>
                            <a-table
                              size="small"
                              style="overflow-y: auto"
                              :columns="userDataParamCols"
                              :dataSource="userDataParams"
                              :pagination="false"
                              :rowKey="record => record.key">
                              <template #bodyCell="{ column, record }">
                                <template v-if="column.key === 'value'">
                                  <a-input v-model:value="userDataValues[record.key]" />
                                </template>
                              </template>
                            </a-table>
                          </a-input-group>
                        </div>
                      </div>
                    </template>
                  </a-step>
                </div>
                <div v-else>
                  <a-form-item name="userdata" ref="userdata" >
                    <a-textarea
                      :placeholder="isAutoScaleProfile ? $t('label.user.data') : 'Userdata'"
                      v-model:value="form.userdata">
                    </a-textarea>
                  </a-form-item>
                </div>
              </a-card>
            </div>
          </template>
        </a-step>
      </div>
    </a-form>
  </a-spin>
    <template #footer>
      <a-button :disabled="loadingData" @click="closeAction">{{ $t('label.cancel') }}</a-button>
      <a-button :loading="loadingData" :disabled="templateLoading || templateError || userDataSelectionBlocked" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
    </template>
  </MoldDialog>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { runAutoScaleMutation, autoScaleMutationDescription, autoScaleOwnerParams } from '@/utils/autoScaleEditing'
import { autoScaleTemplateById } from '@/utils/autoScaleUi'
import UserDataSelection from '@views/compute/wizard/UserDataSelection'
import MoldDialog from '@/components/view/MoldDialog'
import { applyUserDataSelection, userDataSelectionConsumer } from '@/utils/userDataSelection'

export default {
  name: 'ResetUserData',
  mixins: [userDataSelectionConsumer],
  components: {
    MoldDialog,
    UserDataSelection
  },
  props: {
    resource: {
      type: Object,
      required: true
    },
    preFillContent: {
      type: Object,
      default: () => ({})
    }
  },
  data () {
    return {
      loadingData: false,
      templateLoading: false,
      templateError: false,
      doUserdataOverride: false,
      doUserdataAppend: false,
      userdataTabList: [],
      userdataDefaultOverridePolicy: 'ALLOWOVERRIDE',
      userData: {},
      userDataParams: [],
      userDataParamCols: [
        {
          title: this.$t('label.key'),
          dataIndex: 'key'
        },
        {
          title: this.$t('label.value'),
          dataIndex: 'value',
          key: 'value'
        }
      ],
      userDataValues: {},
      templateUserDataCols: [
        {
          title: this.$t('label.user.data'),
          dataIndex: 'userdata'
        },
        {
          title: this.$t('label.user.data.policy'),
          dataIndex: 'userdataoverridepolicy'
        }
      ],
      templateUserDataParams: [],
      templateUserDataValues: {},
      template: {},
      userdataTabKey: 'userdataregistered',
      dataPreFill: {},
      userdata: {}
    }
  },
  computed: {
    blocksNavigation () { return this.loadingData },
    isUserAllowedToListUserDatas () { return Boolean(this.$store.getters.apis.listUserData) },
    isAutoScaleProfile () { return this.resource.resetUserDataApiName === 'updateAutoScaleVmProfile' }
  },
  created () {
    this.initForm()
    this.loadUserdataTabList()
    this.fetchTemplateData()
    this.dataPreFill = this.preFillContent && Object.keys(this.preFillContent).length > 0 ? this.preFillContent : {}
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({})
    },
    async fetchTemplateData () {
      this.templateLoading = true
      this.templateError = false
      try {
        const params = { ...autoScaleOwnerParams(this.resource), id: this.resource.templateid, isready: true, ignoreproject: true }
        const request = (command, args) => getAPI(command, args, { preserveOnFailure: true, timeout: 15000 })
        const json = await autoScaleTemplateById(request, { ...params, zoneid: this.resource.zoneid })
        const template = json.listtemplatesresponse?.template?.find(item => item.id === this.resource.templateid)
        if (!template) throw new Error('Template unavailable')
        this.template = template
        this.userdataDefaultOverridePolicy = template.userdatapolicy
        await this.updateTemplateLinkedUserData(template.userdataid)
      } catch (error) {
        this.templateError = true
      } finally {
        this.templateLoading = false
      }
    },
    async updateTemplateLinkedUserData (id) {
      this.templateUserDataParams = []
      if (!id || id === '0') return
      const json = await getAPI('listUserData', { ...autoScaleOwnerParams(this.resource), ignoreproject: true, id }, { preserveOnFailure: true, timeout: 15000 })
      const item = json?.listuserdataresponse?.userdata?.find(item => item.id === id)
      if (!item) throw new Error('Template user data unavailable')
      const keys = typeof item.params === 'string' ? item.params.split(',').map(key => key.trim()).filter(Boolean) : []
      this.templateUserDataParams = keys.map((key, index) => ({ id: index, key }))
    },
    loadUserdataTabList () {
      this.userdataTabList = [{
        key: 'userdataregistered',
        tab: this.$t(this.isAutoScaleProfile ? 'label.autoscale.userdata.registered' : 'label.user.data.registered')
      },
      {
        key: 'userdatatext',
        tab: this.$t(this.isAutoScaleProfile ? 'label.autoscale.userdata.manual' : 'label.user.data.text')
      }]
    },
    onUserdataTabChange (key, type) {
      this[type] = key
      this.userDataParams = []
      if (this.isAutoScaleProfile && type === 'userdataTabKey' && key === 'userdataregistered') {
        this.form.userdataid = undefined
        this.userDataValues = {}
      }
    },
    updateUserData (id, item) {
      applyUserDataSelection(this, id, item)
    },
    handleSubmit () {
      if (this.loadingData || this.templateLoading || this.templateError || this.userDataSelectionBlocked) return
      const values = toRaw(this.form)
      this.loadingData = true
      const params = {
      }
      if ((!this.isAutoScaleProfile || this.userdataTabKey === 'userdatatext') && values.userdata && values.userdata.length > 0) {
        params.userdata = this.$toBase64AndURIEncoded(values.userdata)
      }
      if ((!this.isAutoScaleProfile || this.userdataTabKey === 'userdataregistered') && values.userdataid) {
        params.userdataid = values.userdataid
      }
      var idx = 0
      if (this.templateUserDataValues) {
        for (const [key, value] of Object.entries(this.templateUserDataValues)) {
          params['userdatadetails[' + idx + '].' + `${key}`] = value
          idx++
        }
      }
      if ((!this.isAutoScaleProfile || params.userdataid) && this.userDataValues) {
        for (const [key, value] of Object.entries(this.userDataValues)) {
          params['userdatadetails[' + idx + '].' + `${key}`] = value
          idx++
        }
      }
      params.id = this.resource.resetUserDataResourceId ? this.resource.resetUserDataResourceId : this.resource.id

      const resetUserDataApiName = this.resource.resetUserDataApiName ? this.resource.resetUserDataApiName : 'resetUserDataForVirtualMachine'

      if (resetUserDataApiName === 'updateAutoScaleVmProfile' && !params.userdataid && !params.userdata) params.userdata = ''
      const update = resetUserDataApiName === 'updateAutoScaleVmProfile'
        ? runAutoScaleMutation(postAPI, getAPI, resetUserDataApiName, params)
        : postAPI(resetUserDataApiName, params)
      return update.then(json => {
        this.$message.success({
          content: `${this.$t('label.action.user.data.reset')} - ${this.$t('label.success')}`,
          duration: 2
        })
        this.$emit('refresh-data')
        this.loadingData = false
        this.closeAction()
      }).catch(error => {
        if (resetUserDataApiName === 'updateAutoScaleVmProfile') {
          this.$notification.error({ message: this.$t('label.error'), description: autoScaleMutationDescription(error, this.$t) })
        } else this.$notifyError(error)
      }).finally(() => {
        this.loadingData = false
      })
    },
    closeAction () {
      if (this.loadingData) return
      this.$emit('close-action')
    }
  }
}
</script>

<style scoped lang="scss">
.form {
  width: 100%;
  min-width: 0;
}
.form {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  :deep(.ant-steps-item-description), :deep(.ant-steps-item-content) { width: 100%; min-width: 0; max-width: 100%; }
  :deep(.ant-card), :deep(.ant-card-body), :deep(.ant-table-wrapper) { min-width: 0; max-width: 100%; }
  :deep(.ant-input-search) { width: min(320px, 100%) !important; }
  :deep(.ant-table-cell) { overflow-wrap: anywhere; }
  :deep(.ant-table) { table-layout: fixed; }
}
</style>
