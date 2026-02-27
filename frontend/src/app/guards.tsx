import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './auth-hooks'

export function ProtectedRoute() {
  const auth = useAuth()
  const location = useLocation()

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}

export function LoginRoute() {
  const auth = useAuth()

  if (auth.isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
