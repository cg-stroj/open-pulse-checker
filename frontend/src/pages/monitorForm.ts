import type { HttpMethod, Monitor, MonitorType } from '../types/monitor'

export interface FormState {
  name: string
  type: MonitorType
  targetUrl: string
  intervalSec: string
  timeoutMs: string
  enabled: boolean
  httpMethod: HttpMethod
  expectedResponseKeyword: string
  emailAlertOnDown: boolean
  emailAlertOnRecovery: boolean
}

export interface FormErrors {
  name?: string
  targetUrl?: string
  intervalSec?: string
  timeoutMs?: string
}

export function initFormState(): FormState {
  return {
    name: '',
    type: 'HTTP',
    targetUrl: '',
    intervalSec: '60',
    timeoutMs: '1200',
    enabled: true,
    httpMethod: 'GET',
    expectedResponseKeyword: '',
    emailAlertOnDown: true,
    emailAlertOnRecovery: true,
  }
}

export function fromMonitor(monitor: Monitor): FormState {
  return {
    name: monitor.name,
    type: monitor.type ?? 'HTTP',
    targetUrl: monitor.targetUrl,
    intervalSec: String(monitor.intervalSec),
    timeoutMs: String(monitor.timeoutMs),
    enabled: monitor.enabled,
    httpMethod: monitor.httpMethod ?? 'GET',
    expectedResponseKeyword: monitor.expectedResponseKeyword ?? '',
    emailAlertOnDown: monitor.emailAlertOnDown,
    emailAlertOnRecovery: monitor.emailAlertOnRecovery,
  }
}

export function parseInteger(value: string) {
  if (!/^\d+$/.test(value.trim())) return null
  return Number(value)
}

export function validateMonitorForm(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.name.trim()) {
    errors.name = 'Name is required.'
  }

  if (!form.targetUrl.trim()) {
    errors.targetUrl = form.type === 'HTTP' ? 'Target URL is required.' : 'Target is required.'
  } else if (form.type === 'HTTP') {
    try {
      const parsed = new URL(form.targetUrl.trim())
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        errors.targetUrl = 'URL must use http or https.'
      }
    } catch {
      errors.targetUrl = 'Provide a valid URL.'
    }
  } else if (form.type === 'TCP') {
    const tcpTarget = form.targetUrl.trim()
    if (!/^[^:\s]+:\d+$/.test(tcpTarget)) {
      errors.targetUrl = 'TCP target must be host:port (for example localhost:5432).'
    }
  } else {
    const pingTarget = form.targetUrl.trim()
    if (/\s/.test(pingTarget) || pingTarget.includes(':')) {
      errors.targetUrl = 'PING target must be a hostname or IP address.'
    }
  }

  const interval = parseInteger(form.intervalSec)
  if (interval === null || ![60, 120, 180, 240, 300].includes(interval)) {
    errors.intervalSec = 'Interval must be one of 60, 120, 180, 240, 300 seconds (1-5 minutes).'
  }

  const timeout = parseInteger(form.timeoutMs)
  if (timeout === null || timeout < 100 || timeout > 120000) {
    errors.timeoutMs = 'Timeout must be an integer between 100 and 120000 ms.'
  }

  return errors
}

export function hasErrors(errors: FormErrors) {
  return Object.values(errors).some(Boolean)
}
