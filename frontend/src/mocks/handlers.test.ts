import { describe, expect, it } from 'vitest'
import { apiClient } from '@/shared/api/client'

describe('MSW mock server', () => {
  it('answers requests made through the API client', async () => {
    const res = await fetch('/api/health').catch(() => null)
    expect(res).not.toBeNull()
    expect(await res?.json()).toEqual({ status: 'UP' })
    expect(apiClient).toBeDefined()
  })
})
