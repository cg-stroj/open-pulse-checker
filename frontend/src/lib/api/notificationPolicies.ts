import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export type NotificationPolicyScopeType = 'GLOBAL' | 'STATUS_PAGE' | 'MONITOR'
export type NotificationSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

export type NotificationChannel = 'WEBHOOK' | 'EMAIL' | 'TELEGRAM' | 'SLACK' | 'DISCORD' | 'TEAMS'

export interface NotificationRouteRule {
  severity: NotificationSeverity
  channels: NotificationChannel[]
}

export interface NotificationEscalationStep {
  stepOrder: number
  afterSeconds: number
  minSeverity: NotificationSeverity
  channels: NotificationChannel[]
}

export interface NotificationPolicy {
  id: string
  scopeType: NotificationPolicyScopeType
  scopeRefId: string | null
  enabled: boolean
  cooldownSeconds: number
  dedupSeconds: number
  routes: NotificationRouteRule[]
  escalationSteps: NotificationEscalationStep[]
  createdAt: string
  updatedAt: string
}

export interface UpsertNotificationPolicyPayload {
  scopeType: NotificationPolicyScopeType
  scopeRefId: string | null
  enabled: boolean
  cooldownSeconds: number
  dedupSeconds: number
  routes: NotificationRouteRule[]
  escalationSteps: NotificationEscalationStep[]
}

interface ApiErrorBody {
  error?: string
}

export function getNotificationPolicyApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: ApiErrorBody } }
  return maybe.response?.data?.error ?? fallback
}

export function useNotificationPoliciesQuery() {
  return useQuery<NotificationPolicy[]>({
    queryKey: ['admin-notification-policies'],
    queryFn: async () => {
      const response = await apiClient.get<NotificationPolicy[]>('/admin/notification-policies')
      return response.data
    },
  })
}

export function useCreateNotificationPolicyMutation() {
  return useMutation({
    mutationFn: async (payload: UpsertNotificationPolicyPayload) => {
      const response = await apiClient.post<NotificationPolicy>('/admin/notification-policies', payload)
      return response.data
    },
  })
}

export function useUpdateNotificationPolicyMutation() {
  return useMutation({
    mutationFn: async (payload: { id: string; data: UpsertNotificationPolicyPayload }) => {
      const response = await apiClient.put<NotificationPolicy>(`/admin/notification-policies/${payload.id}`, payload.data)
      return response.data
    },
  })
}

export function useTestNotificationPolicyMutation() {
  return useMutation({
    mutationFn: async (payload: { id: string; channels?: NotificationChannel[]; reason?: string }) => {
      const response = await apiClient.post(`/admin/notification-policies/${payload.id}/test`, {
        channels: payload.channels,
        reason: payload.reason,
      })
      return response.data
    },
  })
}
