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
  <div class="autoscale-tab">
    <a-alert v-if="affectedGroups.length > 1" type="warning" show-icon :message="$t('message.autoscale.edit.shared', { groups: affectedGroups.join(', ') })" />
    <a-alert v-if="mutationError" type="error" show-icon :message="mutationError" />
    <a-alert v-if="retainedResources" type="warning" show-icon :message="$t('message.autoscale.edit.retained', { ids: retainedResources })" />
    <p v-if="listRefreshFailed" role="status">{{ $t('message.list.refresh.stale') }}</p>
    <div>
      <a-alert type="info" v-if="(resource.state !== 'DISABLED' || resource.operationinprogress)">
        <template #message>
          <div
            v-html="$t(resource.operationinprogress ? 'message.autoscale.operation.wait' : 'message.autoscale.policies.update')" />
        </template>
      </a-alert>

      <a-divider/>
      <div class="form">
        <strong>{{ $t('label.scaleup.policy') }} &nbsp;&nbsp;&nbsp;</strong>
        <a-select
          style="width: 320px"
          v-model:value="selectedPolicyId"
          @change="switchPolicy()"
          :placeholder="$t('label.scaleup.policy')"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => {
                      return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
                    }" >
          <a-select-option
            v-for="(scalepolicy, index) in this.policies"
            :value="scalepolicy.id"
            :key="index"
            :label="scalepolicy.displaytext || scalepolicy.name">
            {{ scalepolicy.name || scalepolicy.id }}
          </a-select-option>
        </a-select>
        <a-button style="margin-left: 10px" ref="submit" type="primary" @click="addPolicyModalVisible = true" :disabled="saving || !('createAutoScalePolicy' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)">
          <template #icon><plus-outlined /></template>
          {{ $t('label.add.policy') }}
        </a-button>
        <a-popconfirm
          :title="$t('label.remove.policy') + '?'"
          @confirm="removeScalePolicyFromGroup"
          :okText="$t('label.yes')"
          :cancelText="$t('label.no')"
        >
          <a-button style="margin-left: 10px" ref="submit" type="primary" :danger="true" :disabled="saving || policies.length < 2 || !('updateAutoScaleVmGroup' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)">
            <template #icon><delete-outlined /></template>
            {{ $t('label.remove.policy') }}
          </a-button>
        </a-popconfirm>

      </div>

      <a-divider/>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.name')" :tooltip="(createAutoScalePolicyApiParams.name || {}).description"/>
          </div>
          <a-input v-model:value="policy.name" :disabled="!this.isEditable"></a-input>
        </div>
        <div class="form__item">
          <div class="form__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.duration')" :tooltip="(createAutoScalePolicyApiParams.duration || {}).description"/>
          </div>
          <a-input v-model:value="policy.duration" type="number" :disabled="!this.isEditable"></a-input>
        </div>
        <div class="form__item">
          <div class="form__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.autoscale.cooldown.seconds')" :tooltip="(createAutoScalePolicyApiParams.quiettime || {}).description"/>
          </div>
          <a-input v-model:value="policy.quiettime" type="number" :disabled="!this.isEditable"></a-input>
        </div>
        <div class="form__item">
          <div class="form__label">{{ $t('label.action') }}</div>
          <a-button ref="submit" :disabled="saving || !('updateAutoScalePolicy' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)" type="primary" @click="updateAutoScalePolicy(null, null)">
            <template #icon><edit-outlined /></template>
            {{ isEditOrApply() }}
          </a-button>
        </div>
      </div>
    </div>

    <a-divider/>
    <div class="title">
      <div class="form__label">
        <tooltip-label :title="$t('label.conditions')" :tooltip="(createAutoScalePolicyApiParams.conditionids || {}).description"/>
      </div>
    </div>
    <a-table
      size="small"
      style="overflow-y: auto"
      :loading="loading"
      :columns="columns"
      :dataSource="policy.conditions"
      :pagination="false"
      :rowKey="record => record.id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'countername'">{{ counterLabel(record.countername) }}</template>
        <template v-if="column.key === 'relationaloperator'">
        {{ getOperator(record.relationaloperator) }}
        </template>
        <template v-if="column.key === 'actions'">
          <tooltip-button
            :tooltip="$t('label.edit')"
            :disabled="saving || !('updateCondition' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)"
            icon="edit-outlined"
            @onClick="() => openUpdateConditionModal(record)" />
          <tooltip-button
            :tooltip="$t('label.delete')"
            :disabled="saving || (policy.conditions || []).length < 2 || !('updateAutoScalePolicy' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)"
            type="primary"
            :danger="true"
            icon="delete-outlined"
            @onClick="deleteConditionFromAutoScalePolicy(record.id)" />
        </template>
      </template>
    </a-table>

    <div>
      <a-divider/>
      <div class="form" v-ctrl-enter="addConditionToPolicy">
        <div class="form__item" ref="newConditionCounterId">
          <div class="form__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.counter')" :tooltip="$t('label.counter.name')"/>
          </div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            v-focus="true"
            v-model:value="newCondition.counterid">
            <a-select-option
              v-for="(counter, index) in countersList"
              :value="counter.id"
              :key="index"
              :label="counterLabel(counter.name)">
              {{ counterLabel(counter.name) }}
            </a-select-option>
          </a-select>
          <span class="error-text">{{ $t('label.required') }}</span>
        </div>
        <div class="form__item" ref="newConditionRelationalOperator">
          <div class="form__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.relationaloperator')" :tooltip="(createConditionApiParams.relationaloperator || {}).description"/>
          </div>
          <a-select
            v-model:value="newCondition.relationaloperator"
            style="width: 100%;"
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="operator in operators" :key="operator" :value="operator">{{ getOperator(operator) }}</a-select-option>
          </a-select>
          <span class="error-text">{{ $t('label.required') }}</span>
        </div>
        <div class="form__item" ref="newConditionThreshold">
          <div class="form__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.threshold')" :tooltip="$t('label.threshold.description')"/>
          </div>
          <a-input v-model:value="newCondition.threshold" type="number"></a-input>
          <span class="error-text">{{ $t('label.required') }}</span>
        </div>
        <div class="form__item">
          <div class="form__label">{{ $t('label.action') }}</div>
          <a-button ref="submit" :disabled="saving || !('createCondition' in $store.getters.apis) || (resource.state !== 'DISABLED' || resource.operationinprogress)" type="primary" @click="addConditionToPolicy">
            <template #icon><plus-outlined /></template>
            {{ $t('label.autoscale.condition.add') }}
          </a-button>
        </div>
      </div>
    </div>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
      :title="$t('label.update.condition')"
      :visible="updateConditionModalVisible"
      :afterClose="closeModal"
      :maskClosable="false"
      :closable="true"
      @cancel="updateConditionModalVisible = false">
      <a-alert v-if="affectedConditionGroups.length > 1" type="warning" show-icon :message="$t('message.autoscale.edit.shared', { groups: affectedConditionGroups.join(', ') })" />
      <span v-show="updateConditionModalLoading" class="modal-loading">
        <loading-outlined />
      </span>

      <div class="update-condition" v-if="selectedCondition" v-ctrl-enter="handleSubmitUpdateConditionForm">
        <div class="update-condition__item">
          <p class="update-condition__label"><span class="form__required">*</span>{{ $t('label.counter') }}</p>
          {{ counterLabel(updateConditionDetails.countername) }}
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.relationaloperator')" :tooltip="(createConditionApiParams.relationaloperator || {}).description"/>
          </div>
          <a-select
            v-model:value="updateConditionDetails.relationaloperator"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="operator in operators" :key="operator" :value="operator">{{ getOperator(operator) }}</a-select-option>
          </a-select>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.threshold')" :tooltip="$t('label.threshold.description')"/>
          </div>
          <a-input v-focus="true" v-model:value="updateConditionDetails.threshold" type="number" />
        </div>

      </div>
      <template #footer>
        <div :span="24" class="action-button">
          <a-button @click="() => updateConditionModalVisible = false">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" @click="handleSubmitUpdateConditionForm">{{ $t('label.ok') }}</a-button>
        </div>
      </template>
    </a-modal>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
      :title="$t('label.add.policy')"
      :visible="addPolicyModalVisible"
      :afterClose="closeModal"
      :maskClosable="false"
      :closable="true"
      @cancel="addPolicyModalVisible = false">

      <div class="update-condition">
        <div class="update-condition__item">
          <div class="update-condition__label">
            <tooltip-label :title="$t('label.name')" :tooltip="(createAutoScalePolicyApiParams.name || {}).description"/>
          </div>
          <a-input v-model:value="newPolicy.name" v-focus="true"></a-input>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.duration')" :tooltip="(createAutoScalePolicyApiParams.duration || {}).description"/>
          </div>
          <a-input v-model:value="newPolicy.duration" type="number"></a-input>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.autoscale.cooldown.seconds')" :tooltip="(createAutoScalePolicyApiParams.quiettime || {}).description"/>
          </div>
          <a-input v-model:value="newPolicy.quiettime" type="number"></a-input>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.counter')" :tooltip="$t('label.counter.name')"/>
          </div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            v-model:value="newPolicy.counterid">
            <a-select-option
              v-for="(counter, index) in countersList"
              :value="counter.id"
              :key="index"
              :label="counterLabel(counter.name)">
              {{ counterLabel(counter.name) }}
            </a-select-option>
          </a-select>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.relationaloperator')" :tooltip="(createConditionApiParams.relationaloperator || {}).description"/>
          </div>
          <a-select
            v-model:value="newPolicy.relationaloperator"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="operator in operators" :key="operator" :value="operator">{{ getOperator(operator) }}</a-select-option>
          </a-select>
        </div>
        <div class="update-condition__item">
          <div class="update-condition__label">
            <span class="form__required">*</span>
            <tooltip-label :title="$t('label.threshold')" :tooltip="$t('label.threshold.description')"/>
          </div>
          <a-input v-model:value="newPolicy.threshold" type="number"></a-input>
        </div>
      </div>

      <template #footer>
        <div :span="24" class="action-button">
        <a-button :loading="loading" @click="closeModal">{{ $t('label.cancel') }}</a-button>
        <a-button :loading="loading" ref="submit" type="primary" @click="addNewScalePolicyToGroup">{{ $t('label.ok') }}</a-button>
      </div>
      </template>
    </a-modal>

  </div>
</template>

<script>
import '@/style/components/view/autoscale.less'
import { autoScaleOperators, validAutoScalePolicyTiming, autoScaleCounterLabel } from '@/utils/autoScalePolicyEvaluation'
import { listRefreshMixin } from '@/utils/listRefreshMixin'
import { autoScaleInteger } from '@/utils/autoscaleValidation'
import { runAutoScaleMutation, autoScaleOwnerParams, autoScaleAffectedGroups, autoScaleMutationDescription, createAndAttachAutoScale } from '@/utils/autoScaleEditing'

import { getAPI, postAPI } from '@/api'
import Status from '@/components/widgets/Status'
import TooltipButton from '@/components/widgets/TooltipButton'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  mixins: [listRefreshMixin(['fetchData'])],
  name: 'conditionsTab',
  components: {
    Status,
    TooltipButton,
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      operators: autoScaleOperators,
      filterColumns: ['Actions'],
      loading: true,
      saving: false,
      affectedGroups: [],
      affectedConditionGroups: [],
      mutationError: '',
      retainedResources: '',
      policies: [],
      isEditable: false,
      policy: {
        duration: null,
        quiettime: null,
        conditions: []
      },
      selectedPolicyId: null,
      newCondition: {
        counterid: null,
        relationaloperator: 'GT',
        threshold: null
      },
      addPolicyModalVisible: null,
      newPolicy: {
        duration: null,
        quiettime: 300,
        counterid: null,
        relationaloperator: 'GT',
        threshold: null
      },
      selectedCondition: null,
      updateConditionModalVisible: false,
      updateConditionModalLoading: false,
      updateConditionDetails: {
        counter: '',
        relationaloperator: '',
        threshold: ''
      },
      countersList: [],
      columns: [
        {
          title: this.$t('label.counter'),
          dataIndex: 'countername'
        },
        {
          title: this.$t('label.relationaloperator'),
          key: 'relationaloperator'
        },
        {
          title: this.$t('label.threshold'),
          dataIndex: 'threshold'
        },
        {
          title: this.$t('label.actions'),
          key: 'actions'
        }
      ]
    }
  },
  beforeCreate () {
    this.createConditionApi = this.$store.getters.apis.createCondition || {}
    this.createConditionApiParams = {}
    ;(this.createConditionApi.params || []).forEach(param => {
      this.createConditionApiParams[param.name] = param
    })
    this.createAutoScalePolicyApi = this.$store.getters.apis.createAutoScalePolicy || {}
    this.createAutoScalePolicyApiParams = {}
    ;(this.createAutoScalePolicyApi.params || []).forEach(param => {
      this.createAutoScalePolicyApiParams[param.name] = param
    })
  },
  created () {
    this.fetchInitData()
  },
  watch: {
    resource: {
      deep: true,
      handler (newItem) {
        if (!newItem || !newItem.id) {
          return
        }
        this.fetchData()
      }
    }
  },
  inject: ['parentFetchData'],
  methods: {
    counterLabel (name) { return autoScaleCounterLabel(name, this.$t) },
    async fetchInitData () {
      this.loading = true
      try {
        const response = await getAPI('listAutoScaleVmGroups', { ...autoScaleOwnerParams(this.resource), listall: true, id: this.resource.id })
        const group = response.listautoscalevmgroupsresponse?.autoscalevmgroup?.[0]
        this.policies = group?.scaleuppolicies || []
        if (!this.policies.some(policy => policy.id === this.selectedPolicyId)) this.selectedPolicyId = this.policies[0]?.id
        this.switchPolicy()
        let provider = this.resource.lbprovider
        if (!provider) {
          const lb = await getAPI('listLoadBalancerRules', { ...autoScaleOwnerParams(this.resource), listall: true, id: group?.lbruleid })
          const networkId = lb.listloadbalancerrulesresponse?.loadbalancerrule?.[0]?.networkid
          const network = await getAPI('listNetworks', { ...autoScaleOwnerParams(this.resource), listall: true, id: networkId })
          provider = network.listnetworksresponse?.network?.[0]?.service?.find(service => service.name === 'Lb')?.provider?.[0]?.name
        }
        const counters = await getAPI('listCounters', { listall: true, provider })
        this.countersList = counters.counterresponse?.counter || []
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.loading = false
      }
    },
    fetchData () {
      const listRequest = this.listRequestToken('fetchData')
      this.loading = !listRequest.loaded
      if (!this.selectedPolicyId) return this.fetchInitData()
      return getAPI('listAutoScalePolicies', {
        ...autoScaleOwnerParams(this.resource),
        listAll: true,
        id: this.selectedPolicyId
      }).then(response => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return

        this.policy = response.listautoscalepoliciesresponse?.autoscalepolicy?.[0] || { conditions: [] }
      }).finally(() => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return

        this.loading = false
      }).catch(error => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true
        this.listRefreshFailed = true
        if (!listRequest.loaded) this.$notifyError(error)
      }).finally(() => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        this.loading = false
      })
    },
    switchPolicy () {
      this.policy = this.policies.find(policy => policy.id === this.selectedPolicyId) || { conditions: [] }
      this.isEditable = false
      if (this.selectedPolicyId) {
        autoScaleAffectedGroups(getAPI, this.resource, { policyId: this.selectedPolicyId })
          .then(groups => { this.affectedGroups = groups }).catch(error => this.$notifyError(error))
      }
    },
    isEditOrApply () {
      if (this.isEditable) return this.$t('label.apply')
      else return this.$t('label.edit')
    },
    handleCancel () {
      this.parentFetchData()
    },
    isNumber (value) {
      if (value && (isNaN(value) || value < 0)) {
        return false
      }
      return true
    },
    isPositiveNumber (value) {
      if (value && (isNaN(value) || value <= 0)) {
        return false
      }
      return true
    },
    getOperator (val) {
      if (val === 'GT' || val === 'gt') return this.$t('label.operator.greater')
      if (val === 'GE' || val === 'ge') return this.$t('label.operator.greater.or.equal')
      if (val === 'LT' || val === 'lt') return this.$t('label.operator.less')
      if (val === 'LE' || val === 'le') return this.$t('label.operator.less.or.equal')
      if (val === 'EQ' || val === 'eq') return this.$t('label.operator.equal')
      return val
    },
    mutate (command, params) { return runAutoScaleMutation(postAPI, getAPI, command, params) },
    recordRetained (resources) { this.retainedResources = Object.entries(resources).filter(([, id]) => id).map(([type, id]) => `${type}: ${id}`).join(', ') },
    async saveEdit (action) {
      if (this.saving) return
      this.saving = true
      this.mutationError = ''
      try {
        await action()
        await this.fetchInitData()
        this.closeModal()
        this.$message.success(this.$t('message.autoscale.edit.saved'))
      } catch (error) {
        this.mutationError = autoScaleMutationDescription(error, this.$t)
        this.$notification.error({ message: this.$t('label.error'), description: this.mutationError })
      } finally {
        this.saving = false
        this.updateConditionModalLoading = false
      }
    },
    addCondition (params) { return this.mutate('createCondition', params).then(result => result.condition) },
    deleteCondition (id) { return this.mutate('deleteCondition', { id }) },
    openUpdateConditionModal (condition) {
      this.selectedCondition = condition
      this.updateConditionModalVisible = true
      this.updateConditionDetails = { countername: condition.countername, relationaloperator: condition.relationaloperator, threshold: condition.threshold }
      this.affectedConditionGroups = []
      autoScaleAffectedGroups(getAPI, this.resource, { conditionId: condition.id })
        .then(groups => { this.affectedConditionGroups = groups }).catch(error => this.$notifyError(error))
    },
    handleSubmitUpdateConditionForm () {
      const threshold = autoScaleInteger(this.updateConditionDetails.threshold, 0)
      if (threshold === null) {
        this.$message.error(this.$t('message.validate.number'))
        return
      }
      return this.saveEdit(() => this.mutate('updateCondition', {
        id: this.selectedCondition.id,
        relationaloperator: this.updateConditionDetails.relationaloperator,
        threshold
      }))
    },
    deleteConditionFromAutoScalePolicy (conditionId) {
      if ((this.policy.conditions || []).length < 2) {
        this.$message.error(this.$t('message.autoscale.policy.last.condition'))
        return
      }
      return this.updateAutoScalePolicy(null, conditionId)
    },
    addConditionToPolicy () {
      if (!this.newCondition.counterid || autoScaleInteger(this.newCondition.threshold, 0) === null) {
        this.$message.error(this.$t('message.please.enter.valid.value'))
        return
      }
      return this.saveEdit(() => createAndAttachAutoScale({
        createCondition: () => this.addCondition({ ...autoScaleOwnerParams(this.resource), ...this.newCondition }),
        attach: condition => this.mutate('updateAutoScalePolicy', { id: this.policy.id, conditionids: [...this.policy.conditions.map(item => item.id), condition.id].join(',') }),
        removeCondition: this.deleteCondition,
        retained: this.recordRetained
      }))
    },
    addNewScalePolicyToGroup () {
      if (!this.newPolicy.counterid || autoScaleInteger(this.newPolicy.threshold, 0) === null ||
          !validAutoScalePolicyTiming(this.newPolicy, this.resource.interval)) {
        this.$message.error(this.$t('message.autoscale.policy.time.invalid', { interval: this.resource.interval || 1 }))
        return
      }
      return this.saveEdit(() => createAndAttachAutoScale({
        createCondition: () => this.addCondition({
          ...autoScaleOwnerParams(this.resource),
          counterid: this.newPolicy.counterid,
          relationaloperator: this.newPolicy.relationaloperator,
          threshold: this.newPolicy.threshold
        }),
        createPolicy: condition => this.mutate('createAutoScalePolicy', {
          ...autoScaleOwnerParams(this.resource),
          name: this.newPolicy.name,
          conditionids: condition.id,
          duration: this.newPolicy.duration,
          quiettime: this.newPolicy.quiettime,
          action: 'ScaleUp'
        }).then(result => result.autoscalepolicy),
        attach: policy => this.mutate('updateAutoScaleVmGroup', { id: this.resource.id, scaleuppolicyids: [...this.policies.map(item => item.id), policy.id].join(',') }),
        removePolicy: id => this.mutate('deleteAutoScalePolicy', { id }),
        removeCondition: this.deleteCondition,
        retained: this.recordRetained
      }))
    },
    removeScalePolicyFromGroup () {
      if (this.policies.length < 2) {
        this.$message.error(this.$t('message.autoscale.policy.last.policy'))
        return
      }
      return this.saveEdit(() => this.mutate('updateAutoScaleVmGroup', {
        id: this.resource.id,
        scaleuppolicyids: this.policies.filter(policy => policy.id !== this.selectedPolicyId).map(policy => policy.id).join(',')
      }))
    },
    updateAutoScalePolicy (conditionIdToAdd, conditionIdToRemove) {
      if (!conditionIdToAdd && !conditionIdToRemove && !this.isEditable) { this.isEditable = true; return }
      if (!validAutoScalePolicyTiming(this.policy, this.resource.interval)) {
        this.$message.error(this.$t('message.autoscale.policy.time.invalid', { interval: this.resource.interval || 1 }))
        return
      }
      const ids = this.policy.conditions.filter(condition => condition.id !== conditionIdToRemove).map(condition => condition.id)
      if (conditionIdToAdd) ids.push(conditionIdToAdd)
      if (!ids.length) { this.$message.error(this.$t('message.autoscale.policy.last.condition')); return }
      return this.saveEdit(() => this.mutate('updateAutoScalePolicy', {
        id: this.policy.id,
        name: this.policy.name,
        duration: this.policy.duration,
        quiettime: this.policy.quiettime,
        conditionids: ids.join(',')
      }))
    },
    closeModal () {
      this.updateConditionModalVisible = false
      this.updateConditionModalLoading = false
      this.addPolicyModalVisible = false
      this.isEditable = false
    },
    capitalise (val) {
      return val.toUpperCase()
    }
  }
}
</script>

<style scoped lang="scss">
.condition {

  &-container {
    display: flex;
    width: 100%;
    flex-wrap: wrap;
    margin-right: -20px;
    margin-bottom: -10px;
  }

  &__item {
    padding-right: 20px;
    margin-bottom: 20px;

    @media (min-width: 760px) {
      flex: 1;
    }

  }

  &__title {
    font-weight: bold;
  }

}

.title {
  margin-bottom: 5px;
  font-weight: bold;
}

.add-btn {
  width: 100%;
  padding-top: 15px;
  padding-bottom: 15px;
  height: auto;
}

.add-actions {
  display: flex;
  justify-content: flex-end;
  margin-right: -20px;
  margin-bottom: 20px;

  @media (min-width: 760px) {
    margin-top: 20px;
  }

  button {
    margin-right: 20px;
  }

}

.update-condition {

  .ant-select {
    width: 100%;
  }

  &__item {
    margin-bottom: 10px;
  }

  &__label {
    margin-bottom: 5px;
    font-weight: bold;
  }

}

.form {
  display: flex;
  margin-right: -20px;
  margin-bottom: 20px;
  flex-direction: column;
  align-items: flex-start;

  @media (min-width: 760px) {
    flex-direction: row;
  }

  &__item {
    display: flex;
    flex-direction: column;
    flex: 1;
    padding-right: 20px;
    margin-bottom: 20px;

    @media (min-width: 760px) {
      margin-bottom: 0;
    }

    input,
    .ant-select {
      margin-top: auto;
    }

  }

  &__label {
    font-weight: bold;
  }

  &__required {
    margin-right: 5px;
    color: red;
  }

  .error-text {
    display: none;
    color: red;
    font-size: 0.8rem;
  }

  .error {

    input {
      border-color: red;
    }

    .error-text {
      display: block;
    }

  }

}
.pagination {
  margin-top: 20px;
}
</style>
