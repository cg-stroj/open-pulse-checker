import axios from 'axios'
import { readAuthSession } from '../auth/session'
import { appConfig } from '../config/app'

export const apiClient = axios.create({
  baseURL: appConfig.apiBaseUrl,
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

interface ApiAuthErrorHandlers {
  onUnauthorized: () => void
  onForbidden: () => void
}

let authErrorHandlers: ApiAuthErrorHandlers | null = null

export function registerApiAuthErrorHandlers(handlers: ApiAuthErrorHandlers) {
  authErrorHandlers = handlers
  return () => {
    authErrorHandlers = null
  }
}

apiClient.interceptors.request.use((config) => {
  const authSession = readAuthSession()
  if (authSession?.authorizationHeader) {
    config.headers.Authorization = authSession.authorizationHeader
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status as number | undefined
    if (status === 401) {
      authErrorHandlers?.onUnauthorized()
    } else if (status === 403) {
      authErrorHandlers?.onForbidden()
    }
    return Promise.reject(error)
  },
)
