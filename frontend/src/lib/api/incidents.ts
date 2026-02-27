import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export type IncidentState = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
export type IncidentManualAction = 'ACKNOWLEDGED' | 'ANNOTATION_ADDED' | 'RESOLVED_MANUALLY' | 'REOPENED'

export interface AdminIncident {
  id: string
  monitorId: string
  monitorName: string
  state: IncidentState
  openedAt: string
  resolvedAt: string | null
  reason: string
}

export interface AdminIncidentEvent {
  id: string
  action: IncidentManualAction
  actor: string
  reason: string
  fromState: IncidentState
  toState: IncidentState
  occurredAt: string
}

interface IncidentActionPayload {
  incidentId: string
  reason: string
}

interface ApiErrorBody {
  error?: string
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: ApiErrorBody } }
  return maybe.response?.data?.error ?? fallback
}

export function useAdminIncidentsQuery() {
  return useQuery<AdminIncident[]>({
    queryKey: ['admin-incidents'],
    queryFn: async () => {
      const response = await apiClient.get<AdminIncident[]>('/admin/incidents')
      return response.data
    },
  })
}

export function useIncidentEventsQuery(incidentId: string | null) {
  return useQuery<AdminIncidentEvent[]>({
    queryKey: ['admin-incidents', incidentId, 'events'],
    enabled: Boolean(incidentId),
    queryFn: async () => {
      const response = await apiClient.get<AdminIncidentEvent[]>(`/admin/incidents/${incidentId}/events`)
      return response.data
    },
  })
}

function postIncidentAction(path: string, payload: IncidentActionPayload) {
  return apiClient.post<AdminIncident>(`/admin/incidents/${payload.incidentId}/${path}`, { reason: payload.reason })
}

export function useAcknowledgeIncidentMutation() {
  return useMutation({ mutationFn: (payload: IncidentActionPayload) => postIncidentAction('acknowledge', payload) })
}

export function useResolveIncidentMutation() {
  return useMutation({ mutationFn: (payload: IncidentActionPayload) => postIncidentAction('resolve', payload) })
}

export function useReopenIncidentMutation() {
  return useMutation({ mutationFn: (payload: IncidentActionPayload) => postIncidentAction('reopen', payload) })
}

export function useAnnotateIncidentMutation() {
  return useMutation({ mutationFn: (payload: IncidentActionPayload) => postIncidentAction('annotations', payload) })
}
