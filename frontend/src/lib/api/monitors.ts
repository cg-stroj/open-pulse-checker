import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Monitor } from '../../types/monitor'

export async function fetchMonitors() {
  const response = await apiClient.get<Monitor[]>('/monitors')
  return response.data
}

export function useMonitorsQuery() {
  return useQuery({
    queryKey: ['monitors'],
    queryFn: fetchMonitors,
  })
}
