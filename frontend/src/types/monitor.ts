export type MonitorType = 'HTTP' | 'TCP' | 'PING'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'HEAD'
export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface Monitor {
  id: string
  name: string
  type: MonitorType
  targetUrl: string
  intervalSec: number
  enabled: boolean
  timeoutMs: number
  httpMethod: HttpMethod | null
  expectedResponseKeyword: string | null
  emailAlertOnDown: boolean
  emailAlertOnRecovery: boolean
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
  intervalSec: number
  enabled?: boolean
  timeoutMs: number
  httpMethod?: HttpMethod
  expectedResponseKeyword?: string
  emailAlertOnDown?: boolean
  emailAlertOnRecovery?: boolean
}

export interface UpdateMonitorPayload {
  name: string
  type: MonitorType
  targetUrl: string
  intervalSec: number
  enabled: boolean
  timeoutMs: number
  httpMethod?: HttpMethod
  expectedResponseKeyword?: string
  emailAlertOnDown?: boolean
  emailAlertOnRecovery?: boolean
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
