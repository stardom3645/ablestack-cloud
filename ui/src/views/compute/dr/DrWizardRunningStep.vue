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
  <div>
    <div>
      <a-card class="ant-form-text card-launch-description">
        {{ $t(description.launching) }}
      </a-card>
      <a-card
          id="launch-content"
          class="ant-form-text card-launch-content">
        <a-steps
            size="small"
            direction="vertical"
            :current="currentStep"
        >
          <a-step
              v-for="(step, index) in steps"
              :key="index"
              :title="$t(step.title)"
              :status="step.status">
            <template #icon>
              <LoadingOutlined v-if="step.status===status.PROCESS" />
              <CloseCircleOutlined v-else-if="step.status===status.FAILED" />
            </template>
            <template #description>
              <a-card
                  class="step-error"
                  v-if="step.status===status.FAILED"
              >
                <div><strong>{{ $t('label.error.something.went.wrong.please.correct.the.following') }}:</strong></div>
                <div>{{ messageError }}</div>
              </a-card>
            </template>
          </a-step>
        </a-steps>
      </a-card>
    <div class="form-action">
      <a-button
        v-if="processStatus==='error'"
        class="button-next"
        type="primary"
        :loading="loading"
        @click="closeModal">{{ $t('label.cancel') }}
      </a-button>
    </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { api } from '@/api'
const STATUS_PROCESS = 'process'
const STATUS_FINISH = 'finish'
const STATUS_FAILED = 'error'
export default {
  props: {
    prefillContent: {
      type: Object,
      default: function () {
        return {}
      }
    },
    isFixError: {
      type: Boolean,
      default: false
    },
    resource: {
      type: Object,
      default: () => {}
    }
  },
  data: () => ({
    formItemLayout: {
      labelCol: { span: 6 },
      wrapperCol: { span: 14 }
    },
    formModel: {},
    isLaunchTest: false,
    processStatus: null,
    description: {
      waiting: 'message.launch.dr.simulation.test',
      launching: 'message.waiting.dr.simulation.test',
      finish: 'message.finish.dr.simulation.test'
    },
    loading: false,
    messageError: '',
    currentStep: 0,
    steps: [],
    stepData: {},
    status: {
      PROCESS: STATUS_PROCESS,
      FAILED: STATUS_FAILED,
      FINISH: STATUS_FINISH
    },
    clusters: [],
    vmMap: []
  }),
  created () {
    this.initForm()
  },
  watch: {
    formModel: {
      deep: true,
      handler (changedFields) {
        const fieldsChanged = toRaw(changedFields)
        this.$emit('fieldsChanged', fieldsChanged)
      }
    }
  },
  updated () {
    const launchElm = this.$el.querySelector('#launch-content')
    if (launchElm) {
      launchElm.scrollTop = launchElm.scrollHeight
    }
  },
  mounted () {
    if (this.launchMock) {
      this.processStatus = STATUS_PROCESS
      this.stepData = this.launchData
    }
    this.handleSubmit()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
      })
      this.rules = reactive({
      })
      this.formModel = toRaw(this.form)
    },
    addStep (title, step) {
      this.steps.push({
        index: this.currentStep,
        title,
        step,
        status: STATUS_PROCESS
      })
      this.setStepStatus(STATUS_PROCESS)
    },
    setStepStatus (status) {
      const index = this.steps.findIndex(step => step.index === this.currentStep)
      this.steps[index].status = status
      this.nsx = false
    },
    async handleSubmit () {
      this.processStatus = STATUS_PROCESS
      if (!this.stepData.stepMove) {
        this.stepData.stepMove = []
      }
      await this.stepOneTestDr()
    },
    async stepOneTestDr () {
      this.addStep('message.dr.simulation.test.step1', 'stepOneTestDr')
      try {
        if (!this.stepData.stepMove.includes('stepOneTestDr')) {
          await this.drInfo()
          await this.stopVm()
          await this.stopDrVm()
          this.stepData.stepMove.push('stepOneTestDr')
        }
        await this.stepTwoTestDr()
        await this.stepThreeTestDr()
        await this.stepFourTestDr()
        await this.stepFiveTestDr()
        await this.stepSixTestDr()
        await this.stepSevenTestDr()
        await this.stepLastTestDr()
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepTwoTestDr () {
      if (this.stepData.stepMove.includes('stepTwoTestDr')) {
        return
      }
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step2', 'stepTwoTestDr')
      try {
        await this.demoteImage()
        this.stepData.stepMove.push('stepTwoTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepThreeTestDr () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step3', 'stepThreeTestDr')
      try {
        await this.startDrVm()
        this.stepData.stepMove.push('stepThreeTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepFourTestDr () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step4', 'stepFourTestDr')
      try {
        await this.statusVm()
        this.stepData.stepMove.push('stepFourTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepFiveTestDr () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step5', 'stepFiveTestDr')
      try {
        await this.stopDrVm()
        this.stepData.stepMove.push('stepFiveTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepSixTestDr () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step6', 'stepSixTestDr')
      try {
        await this.promoteDrImage()
        this.stepData.stepMove.push('stepSixTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepSevenTestDr () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.simulation.test.step7', 'stepSevenTestDr')
      try {
        await this.startVm()
        this.stepData.stepMove.push('stepSevenTestDr')
      } catch (e) {
        this.messageError = e
        this.processStatus = STATUS_FAILED
        this.setStepStatus(STATUS_FAILED)
      }
    },
    async stepLastTestDr () {
      await this.stepComplete()
    },
    async pollJob (jobId) {
      return new Promise(resolve => {
        const asyncJobInterval = setInterval(() => {
          api('queryAsyncJobResult', { jobId }).then(async json => {
            const result = json.queryasyncjobresultresponse
            if (result.jobstatus === 0) {
              return
            }
            clearInterval(asyncJobInterval)
            resolve(result)
          })
        }, 1000)
      })
    },
    drInfo () {
      return new Promise((resolve, reject) => {
        api('getDisasterRecoveryClusterList', {
          drclustertype: 'secondary'
        }).then(json => {
          this.drClusterList = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster || []
          for (const cluster of this.drClusterList) {
            const vmList = cluster.drclustervmmap
            if (vmList.some(vm => vm.drclustervmid === this.resource.id)) {
              this.clusters = cluster
              var map = cluster.drclustervmmap
              this.vmMap = map.filter(it => it.drclustervmid === this.resource.id)
              break
            }
          }
          console.log(this.vmMap)
          resolve(this.vmMap)
        }).catch(error => {
          reject(error)
        })
      })
    },
    async stopVm () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.forced = true
        api('stopVirtualMachine', params).then(async json => {
          const jobId = json.stopvirtualmachineresponse.jobid
          if (jobId) {
            const result = await this.pollJob(jobId)
            if (result.jobstatus === 2) {
              message = `stopVirtualMachine ${this.$t('label.failed').toLowerCase()}. ${this.$t('label.error')}: ` + result.jobresult.errortext
              reject(message)
              return
            }
            resolve(result)
          }
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    stopDrVm () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.drclustername = this.clusters.drclustername
        api('stopDisasterRecoveryClusterVm', params).then(json => {
          resolve()
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    demoteImage () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.drclustername = this.clusters.drclustername
        api('demoteDisasterRecoveryClusterVm', params).then(json => {
          resolve()
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    startDrVm () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.drclustername = this.clusters.drclustername
        api('startDisasterRecoveryClusterVm', params).then(json => {
          resolve()
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    statusVm () {
      return new Promise((resolve, reject) => {
        setTimeout(() => {
          // 1min status check
          resolve()
        }, 60000)
      })
    },
    promoteDrImage () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.drclustername = this.clusters.drclustername
        api('promoteDisasterRecoveryClusterVm', params).then(json => {
          resolve()
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    async startVm () {
      return new Promise((resolve, reject) => {
        let message = ''
        const params = {}
        params.id = this.resource.id
        params.considerlasthost = true
        api('startVirtualMachine', params).then(async json => {
          const jobId = json.startvirtualmachineresponse.jobid
          if (jobId) {
            const result = await this.pollJob(jobId)
            if (result.jobstatus === 2) {
              message = `startVirtualMachine ${this.$t('label.failed').toLowerCase()}. ${this.$t('label.error')}: ` + result.jobresult.errortext
              reject(message)
              return
            }
            resolve(result)
          }
        }).catch(error => {
          message = error.response.headers['x-description']
          reject(message)
        })
      })
    },
    stepComplete () {
      this.setStepStatus(STATUS_FINISH)
      this.currentStep++
      this.addStep('message.dr.test.complete', 'stepComplete')
      this.setStepStatus(STATUS_FINISH)
      this.processStatus = STATUS_FINISH
    },
    closeModal () {
      this.steps = []
      this.$emit('close-modal')
      this.$emit('refresh-data')
    }
  }
}
</script>
<style scoped lang="less">
  .card-waiting-launch {
    text-align: center;
    margin: 10px 0;
    width: 100%;
    padding: 20px;
    font-size: 20px;
  }
  .card-item {
    margin-top: 10px;
    .card-form-item {
      float: left;
    }
    .checkbox-advance {
      margin-top: 10px;
    }
    .zone-support {
      text-align: justify;
    }
  }
  .ant-form-text {
    text-align: justify;
    margin: 10px 0;
    padding: 24px;
    width: 100%;
  }
</style>
