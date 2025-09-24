<template>
  <a-affix :offset-top="0">
    <a-alert
      v-if="visible"
      banner
      :show-icon="true"
      :type="type"
      :closable="closable"
      @close="onClose"
    >
      <template #message>
        <span>
          {{ messagePrefix }} ({{ count }}건) —
          <a :href="detailsLink">자세히 보기</a>
        </span>
      </template>
    </a-alert>
  </a-affix>
</template>

<script>
import { api } from '@/api'
import { vueProps } from '@/vue-app'

export default {
  name: 'AutoAlertBanner',
  data () {
    const cfg = (vueProps.$config && vueProps.$config.announcementBannerAuto) || {}
    return {
      enabled: cfg.enabled !== false,
      intervalSec: Number(cfg.intervalSec || 30),
      type: cfg.type || 'error',
      closable: cfg.closable !== false,
      messagePrefix: cfg.messagePrefix || '경고 발생',
      detailsLink: cfg.detailsLink || '#/wallalerts?state=ALERTING',
      onlyOnAlertRules: cfg.onlyOnAlertRules === true, // 기본: 전체 레이아웃에서 표시
      visible: false,
      count: 0,
      timerId: null,
      manuallyClosed: false
    }
  },
  methods: {
    async checkAlerts () {
      if (!this.enabled) {
        this.visible = false
        return
      }
      if (this.onlyOnAlertRules && this.$route?.name !== 'alertRules') {
        this.visible = false
        return
      }
      if (document.visibilityState !== 'visible') {
        return
      }
      try {
        const params = { state: 'ALERTING', page: 1, pagesize: 1 }
        const res = await api('listWallAlertRules', params)
        const resp = res?.listwallalertrulesresponse || res?.listWallAlertRulesResponse || {}
        let cnt = typeof resp.count === 'number' ? resp.count : 0
        if (!cnt && Array.isArray(resp.wallalertruleresponse)) {
          cnt = resp.wallalertruleresponse.filter(r => r?.state === 'ALERTING' || r?.type === 'alerting').length
        }
        this.count = cnt
        this.visible = cnt > 0 && !this.manuallyClosed
      } catch (_e) {
        // 조용히 무시(안정성 우선)
      }
    },
    startTimer () {
      const ms = Math.max(5, this.intervalSec) * 1000
      this.timerId = window.setInterval(() => this.checkAlerts(), ms)
    },
    stopTimer () {
      if (this.timerId) {
        window.clearInterval(this.timerId)
        this.timerId = null
      }
    },
    onVisibilityChange () {
      if (document.visibilityState === 'visible') {
        this.checkAlerts()
        this.startTimer()
      } else {
        this.stopTimer()
      }
    },
    onClose () {
      this.manuallyClosed = true
      this.visible = false
    }
  },
  mounted () {
    this.checkAlerts()
    this.startTimer()
    document.addEventListener('visibilitychange', this.onVisibilityChange)
  },
  beforeUnmount () {
    this.stopTimer()
    document.removeEventListener('visibilitychange', this.onVisibilityChange)
  },
  watch: {
    '$route.name' () {
      this.manuallyClosed = false
      this.checkAlerts()
    }
  }
}
</script>
