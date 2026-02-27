const SESSION_STORAGE_KEY = 'opc.admin.auth'

export interface AuthSession {
  username: string
  authorizationHeader: string
}

function isBrowser() {
  return typeof window !== 'undefined' && Boolean(window.sessionStorage)
}

export function encodeBasicAuthorization(username: string, password: string) {
  const normalized = `${username}:${password}`
  return `Basic ${window.btoa(normalized)}`
}

export function readAuthSession(): AuthSession | null {
  if (!isBrowser()) return null

  const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as Partial<AuthSession>
    if (!parsed.username || !parsed.authorizationHeader) return null
    return { username: parsed.username, authorizationHeader: parsed.authorizationHeader }
  } catch {
    return null
  }
}

export function writeAuthSession(session: AuthSession) {
  if (!isBrowser()) return
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearAuthSession() {
  if (!isBrowser()) return
  window.sessionStorage.removeItem(SESSION_STORAGE_KEY)
}
