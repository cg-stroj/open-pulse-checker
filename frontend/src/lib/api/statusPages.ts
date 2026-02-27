import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export interface StatusPage {
  id: string
  name: string
  slug: string
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateStatusPagePayload {
  name: string
  slug: string
  isPublic: boolean
}

export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'
export type StatusPageOverallStatus = 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE'

export interface PublicMonitorSummary {
  monitorId: string
  monitorName: string
  displayOrder: number
  currentStatus: CheckStatus
  statusCode: number | null
  latencyMs: number | null
  checkedAt: string | null
}

export interface PublicIncidentTimelineItem {
  incidentId: string
  monitorId: string
  monitorName: string
  state: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  openedAt: string
  resolvedAt: string | null
  reason: string
}

export interface PublicStatusPage {
  page: StatusPage
  overallStatus: StatusPageOverallStatus
  monitors: PublicMonitorSummary[]
  incidents: PublicIncidentTimelineItem[]
}

interface ApiErrorBody {
  error?: string
}

export function getStatusPageApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: ApiErrorBody } }
  return maybe.response?.data?.error ?? fallback
}

export function useStatusPagesQuery() {
  return useQuery<StatusPage[]>({
    queryKey: ['status-pages'],
    queryFn: async () => {
      const response = await apiClient.get<StatusPage[]>('/status-pages')
      return response.data
    },
  })
}

export function useCreateStatusPageMutation() {
  return useMutation({
    mutationFn: async (payload: CreateStatusPagePayload) => {
      const response = await apiClient.post<StatusPage>('/status-pages', payload)
      return response.data
    },
  })
}

export function useAttachStatusPageMonitorsMutation() {
  return useMutation({
    mutationFn: async (payload: { pageId: string; monitorIds: string[] }) => {
      const response = await apiClient.post<PublicMonitorSummary[]>(`/status-pages/${payload.pageId}/monitors`, {
        monitorIds: payload.monitorIds,
      })
      return response.data
    },
  })
}

export function useRemoveStatusPageMonitorMutation() {
  return useMutation({
    mutationFn: async (payload: { pageId: string; monitorId: string }) => {
      await apiClient.delete(`/status-pages/${payload.pageId}/monitors/${payload.monitorId}`)
    },
  })
}

export function usePublicStatusPageQuery(slug: string | null) {
  return useQuery<PublicStatusPage>({
    queryKey: ['status-pages', 'public', slug],
    enabled: Boolean(slug),
    retry: false,
    queryFn: async () => {
      const response = await apiClient.get<PublicStatusPage>(`/public/status-pages/${slug}`)
      return response.data
    },
  })
}
