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
  <div class="form">
    <a-steps
      ref="zoneStep"
      labelPlacement="vertical"
      size="small"
      :current="currentStep">
      <a-step
        v-for="(item, index) in zoneSteps"
        :key="item.title"
        :title="$t(item.title)"
        :ref="`step${index}`">
      </a-step>
    </a-steps>
    <div>
      <dr-wizard-start-step
          v-if="zoneSteps[currentStep].name === 'type'"
          @nextPressed="nextPressed"
          @fieldsChanged="onFieldsChanged"
          :prefillContent="zoneConfig"
      />
      <dr-wizard-running-step
        v-else-if="zoneSteps[currentStep].name === 'coreType'"
        @nextPressed="nextPressed"
        @backPressed="backPressed"
        @fieldsChanged="onFieldsChanged"
        :isFixError="stepFixError"
        :prefillContent="zoneConfig"
      />
      <dr-wizard-launch
        v-else
        @closeAction="onCloseAction"
        @refresh-data="onRefreshData"
        @stepError="onStepError"
        :launchZone="launchZone"
        :stepChild="stepChild"
        :launchData="launchData"
        :isFixError="stepFixError"
        :prefillContent="zoneConfig"
      />
    </div>
  </div>
</template>
<script>
import { mixinDevice } from '@/utils/mixin.js'
import DrWizardLaunch from '@/views/compute/dr/DrWizardLaunch.vue'
import DrWizardStartStep from '@/views/compute/dr/DrWizardStartStep.vue'
import DrWizardRunningStep from '@/views/compute/dr/DrWizardRunningStep.vue'
import ZoneWizardZoneTypeStep from '@/views/infra/zone/ZoneWizardZoneTypeStep.vue'
import ZoneWizardCoreZoneTypeStep from '@/views/infra/zone/ZoneWizardCoreZoneTypeStep.vue'
import ZoneWizardZoneDetailsStep from '@/views/infra/zone/ZoneWizardZoneDetailsStep.vue'
import ZoneWizardNetworkSetupStep from '@/views/infra/zone/ZoneWizardNetworkSetupStep.vue'
import ZoneWizardAddResources from '@/views/infra/zone/ZoneWizardAddResources.vue'
import ZoneWizardLaunchZone from '@/views/infra/zone/ZoneWizardLaunchZone.vue'

export default {
  components: {
    DrWizardStartStep,
    DrWizardLaunch,
    DrWizardRunningStep,
    ZoneWizardZoneTypeStep,
    ZoneWizardCoreZoneTypeStep,
    ZoneWizardZoneDetailsStep,
    ZoneWizardNetworkSetupStep,
    ZoneWizardAddResources,
    ZoneWizardLaunchZone
  },
  mixins: [mixinDevice],
  data () {
    return {
      currentStep: 0,
      stepFixError: false,
      launchZone: false,
      launchData: {},
      stepChild: '',
      coreTypeStep: {
        name: 'coreType',
        title: 'label.running',
        step: [],
        description: this.$t('message.select.zone.description'),
        hint: this.$t('message.select.zone.hint')
      },
      steps: [
        {
          name: 'type',
          title: 'label.start',
          step: [],
          description: this.$t('message.select.zone.description'),
          hint: this.$t('message.select.zone.hint')
        },
        {
          name: 'launch',
          title: 'label.end',
          step: ['launchZone'],
          description: this.$t('message.launch.zone.description'),
          hint: this.$t('message.launch.zone.hint')
        }
      ],
      zoneConfig: {}
    }
  },
  computed: {
    zoneSteps () {
      var steps = [...this.steps]
      steps.splice(1, 0, this.coreTypeStep)
      return steps
    }
  },
  methods: {
    nextPressed () {
      this.currentStep++
      this.scrollToStepActive()
    },
    backPressed (data) {
      this.currentStep--
      this.scrollToStepActive()
    },
    scrollToStepActive () {
      if (!this.isMobile()) {
        return
      }
      this.$nextTick(() => {
        if (!this.$refs.zoneStep) {
          return
        }
        if (this.currentStep === 0) {
          this.$refs.zoneStep.$el.scrollLeft = 0
          return
        }
        this.$refs.zoneStep.$el.scrollLeft = this.$refs['step' + (this.currentStep - 1)][0].$el.offsetLeft
      })
    },
    onFieldsChanged (data) {
      if (data.zoneType &&
        this.zoneConfig.zoneType &&
        data.zoneType !== this.zoneConfig.zoneType) {
        this.zoneConfig.physicalNetworks = null
      }

      this.zoneConfig = { ...this.zoneConfig, ...data }
    },
    onCloseAction () {
      this.$emit('close-action')
    },
    onRefreshData () {
      this.$message.success(this.$t('message.processing.complete'))
      this.$emit('refresh-data')
      this.onCloseAction()
    },
    onStepError (step, launchData) {
      this.currentStep = this.zoneSteps.findIndex(item => item.step.includes(step))
      this.stepChild = step
      this.launchData = launchData
      this.launchZone = false
      this.stepFixError = true
    },
    onLaunchZone () {
      this.stepFixError = false
      this.launchZone = true
      this.currentStep = this.zoneSteps.findIndex(item => item.step.includes('launchZone'))
    }
  }
}
</script>

<style scoped lang="scss">
  .form {
    width: 100%;

    @media (min-width: 1000px) {
      width: 800px;
    }

    :deep(.form-action) {
      position: relative;
      margin-top: 16px;
      height: 35px;
    }

    :deep(.button-next) {
      position: absolute;
      right: 0;
    }

    :deep(.button-next).ant-btn-loading:not(.ant-btn-circle):not(.ant-btn-circle-outline):not(.ant-btn-icon-only) {
      position: absolute;
      right: 0;
    }

    :deep(.ant-steps) {
      overflow-x: auto;
      padding: 10px 0;
    }

    :deep(.submit-btn) {
      display: none;
    }
  }

  :deep(.ant-form-text) {
    width: 100%;
  }

  .steps-content {
    border: 1px dashed #e9e9e9;
    border-radius: 6px;
    background-color: #fafafa;
    min-height: 200px;
    text-align: center;
    vertical-align: center;
    padding: 8px;
    padding-top: 16px;
  }

  .steps-action {
    margin-top: 24px;
  }
</style>
