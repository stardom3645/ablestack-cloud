import { shallowRef, defineAsyncComponent } from 'vue'

export default {
  name: 'alertRules',
  title: 'label.alertRules',
  icon: 'SoundOutlined',
  permission: ['listWallAlertRules'],
  resourceType: 'WallAlertRule',
  filters: () => {
    const filters = ['alerting', 'OK', 'pending', 'nodata']
    return filters
  },
  searchFilters: ['name', 'state', 'kind'],
  columns: () => [
    'name', // 규칙명
    'ispaused',
    'state', // ALERTING/PENDING/OK/NODATA
    'threshold',
    'rulegroup',
    'kind', // HOST/STORAGE/CLOUD/USER
    'lastEvaluation'
  ],
  details: ['name', 'id', 'state', 'ispaused', 'threshold', 'rulegroup', 'kind', 'lastEvaluation', 'summary', 'description'],
  dataMap: (item) => ({
    ...item,
    rawState: item.state,
    state: item?.ispaused ? 'stopped' : 'running'
  }),
  tabs: [{
    name: 'details',
    component: shallowRef(defineAsyncComponent(() => import('@/components/view/DetailsTab.vue')))
  }],
  actions: [
    {
      api: 'updateWallAlertRuleThreshold',
      icon: 'edit-outlined',
      label: 'label.update.threshold',
      message: 'message.confirm.update.threshold',
      dataView: true,
      // 팝업 폼 사용
      popup: true,
      // 단건 수정
      groupAction: false,
      // API에 보낼 파라미터
      args: ['id', 'threshold'],
      mapping: {
        id: {
          value: (record) => record.id
        }
      },
      fields: [
        {
          name: 'threshold',
          title: 'label.threshold',
          type: 'number',
          required: true,
          min: 0,
          step: 'any'
        }
      ],
      successMessage: 'label.threshold.updated'
    },
    {
      api: 'pauseWallAlertRule',
      icon: 'pause-circle-outlined',
      label: 'label.action.pause.alert.rule',
      message: 'message.action.pause.alert.rule',
      dataView: true,
      groupAction: true,
      popup: false,
      args: ['id', 'paused'],
      mapping: {
        id: { value: (record) => record.id },
        paused: { value: () => true }
      },
      groupMap: (selection, _values, records) => {
        return selection.map(x => {
          const rec = records.find(r => r.id === x)
          return { id: rec?.id || x, paused: true }
        })
      },
      show: (record) => record && (record.isPaused === false || record.isPaused === undefined)
    },
    {
      api: 'pauseWallAlertRule',
      icon: 'play-circle-outlined',
      label: 'label.action.resume.alert.rule',
      message: 'message.action.start.alert.rule',
      dataView: true,
      groupAction: true,
      popup: false,
      args: ['id', 'paused'],
      mapping: {
        id: { value: (record) => record.id },
        paused: { value: () => false }
      },
      groupMap: (selection, _values, records) => {
        return selection.map(x => {
          const rec = records.find(r => r.id === x)
          return { id: rec?.id || x, paused: false }
        })
      },
      show: (record) => record && record.isPaused === true
    }
  ]
}
