import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { CheckResult, CreateMonitorPayload, Monitor, UpdateMonitorPayload } from '../../types/monitor'

interface ApiErrorBody {
  error?: string
}

export function getMonitorApiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: ApiErrorBody } }
  return maybe.response?.data?.error ?? fallback
}

export function useMonitorsQuery() {
  return useQuery<Monitor[]>({
    queryKey: ['monitors'],
    queryFn: async () => {
      const response = await apiClient.get<Monitor[]>('/monitors')
      return response.data
    },
  })
}

export function useMonitorDetailQuery(id: string | null) {
  return useQuery<Monitor>({
    queryKey: ['monitors', id],
    enabled: Boolean(id),
    queryFn: async () => {
      const response = await apiClient.get<Monitor>(`/monitors/${id}`)
      return response.data
    },
  })
}

export function useCreateMonitorMutation() {
  return useMutation({
    mutationFn: async (payload: CreateMonitorPayload) => {
      const response = await apiClient.post<Monitor>('/monitors', payload)
      return response.data
    },
  })
}

export function useUpdateMonitorMutation() {
  return useMutation({
    mutationFn: async (payload: { id: string; data: UpdateMonitorPayload }) => {
      const response = await apiClient.put<Monitor>(`/monitors/${payload.id}`, payload.data)
      return response.data
    },
  })
}

export function useToggleMonitorMutation() {
  return useMutation({
    mutationFn: async (payload: { id: string; enabled: boolean }) => {
      const response = await apiClient.patch<Monitor>(`/monitors/${payload.id}/enabled`, { enabled: payload.enabled })
      return response.data
    },
  })
}

export function useRunMonitorCheckMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await apiClient.post<CheckResult>(`/monitors/${id}/run-check`)
      return response.data
    },
  })
}

export function useDeleteMonitorMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/monitors/${id}`)
    },
  })
}
