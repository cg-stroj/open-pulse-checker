import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
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

function buildActuatorBaseUrl() {
  const fallbackOrigin = typeof window === 'undefined' ? 'http://localhost:8080' : window.location.origin
  const apiUrl = new URL(appConfig.apiBaseUrl, fallbackOrigin)
  return `${apiUrl.origin}/actuator`
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

async function fetchMetric(name: string, tags: string[] = []) {
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
