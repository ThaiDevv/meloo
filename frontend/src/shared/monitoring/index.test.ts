import { describe, expect, it } from 'vitest'
import main from '../../main.tsx?raw'
import { initMonitoring } from './index'

describe('initMonitoring', () => {
  it('is a no-op that can be called safely', () => {
    expect(() => {
      initMonitoring()
    }).not.toThrow()
  })

  it('is called from main.tsx so INF-04 never has to touch it', () => {
    expect(main).toMatch(/initMonitoring\(\)/)
  })
})
