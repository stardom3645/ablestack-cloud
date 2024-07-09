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
  <a-alert v-if="drSteps[currentStep].name === 'start'" style="margin-bottom: 5px" type="warning" show-icon>
    <template #message>
      <span v-html="$t('message.warning.dr.mirroring.test.vm.step.start')" />
    </template>
  </a-alert>
  <div class="form">
    <a-steps
      ref="drStep"
      labelPlacement="vertical"
      size="small"
      :current="currentStep">
      <a-step
        v-for="(item, index) in drSteps"
        :key="item.title"
        :title="$t(item.title)"
        :ref="`step${index}`">
      </a-step>
    </a-steps>
    <div>
      <dr-wizard-start-step
          v-if="drSteps[currentStep].name === 'start'"
          @nextPressed="nextPressed"
          :resource="this.resource"
      />
      <dr-wizard-end-step
        v-else-if="drSteps[currentStep].name === 'stepComplete'"
        @closeAction="onCloseAction"
        @refresh-data="onRefreshData"
        @stepError="onStepError"
        :stepComplete="stepComplete"
        :stepChild="stepChild"
        :mockData="mockData"
        :resource="this.resource"
      />
      <dr-wizard-running-step
        v-else
        @nextPressed="nextPressed"
        @backPressed="backPressed"
        :resource="this.resource"
      />
    </div>
  </div>
</template>
<script>
import { mixinDevice } from '@/utils/mixin.js'
import DrWizardEndStep from '@/views/compute/dr/DrWizardEndStep.vue'
import DrWizardStartStep from '@/views/compute/dr/DrWizardStartStep.vue'
import DrWizardRunningStep from '@/views/compute/dr/DrWizardRunningStep.vue'
export default {
  components: {
    DrWizardStartStep,
    DrWizardRunningStep,
    DrWizardEndStep
  },
  props: {
    resource: {
      type: Object,
      default: () => {}
    }
  },
  mixins: [mixinDevice],
  data () {
    return {
      currentStep: 0,
      stepComplete: false,
      mockData: {},
      stepChild: '',
      steps: [
        {
          name: 'start',
          title: 'label.start',
          step: []
        },
        {
          name: 'running',
          title: 'label.running',
          step: ['stepOneTestDr', 'stepTwoTestDr', 'stepThreeTestDr', 'stepFourTestDr', 'stepFiveTestDr', 'stepSixTestDr', 'stepSevenTestDr']
        },
        {
          name: 'end',
          title: 'label.end',
          step: ['stepComplete']
        }
      ]
    }
  },
  computed: {
    drSteps () {
      var steps = [...this.steps]
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
        if (!this.$refs.drStep) {
          return
        }
        if (this.currentStep === 0) {
          this.$refs.drStep.$el.scrollLeft = 0
          return
        }
        this.$refs.drStep.$el.scrollLeft = this.$refs['step' + (this.currentStep - 1)][0].$el.offsetLeft
      })
    },
    onCloseAction () {
      this.$emit('close-action')
    },
    onRefreshData () {
      this.$message.success(this.$t('message.processing.complete'))
      this.$emit('refresh-data')
      this.onCloseAction()
    },
    onStepError (step, mockData) {
      this.currentStep = this.drSteps.findIndex(item => item.step.includes(step))
      this.stepChild = step
      this.mockData = mockData
      this.stepComplete = false
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
  .ant-alert-warning {
    border: 1px solid #ffe58f;
    background-color: #fffbe6;
  }
  .steps-action {
    margin-top: 24px;
  }
</style>
