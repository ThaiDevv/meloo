import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Sentry from '@sentry/react'
import main from '../../main.tsx?raw'
import { initMonitoring } from './index'

vi.mock('@sentry/react', () => ({
  init: vi.fn(),
  globalHandlersIntegration: () => ({ name: 'GlobalHandlers' }),
  browserApiErrorsIntegration: () => ({ name: 'BrowserApiErrors' }),
}))

beforeEach(() => {
  vi.stubEnv('PROD', true)
  vi.stubEnv('VITE_API_MOCK', 'false')
  vi.stubEnv('VITE_SENTRY_DSN', 'https://public@example.com/1')
  vi.stubEnv('VITE_SENTRY_RELEASE', 'meloo@sha-1234567')
  vi.mocked(Sentry.init).mockClear()
})
afterEach(() => vi.unstubAllEnvs())

describe('initMonitoring', () => {
  it.each([
    ['PROD', false],
    ['VITE_API_MOCK', 'true'],
    ['VITE_SENTRY_DSN', ''],
    ['VITE_SENTRY_RELEASE', ''],
  ] as const)('does not send events when %s is %s', (name, value) => {
    vi.stubEnv(name, value)
    initMonitoring()
    expect(Sentry.init).not.toHaveBeenCalled()
  })

  it('initializes release-tagged error capture without replay, traces or breadcrumbs', () => {
    initMonitoring()
    expect(Sentry.init).toHaveBeenCalledWith(
      expect.objectContaining({
        release: 'meloo@sha-1234567',
        defaultIntegrations: false,
        tracesSampleRate: 0,
        replaysOnErrorSampleRate: 0,
        maxBreadcrumbs: 0,
      }),
    )
  })

  it('removes nested sensitive data, messages, URLs, frame variables and attachments', () => {
    initMonitoring()
    const options = vi.mocked(Sentry.init).mock.calls[0]?.[0]
    if (!options?.beforeSend) throw new Error('Monitoring callback missing')
    const secret = 'answer-JWT-audio-refresh-secret'
    const hint = { attachments: [{ filename: secret, data: secret }] }
    const result = options.beforeSend(
      {
        type: undefined,
        message: secret,
        user: { email: secret },
        request: { url: `https://api.test/audio/${secret}`, headers: { Authorization: secret } },
        extra: { nested: { answer: secret } },
        contexts: { game: { answer: secret } },
        breadcrumbs: [{ message: secret }],
        tags: { token: secret },
        exception: {
          values: [
            {
              type: secret,
              value: secret,
              stacktrace: {
                frames: [
                  { filename: `https://api.test/audio/${secret}`, vars: { answer: secret } },
                  {
                    filename: 'https://meloo.test/assets/index-abc.js',
                    lineno: 4,
                    colno: 8,
                    context_line: secret,
                  },
                ],
              },
            },
          ],
        },
      },
      hint,
    )
    expect(JSON.stringify(result)).not.toContain(secret)
    expect(result).toMatchObject({
      release: 'meloo@sha-1234567',
      exception: {
        values: [
          {
            type: 'FrontendError',
            stacktrace: { frames: [{ filename: '/assets/index-abc.js', lineno: 4, colno: 8 }] },
          },
        ],
      },
    })
    expect(hint.attachments).toEqual([])
  })

  it('is called from main.tsx without changing the application entry point', () => {
    expect(main).toMatch(/initMonitoring\(\)/)
  })
})
