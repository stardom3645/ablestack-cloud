import { pollJobPlugin } from '@/utils/plugins'
import { getAPI } from '@/api'
import { message, notification } from 'ant-design-vue'
import eventBus from '@/config/eventBus'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
jest.mock('@/locales', () => ({ i18n: { global: { t: x => x } } }))
jest.mock('@/store', () => ({ getters: { countNotify: 0, headerNotices: [] }, state: { user: {} }, watch: jest.fn(), dispatch: jest.fn(), commit: jest.fn() }))
jest.mock('@/config/eventBus', () => ({ emit: jest.fn() }))
jest.mock('ant-design-vue', () => ({ message: { destroy: jest.fn(), loading: jest.fn(), success: jest.fn(), error: jest.fn() }, notification: { error: jest.fn(), warning: jest.fn() } }))
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
function setup () {
  const app = { config: { globalProperties: {} } }
  pollJobPlugin.install(app)
  return app.config.globalProperties.$pollJob.bind({ $route: { fullPath: '/vm/a' }, $router: { currentRoute: { value: { path: '/vm/a' } } } })
}
describe('pollJob notification lifecycle', () => {
  beforeEach(() => {
    jest.useFakeTimers(); jest.clearAllMocks()
    for (const method of ['destroy', 'loading', 'success', 'error']) jest.spyOn(message, method).mockImplementation(() => {})
    jest.spyOn(notification, 'warning').mockImplementation(() => {})
    jest.spyOn(notification, 'close').mockImplementation(() => {})
  })
  afterEach(() => jest.useRealTimers())
  it('cleans the second job after interrupted polling while the first succeeds', async () => {
    const count = {}
    getAPI.mockImplementation((_, { jobId }) => {
      count[jobId] = (count[jobId] || 0) + 1
      if (count[jobId] === 1) return Promise.resolve({ queryasyncjobresultresponse: { jobstatus: 0 } })
      return jobId === 'a' ? Promise.resolve({ queryasyncjobresultresponse: { jobstatus: 1 } }) : Promise.reject(new Error('offline'))
    })
    const poll = setup(); const a = poll({ jobId: 'a' }); const b = poll({ jobId: 'b', batchKey: 'batch' })
    await flush(); for (let i = 0; i < 4; i++) { jest.runOnlyPendingTimers(); await flush() }
    expect((await a).jobstatus).toBe(1); expect((await b).trackingStatus).toBe('unknown')
    expect(message.destroy).toHaveBeenCalledWith('b'); expect(notification.warning).toHaveBeenCalled()
    expect(message.error).not.toHaveBeenCalled()
    getAPI.mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 1 } })
    await poll({ jobId: 'b', retry: true })
    expect(notification.close).toHaveBeenCalledWith('batch')
    expect(message.success).toHaveBeenCalled()
  })
  it('cleans loading and invokes completion even when refresh listeners throw', async () => {
    getAPI.mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 1 } })
    eventBus.emit.mockImplementation(() => { throw new Error('listener') })
    const log = jest.spyOn(console, 'error').mockImplementation(() => {})
    const successMethod = jest.fn(); await setup()({ jobId: 'a', successMethod })
    expect(message.destroy).toHaveBeenCalledWith('a'); expect(successMethod).toHaveBeenCalledTimes(1)
    log.mockRestore(); eventBus.emit.mockReset()
  })
})
