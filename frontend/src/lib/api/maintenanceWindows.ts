import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export type MaintenanceWindowScopeType = 'GLOBAL' | 'MONITOR'
export type MaintenanceWindowType = 'ONE_TIME' | 'RECURRING'
export type MaintenancePolicy = 'SUPPRESS' | 'ANNOTATE'

export interface MaintenanceWindow {
  id: string
  name: string
  scopeType: MaintenanceWindowScopeType
  scopeRefId: string | null
  type: MaintenanceWindowType
  policy: MaintenancePolicy
  enabled: boolean
  startAt: string | null
  endAt: string | null
  timezone: string | null
  recurringDays: string[]
  recurringStartTime: string | null
  recurringEndTime: string | null
  createdAt: string
  updatedAt: string
}

export interface UpsertMaintenanceWindowPayload {
  name: string
  scopeType: MaintenanceWindowScopeType
  scopeRefId: string | null
  type: MaintenanceWindowType
  policy: MaintenancePolicy
  enabled: boolean
  startAt: string | null
  endAt: string | null
  timezone: string | null
  recurringDays: string[]
  recurringStartTime: string | null
  recurringEndTime: string | null
}

interface ApiErrorBody {
  error?: string
}

export function getMaintenanceApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: ApiErrorBody } }
  return maybe.response?.data?.error ?? fallback
}

export function useMaintenanceWindowsQuery() {
  return useQuery<MaintenanceWindow[]>({
    queryKey: ['admin-maintenance-windows'],
    queryFn: async () => {
      const response = await apiClient.get<MaintenanceWindow[]>('/admin/maintenance-windows')
      return response.data
    },
  })
}

export function useCreateMaintenanceWindowMutation() {
  return useMutation({
    mutationFn: async (payload: UpsertMaintenanceWindowPayload) => {
      const response = await apiClient.post<MaintenanceWindow>('/admin/maintenance-windows', payload)
      return response.data
    },
  })
}

export function useUpdateMaintenanceWindowMutation() {
  return useMutation({
    mutationFn: async (payload: { id: string; data: UpsertMaintenanceWindowPayload }) => {
      const response = await apiClient.put<MaintenanceWindow>(`/admin/maintenance-windows/${payload.id}`, payload.data)
      return response.data
    },
  })
}

export function useDeleteMaintenanceWindowMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/admin/maintenance-windows/${id}`)
    },
  })
}
