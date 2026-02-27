import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export interface AuditEvent {
  id: string
  actor: string
  action: string
  resource: string
  outcome: string
  details: string | null
  occurredAt: string
}

export interface AuditEventsPage {
  items: AuditEvent[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface AuditEventFilters {
  q?: string
  actor?: string
  action?: string
  resource?: string
  outcome?: string
  fromAt?: string
  toAt?: string
}

export interface AuditEventsQueryParams extends AuditEventFilters {
  page: number
  size: number
}

export function useAuditEventsQuery(params: AuditEventsQueryParams) {
  return useQuery<AuditEventsPage>({
    queryKey: ['admin-audit-events', params],
    queryFn: async () => {
      const response = await apiClient.get<AuditEventsPage>('/admin/audit-events', {
        params: {
          page: params.page,
          size: params.size,
          q: params.q?.trim() || undefined,
          actor: params.actor?.trim() || undefined,
          action: params.action?.trim() || undefined,
          resource: params.resource?.trim() || undefined,
          outcome: params.outcome?.trim() || undefined,
          fromAt: params.fromAt || undefined,
          toAt: params.toAt || undefined,
        },
      })
      return response.data
    },
  })
}

export async function exportAuditEvents(format: 'csv' | 'json', filters: AuditEventFilters) {
  const response = await apiClient.get<Blob>('/admin/audit-events/export', {
    params: {
      format,
      q: filters.q?.trim() || undefined,
      actor: filters.actor?.trim() || undefined,
      action: filters.action?.trim() || undefined,
      resource: filters.resource?.trim() || undefined,
      outcome: filters.outcome?.trim() || undefined,
      fromAt: filters.fromAt || undefined,
      toAt: filters.toAt || undefined,
    },
    responseType: 'blob',
  })

  const blob = new Blob([response.data], {
    type: format === 'csv' ? 'text/csv;charset=utf-8' : 'application/json;charset=utf-8',
  })

  const contentDisposition = response.headers['content-disposition'] as string | undefined
  const match = contentDisposition?.match(/filename=([^;]+)/i)
  const fallback = `audit-events.${format}`
  const filename = (match?.[1] ?? fallback).replace(/"/g, '').trim()

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
