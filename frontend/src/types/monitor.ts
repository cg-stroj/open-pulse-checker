export type MonitorType = 'HTTP' | 'TCP' | 'PING'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'HEAD'
export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface Monitor {
  id: string
  name: string
  type: MonitorType
  targetUrl: string
  httpMethod?: HttpMethod | null
  expectedResponseKeyword?: string | null
  intervalSec: number
  enabled: boolean
  timeoutMs: number
  lastCheckAt: string | null
  lastCheckStatus: CheckStatus | null
  lastStatusCode: number | null
  lastLatencyMs: number | null
  createdAt: string
  updatedAt: string
}

export interface CreateMonitorPayload {
  name: string
  type: MonitorType
  targetUrl: string
  httpMethod?: HttpMethod
  expectedResponseKeyword?: string
  intervalSec: number
  enabled?: boolean
  timeoutMs: number
}

export interface UpdateMonitorPayload {
  name: string
  type: MonitorType
  targetUrl: string
  httpMethod?: HttpMethod
  expectedResponseKeyword?: string
  intervalSec: number
  enabled: boolean
  timeoutMs: number
}

export interface CheckResult {
  id: string
  monitorId: string
  status: CheckStatus
  statusCode: number | null
  latencyMs: number | null
  checkedAt: string
  error: string | null
}
