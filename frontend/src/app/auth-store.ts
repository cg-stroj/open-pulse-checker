import { createContext } from 'react'

interface LoginPayload {
  username: string
  password: string
}

export interface AuthContextValue {
  isAuthenticated: boolean
  username: string | null
  isLoggingIn: boolean
  loginError: string | null
  login: (payload: LoginPayload) => Promise<boolean>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
