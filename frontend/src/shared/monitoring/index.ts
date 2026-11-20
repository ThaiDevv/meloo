import * as Sentry from '@sentry/react'

export function initMonitoring(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN
  const release = import.meta.env.VITE_SENTRY_RELEASE
  if (!import.meta.env.PROD || import.meta.env.VITE_API_MOCK === 'true' || !dsn || !release) return
  Sentry.init({
    dsn,
    release,
    environment: import.meta.env.MODE,
    defaultIntegrations: false,
    integrations: [Sentry.globalHandlersIntegration(), Sentry.browserApiErrorsIntegration()],
    tracesSampleRate: 0,
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,
    maxBreadcrumbs: 0,
    dataCollection: {
      userInfo: false,
      cookies: false,
      httpHeaders: false,
      httpBodies: [],
      urlQueryParams: false,
      stackFrameVariables: false,
      frameContextLines: 0,
    },
    beforeSend(event, hint) {
      // Keep only bundled code positions; messages, URLs and scope can expose game answers/tokens.
      hint.attachments = []
      const frames = (
        event.exception?.values?.flatMap((error) => error.stacktrace?.frames ?? []) ?? []
      )
        .filter((frame) => /^https?:\/\/[^/]+\/assets\/[\w.-]+\.js$/.test(frame.filename ?? ''))
        .slice(-50)
        .map((frame) => ({
          filename: new URL(frame.filename ?? window.location.href).pathname,
          lineno: frame.lineno,
          colno: frame.colno,
        }))
      return {
        type: undefined,
        event_id: event.event_id,
        timestamp: event.timestamp,
        release,
        environment: import.meta.env.MODE,
        level: 'error',
        exception: { values: [{ type: 'FrontendError', stacktrace: { frames } }] },
      }
    },
  })
}
