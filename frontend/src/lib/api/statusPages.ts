import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export interface StatusPageBranding {
  brandName: string | null
  brandTheme: string | null
  brandLogoUrl: string | null
  brandCustomHeader: string | null
  brandCustomFooter: string | null
}

export interface StatusPage {
  id: string
  name: string
  slug: string
  isPublic: boolean
  branding: StatusPageBranding
  createdAt: string
  updatedAt: string
}

export interface CreateStatusPagePayload {
  name: string
  slug: string
  isPublic: boolean
}

export interface UpdateStatusPagePayload {
  name?: string
  slug?: string
  isPublic?: boolean
  brandName?: string
  brandTheme?: string
  brandLogoUrl?: string
  brandCustomHeader?: string
  brandCustomFooter?: string
}

export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'
export type StatusPageOverallStatus = 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE'

export interface PublicComponentGroup {
  id: string
  name: string
  displayOrder: number
}

export interface PublicMaintenanceAnnouncement {
  id: string
  title: string
  message: string
  publishAt: string
  startsAt: string | null
  endsAt: string | null
}

export interface PublicMonitorSummary {
  monitorId: string
  monitorName: string
  displayOrder: number
  componentGroupId: string | null
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
  componentGroups: PublicComponentGroup[]
  maintenanceAnnouncements: PublicMaintenanceAnnouncement[]
  monitors: PublicMonitorSummary[]
  incidents: PublicIncidentTimelineItem[]
}

export interface StatusPageV2Config {
  componentGroups: Array<{ id: string; name: string; displayOrder: number }>
  monitorBindings: Array<{ monitorId: string; displayOrder: number; componentGroupId: string | null }>
  maintenanceAnnouncements: Array<{
    id: string
    title: string
    message: string
    publishAt: string
    startsAt: string | null
    endsAt: string | null
    isPublic: boolean
  }>
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

export function useUpdateStatusPageMutation() {
  return useMutation({
    mutationFn: async (payload: { pageId: string; data: UpdateStatusPagePayload }) => {
      const response = await apiClient.put<StatusPage>(`/status-pages/${payload.pageId}`, payload.data)
      return response.data
    },
  })
}

export function useStatusPageV2ConfigQuery(pageId: string | null) {
  return useQuery<StatusPageV2Config>({
    queryKey: ['status-pages', pageId, 'config'],
    enabled: Boolean(pageId),
    queryFn: async () => {
      const response = await apiClient.get<StatusPageV2Config>(`/status-pages/${pageId}/config`)
      return response.data
    },
  })
}

export function useUpsertStatusPageV2ConfigMutation() {
  return useMutation({
    mutationFn: async (payload: { pageId: string; data: StatusPageV2Config }) => {
      const response = await apiClient.put<StatusPageV2Config>(`/status-pages/${payload.pageId}/config`, payload.data)
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
