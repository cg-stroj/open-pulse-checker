import { useQuery } from '@tanstack/react-query'
import axios, { AxiosError } from 'axios'
import { readAuthSession } from '../auth/session'
import { appConfig } from '../config/app'

type MetricStatistic =
  | 'COUNT'
  | 'TOTAL'
  | 'TOTAL_TIME'
  | 'VALUE'
  | 'MAX'
  | 'UNKNOWN'

interface RawMetricMeasurement {
  statistic?: string
  value?: number
}

interface RawMetricTag {
  tag?: string
  values?: string[]
}

interface RawMetricResponse {
  name?: string
  measurements?: RawMetricMeasurement[]
  availableTags?: RawMetricTag[]
}

interface RawMetricCatalogResponse {
  names?: string[]
}

interface MetricValue {
  count: number
  total: number
  max: number
  value: number
}

export interface OpsDashboardData {
  lock: {
    contentionRatio: number
    lockAcquireSuccess: number
    lockAcquireFail: number
    lockAcquireSteal: number
    lockRenewFail: number
  }
  scheduler: {
    skipLock: number
    skipLocalInflight: number
    skipLockRatio: number
  }
  dlq: {
    backlog: number
    oldestAgeSeconds: number
  }
  notifier: {
    dispatchSuccess: number
    dispatchFailed: number
    failureRatio: number
  }
  latency: {
    dispatchMeanSeconds: number
    dispatchMaxSeconds: number
    deliveryMeanSeconds: number
    deliveryMaxSeconds: number
  }
}

export type ObservabilityErrorKind = 'unauthorized' | 'forbidden' | 'endpoint-missing' | 'metric-missing' | 'server-error' | 'network' | 'unknown'

export class ObservabilityError extends Error {
  readonly kind: ObservabilityErrorKind
  readonly status?: number
  readonly metricName?: string
  readonly missingMetricNames?: string[]

  constructor(
    kind: ObservabilityErrorKind,
    message: string,
    options: {
      status?: number
      metricName?: string
      missingMetricNames?: string[]
      cause?: unknown
    } = {},
  ) {
    super(message)
    this.name = 'ObservabilityError'
    this.kind = kind
    this.status = options.status
    this.metricName = options.metricName
    this.missingMetricNames = options.missingMetricNames
    if (options.cause) {
      this.cause = options.cause
    }
  }
}

const REQUIRED_METRIC_NAMES = [
  'openpulse.scheduler.lock.acquire.success',
  'openpulse.scheduler.lock.acquire.fail',
  'openpulse.scheduler.lock.acquire.steal',
  'openpulse.scheduler.lock.renew.fail',
  'openpulse.scheduler.execution.skip.lock',
  'openpulse.scheduler.execution.skip.local_inflight',
  'openpulse.alerts.dlq.backlog',
  'openpulse.alerts.dlq.oldest.age.seconds',
  'openpulse.alerts.dispatch.attempts',
  'openpulse.alerts.dispatch.latency',
  'openpulse.alerts.delivery.delay',
] as const

function buildActuatorBaseUrl() {
  const fallbackOrigin = typeof window === 'undefined' ? 'http://localhost:8080' : window.location.origin
  const apiUrl = new URL(appConfig.apiBaseUrl, fallbackOrigin)

  const normalizedPath = apiUrl.pathname.endsWith('/') ? apiUrl.pathname.slice(0, -1) : apiUrl.pathname
  const apiPathMatch = normalizedPath.match(/^(.*)\/api(?:\/v\d+)?$/)
  const proxyPrefix = apiPathMatch?.[1] ?? ''

  return `${apiUrl.origin}${proxyPrefix}/actuator`
}

const actuatorClient = axios.create({
  baseURL: buildActuatorBaseUrl(),
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

actuatorClient.interceptors.request.use((config) => {
  const authSession = readAuthSession()
  if (authSession?.authorizationHeader) {
    config.headers.Authorization = authSession.authorizationHeader
  }
  return config
})

function buildObservabilityError(error: unknown, metricName?: string): ObservabilityError {
  if (!(error instanceof AxiosError)) {
    return new ObservabilityError('unknown', 'Unknown error while loading observability metrics.', { cause: error, metricName })
  }

  const status = error.response?.status

  if (status === 401) {
    return new ObservabilityError('unauthorized', 'Actuator request failed with 401 (unauthorized).', { status, metricName, cause: error })
  }
  if (status === 403) {
    return new ObservabilityError('forbidden', 'Actuator request failed with 403 (forbidden).', { status, metricName, cause: error })
  }
  if (status === 404) {
    return new ObservabilityError(metricName ? 'metric-missing' : 'endpoint-missing', metricName ? `Metric is not exposed: ${metricName}` : 'Actuator endpoint is not available.', {
      status,
      metricName,
      cause: error,
    })
  }
  if (typeof status === 'number' && status >= 500) {
    return new ObservabilityError('server-error', `Actuator endpoint failed with ${status}.`, { status, metricName, cause: error })
  }
  if (!status) {
    return new ObservabilityError('network', 'Network error while contacting actuator endpoints.', { metricName, cause: error })
  }

  return new ObservabilityError('unknown', `Unexpected actuator error (${status}).`, { status, metricName, cause: error })
}

export function describeObservabilityError(error: unknown): string {
  if (!(error instanceof ObservabilityError)) {
    return 'Unable to load metrics from actuator endpoints. Verify ADMIN access and backend availability.'
  }

  switch (error.kind) {
    case 'unauthorized':
      return 'Actuator metrics request returned 401 Unauthorized. Sign in again with an ADMIN account.'
    case 'forbidden':
      return 'Actuator metrics request returned 403 Forbidden. Confirm this account has ADMIN role and actuator access.'
    case 'endpoint-missing':
      return 'Actuator metrics endpoint returned 404. Verify management endpoint exposure and reverse-proxy routing to /actuator.'
    case 'metric-missing': {
      const names = error.missingMetricNames?.length ? error.missingMetricNames.join(', ') : error.metricName
      return `Required metric is not exposed (${names ?? 'unknown metric'}). Verify meter registration and /actuator/metrics exposure.`
    }
    case 'server-error':
      return `Actuator endpoint failed with ${error.status ?? '5xx'}. Check backend logs and server health.`
    case 'network':
      return 'Network error reaching actuator endpoints. Verify base URL, proxy routing, CORS, and backend availability.'
    case 'unknown':
    default:
      return 'Unable to load metrics from actuator endpoints due to an unexpected error. Check browser network tab and backend logs.'
  }
}

async function fetchMetricCatalog() {
  try {
    const response = await actuatorClient.get<RawMetricCatalogResponse>('/metrics')
    const names = new Set(response.data.names ?? [])
    const missingMetricNames = REQUIRED_METRIC_NAMES.filter((metricName) => !names.has(metricName))
    if (missingMetricNames.length > 0) {
      throw new ObservabilityError('metric-missing', 'Required dashboard metrics are missing from actuator catalog.', {
        missingMetricNames,
      })
    }
  } catch (error) {
    if (error instanceof ObservabilityError) {
      throw error
    }
    throw buildObservabilityError(error)
  }
}

async function fetchMetric(name: string, tags: string[] = []) {
  try {
    const response = await actuatorClient.get<RawMetricResponse>(`/metrics/${name}`, {
      params: tags.length ? { tag: tags } : undefined,
      paramsSerializer: {
        serialize(params) {
          const entries = Array.isArray(params.tag) ? params.tag : []
          return entries.map((value) => `tag=${encodeURIComponent(value)}`).join('&')
        },
      },
    })

    return response.data
  } catch (error) {
    throw buildObservabilityError(error, name)
  }
}

function parseStatistic(value: string | undefined): MetricStatistic {
  switch (value) {
    case 'COUNT':
    case 'TOTAL':
    case 'TOTAL_TIME':
    case 'VALUE':
    case 'MAX':
      return value
    default:
      return 'UNKNOWN'
  }
}

function toNumber(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return 0
}

function parseMetric(raw: RawMetricResponse | null | undefined): MetricValue {
  const parsed: MetricValue = { count: 0, total: 0, max: 0, value: 0 }

  if (!Array.isArray(raw?.measurements)) {
    return parsed
  }

  for (const measurement of raw.measurements) {
    const statistic = parseStatistic(measurement.statistic)
    const value = toNumber(measurement.value)

    if (statistic === 'COUNT') {
      parsed.count = value
    } else if (statistic === 'TOTAL' || statistic === 'TOTAL_TIME') {
      parsed.total = value
    } else if (statistic === 'MAX') {
      parsed.max = value
    } else if (statistic === 'VALUE') {
      parsed.value = value
    }
  }

  return parsed
}

function ratio(numerator: number, denominator: number): number {
  if (denominator <= 0) {
    return 0
  }
  return numerator / denominator
}

export async function fetchOpsDashboardData(): Promise<OpsDashboardData> {
  await fetchMetricCatalog()

  const [lockSuccess, lockFail, lockSteal, lockRenewFail, skipLock, skipLocalInflight, dlqBacklog, dlqOldestAge, dispatchSuccess, dispatchFailed, dispatchLatency, deliveryLatency] =
    await Promise.all([
      fetchMetric('openpulse.scheduler.lock.acquire.success'),
      fetchMetric('openpulse.scheduler.lock.acquire.fail'),
      fetchMetric('openpulse.scheduler.lock.acquire.steal'),
      fetchMetric('openpulse.scheduler.lock.renew.fail'),
      fetchMetric('openpulse.scheduler.execution.skip.lock'),
      fetchMetric('openpulse.scheduler.execution.skip.local_inflight'),
      fetchMetric('openpulse.alerts.dlq.backlog'),
      fetchMetric('openpulse.alerts.dlq.oldest.age.seconds'),
      fetchMetric('openpulse.alerts.dispatch.attempts', ['outcome:success']),
      fetchMetric('openpulse.alerts.dispatch.attempts', ['outcome:failed']),
      fetchMetric('openpulse.alerts.dispatch.latency', ['outcome:success']),
      fetchMetric('openpulse.alerts.delivery.delay', ['outcome:success']),
    ])

  const lockSuccessValue = parseMetric(lockSuccess).count
  const lockFailValue = parseMetric(lockFail).count
  const lockStealValue = parseMetric(lockSteal).count
  const lockRenewFailValue = parseMetric(lockRenewFail).count

  const skipLockValue = parseMetric(skipLock).count
  const skipLocalInflightValue = parseMetric(skipLocalInflight).count

  const dlqBacklogValue = parseMetric(dlqBacklog).value
  const dlqOldestAgeValue = parseMetric(dlqOldestAge).value

  const dispatchSuccessValue = parseMetric(dispatchSuccess).count
  const dispatchFailedValue = parseMetric(dispatchFailed).count

  const dispatchLatencyValue = parseMetric(dispatchLatency)
  const deliveryLatencyValue = parseMetric(deliveryLatency)

  return {
    lock: {
      contentionRatio: ratio(lockFailValue, lockSuccessValue + lockFailValue),
      lockAcquireSuccess: lockSuccessValue,
      lockAcquireFail: lockFailValue,
      lockAcquireSteal: lockStealValue,
      lockRenewFail: lockRenewFailValue,
    },
    scheduler: {
      skipLock: skipLockValue,
      skipLocalInflight: skipLocalInflightValue,
      skipLockRatio: ratio(skipLockValue, skipLockValue + skipLocalInflightValue),
    },
    dlq: {
      backlog: dlqBacklogValue,
      oldestAgeSeconds: dlqOldestAgeValue,
    },
    notifier: {
      dispatchSuccess: dispatchSuccessValue,
      dispatchFailed: dispatchFailedValue,
      failureRatio: ratio(dispatchFailedValue, dispatchSuccessValue + dispatchFailedValue),
    },
    latency: {
      dispatchMeanSeconds: ratio(dispatchLatencyValue.total, dispatchLatencyValue.count),
      dispatchMaxSeconds: dispatchLatencyValue.max,
      deliveryMeanSeconds: ratio(deliveryLatencyValue.total, deliveryLatencyValue.count),
      deliveryMaxSeconds: deliveryLatencyValue.max,
    },
  }
}

export function useOpsDashboardQuery(refreshIntervalMs: number, enabled: boolean) {
  return useQuery({
    queryKey: ['dashboard', 'ops-observability'],
    queryFn: fetchOpsDashboardData,
    refetchInterval: enabled ? refreshIntervalMs : false,
  })
}
