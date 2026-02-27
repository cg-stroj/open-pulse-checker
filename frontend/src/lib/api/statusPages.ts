import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

export interface StatusPage {
  id: string
  name: string
  slug: string
  isPublic: boolean
  createdAt: string
  updatedAt: string
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
