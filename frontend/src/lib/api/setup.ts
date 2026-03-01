import { useMutation, useQuery } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { apiClient } from './client'

export interface SetupStatusResponse {
  setupRequired: boolean
  setupLocked: boolean
  setupToken: string | null
  setupTokenExpiresAt: string | null
}

export interface CreateFirstAdminPayload {
  username: string
  password: string
  setupToken: string
}

interface ApiErrorBody {
  error?: string
}

export function useSetupStatusQuery(enabled = true) {
  return useQuery({
    queryKey: ['setup', 'status'],
    enabled,
    retry: false,
    staleTime: 0,
    queryFn: async () => {
      const response = await apiClient.get<SetupStatusResponse>('/setup/status')
      return response.data
    },
  })
}

export function useCreateFirstAdminMutation() {
  return useMutation({
    mutationFn: async (payload: CreateFirstAdminPayload) => {
      const response = await apiClient.post('/setup/first-admin', payload)
      return response.data
    },
  })
}

export function getSetupApiErrorMessage(error: unknown, fallback: string) {
  const typed = error as AxiosError<ApiErrorBody>
  return typed.response?.data?.error ?? fallback
}
