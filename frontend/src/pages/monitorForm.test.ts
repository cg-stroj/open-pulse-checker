import { describe, expect, it } from 'vitest'
import { initFormState, validateMonitorForm } from './monitorForm'

describe('validateMonitorForm', () => {
  it('accepts allowed interval values', () => {
    const form = initFormState()

    for (const intervalSec of ['60', '120', '180', '240', '300']) {
      const errors = validateMonitorForm({ ...form, intervalSec, targetUrl: 'https://example.com' })
      expect(errors.intervalSec).toBeUndefined()
    }
  })

  it('rejects interval outside 1-5 minute set', () => {
    const errors = validateMonitorForm({
      ...initFormState(),
      targetUrl: 'https://example.com',
      intervalSec: '90',
    })

    expect(errors.intervalSec).toBe('Interval must be one of 60, 120, 180, 240, 300 seconds (1-5 minutes).')
  })

  it('requires tcp host:port format', () => {
    const errors = validateMonitorForm({
      ...initFormState(),
      type: 'TCP',
      targetUrl: 'localhost',
    })

    expect(errors.targetUrl).toBe('TCP target must be host:port (for example localhost:5432).')
  })
})
