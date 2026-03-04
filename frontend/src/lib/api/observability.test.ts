import { describe, expect, it } from 'vitest'
import { describeObservabilityError, ObservabilityError } from './observability'

describe('describeObservabilityError', () => {
  it('returns unauthorized diagnostic for 401', () => {
    const error = new ObservabilityError('unauthorized', '401')
    expect(describeObservabilityError(error)).toContain('401 Unauthorized')
  })

  it('returns forbidden diagnostic for 403', () => {
    const error = new ObservabilityError('forbidden', '403')
    expect(describeObservabilityError(error)).toContain('403 Forbidden')
  })

  it('returns endpoint misconfiguration diagnostic for endpoint 404', () => {
    const error = new ObservabilityError('endpoint-missing', 'missing')
    expect(describeObservabilityError(error)).toContain('management endpoint exposure')
  })

  it('returns missing metric diagnostic with metric names', () => {
    const error = new ObservabilityError('metric-missing', 'missing metric', {
      missingMetricNames: ['openpulse.alerts.dispatch.latency'],
    })
    expect(describeObservabilityError(error)).toContain('openpulse.alerts.dispatch.latency')
  })

  it('returns server diagnostic for 5xx', () => {
    const error = new ObservabilityError('server-error', '500', { status: 500 })
    expect(describeObservabilityError(error)).toContain('500')
  })

  it('returns network diagnostic for network failures', () => {
    const error = new ObservabilityError('network', 'network')
    expect(describeObservabilityError(error)).toContain('Network error')
  })
})
