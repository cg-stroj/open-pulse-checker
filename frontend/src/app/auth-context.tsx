import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { notify } from '../components/feedback/toast'
import { apiClient, registerApiAuthErrorHandlers } from '../lib/api/client'
import { clearAuthSession, encodeBasicAuthorization, readAuthSession, writeAuthSession } from '../lib/auth/session'
import { AuthContext, type AuthContextValue } from './auth-store'

function getErrorCode(error: unknown) {
  const maybe = error as { response?: { status?: number } }
  return maybe.response?.status ?? 0
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [session, setSession] = useState(() => readAuthSession())
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  const [loginError, setLoginError] = useState<string | null>(null)
  const redirectLockRef = useRef(false)

  useEffect(() => {
    return registerApiAuthErrorHandlers({
      onUnauthorized: () => {
        if (redirectLockRef.current) return
        redirectLockRef.current = true

        clearAuthSession()
        setSession(null)
        notify.error('Your session is invalid or expired. Please sign in again.')
        navigate('/login', { replace: true })
        window.setTimeout(() => {
          redirectLockRef.current = false
        }, 600)
      },
      onForbidden: () => {
        if (window.location.pathname !== '/unauthorized') {
          notify.error('Your account does not have admin access for this action.')
          navigate('/unauthorized')
        }
      },
    })
  }, [navigate])

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: Boolean(session),
      username: session?.username ?? null,
      isLoggingIn,
      loginError,
      login: async ({ username, password }) => {
        const cleanedUsername = username.trim()
        if (!cleanedUsername || !password) {
          setLoginError('Username and password are required.')
          return false
        }

        setIsLoggingIn(true)
        setLoginError(null)

        try {
          const authorizationHeader = encodeBasicAuthorization(cleanedUsername, password)
          const nextSession = { username: cleanedUsername, authorizationHeader }

          await apiClient.get('/admin/auth/login', {
            headers: {
              Authorization: authorizationHeader,
            },
          })

          writeAuthSession(nextSession)
          setSession(nextSession)
          notify.success('Signed in successfully.')
          navigate('/dashboard', { replace: true })
          return true
        } catch (error) {
          const status = getErrorCode(error)
          if (status === 401) {
            setLoginError('Invalid credentials. Please verify username and password.')
          } else if (status === 403) {
            setLoginError('Login succeeded, but this account lacks ADMIN role access.')
          } else {
            setLoginError('Sign in failed due to network or API issue. Please retry.')
          }
          clearAuthSession()
          setSession(null)
          return false
        } finally {
          setIsLoggingIn(false)
        }
      },
      logout: () => {
        clearAuthSession()
        setSession(null)
        notify.info('Signed out.')
        navigate('/login', { replace: true })
      },
    }),
    [isLoggingIn, loginError, navigate, session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
