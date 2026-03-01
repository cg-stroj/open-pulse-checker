import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { notify } from '../components/feedback/toast'
import { ErrorState, LoadingState } from '../components/states/States'
import { Button } from '../components/ui/Button'
import { Field, TextInput } from '../components/ui/FormControls'
import { useAuth } from '../app/auth-hooks'
import { getSetupApiErrorMessage, useCreateFirstAdminMutation, useSetupStatusQuery } from '../lib/api/setup'

function getErrorCode(error: unknown) {
  const maybe = error as { response?: { status?: number } }
  return maybe.response?.status ?? 0
}

function mapCreateError(error: unknown) {
  const status = getErrorCode(error)
  const message = getSetupApiErrorMessage(error, 'Setup failed due to network or API issue. Please retry.')

  if (status === 400) {
    return message
  }

  if (status === 409) {
    return 'Setup is already completed and locked. Please sign in with an existing admin account.'
  }

  if (status === 0) {
    return 'Network error while completing setup. Check API availability and retry.'
  }

  if (message.toLowerCase().includes('token')) {
    return 'Setup token is invalid or expired. Reload this page to request a fresh setup session.'
  }

  return message
}

export function SetupPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const setupStatusQuery = useSetupStatusQuery(true)
  const createFirstAdminMutation = useCreateFirstAdminMutation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmPolicy, setConfirmPolicy] = useState(false)
  const [confirmSecurity, setConfirmSecurity] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const setupToken = setupStatusQuery.data?.setupToken ?? null
  const canSubmit = useMemo(() => {
    return Boolean(username.trim() && password && confirmPassword && confirmPolicy && confirmSecurity)
  }, [confirmPolicy, confirmSecurity, confirmPassword, password, username])

  if (setupStatusQuery.isLoading) {
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-3xl items-center p-4">
        <LoadingState title="Preparing first-run setup" description="Checking setup status and requesting secure one-time token." />
      </div>
    )
  }

  if (setupStatusQuery.isError || !setupStatusQuery.data) {
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-3xl items-center p-4">
        <ErrorState
          title="Could not load setup status"
          description="We could not contact the API to initialize onboarding. Verify connectivity and retry."
          action={
            <Button variant="secondary" onClick={() => void setupStatusQuery.refetch()}>
              Retry
            </Button>
          }
        />
      </div>
    )
  }

  if (setupStatusQuery.data.setupLocked || !setupStatusQuery.data.setupRequired) {
    return <Navigate to={auth.isAuthenticated ? '/dashboard' : '/login'} replace />
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cleanedUsername = username.trim()

    if (!cleanedUsername) {
      setFormError('Owner email or username is required.')
      return
    }

    if (password.length < 12) {
      setFormError('Password must be at least 12 characters long.')
      return
    }

    if (password !== confirmPassword) {
      setFormError('Password confirmation does not match.')
      return
    }

    if (!confirmPolicy || !confirmSecurity) {
      setFormError('Please confirm the setup policy and credential security checks.')
      return
    }

    if (!setupToken) {
      setFormError('Setup token is missing or expired. Reload the page to request a new token.')
      return
    }

    setFormError(null)

    try {
      await createFirstAdminMutation.mutateAsync({
        username: cleanedUsername,
        password,
        setupToken,
      })
      notify.success('First admin account created. Please sign in.')
      navigate('/login', { replace: true, state: { presetUsername: cleanedUsername } })
    } catch (error) {
      setFormError(mapCreateError(error))
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-base p-4">
      <section className="w-full max-w-xl space-y-5 rounded-lg border border-surface-border bg-bg-elevated p-6">
        <div>
          <h1 className="text-2xl font-semibold">Initial admin setup</h1>
          <p className="text-sm text-text-secondary">Create the first owner account to finish Open Pulse Checker onboarding.</p>
        </div>

        <form className="space-y-3" onSubmit={onSubmit}>
          <Field label="Owner email or username">
            <TextInput autoComplete="username" maxLength={120} value={username} onChange={(event) => setUsername(event.target.value)} />
          </Field>

          <Field label="Password">
            <TextInput type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </Field>

          <Field label="Confirm password">
            <TextInput type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
          </Field>

          <label className="flex items-start gap-2 text-sm text-text-secondary">
            <input
              type="checkbox"
              className="mt-1"
              checked={confirmPolicy}
              onChange={(event) => setConfirmPolicy(event.target.checked)}
            />
            <span>I understand this account receives full administrative access and must follow organization policy.</span>
          </label>

          <label className="flex items-start gap-2 text-sm text-text-secondary">
            <input
              type="checkbox"
              className="mt-1"
              checked={confirmSecurity}
              onChange={(event) => setConfirmSecurity(event.target.checked)}
            />
            <span>I confirm the credentials are stored securely and not shared in plain text.</span>
          </label>

          {formError ? <p className="text-sm text-red-400">{formError}</p> : null}

          <Button type="submit" disabled={createFirstAdminMutation.isPending || !canSubmit} className="w-full">
            {createFirstAdminMutation.isPending ? 'Creating admin…' : 'Complete setup'}
          </Button>
        </form>
      </section>
    </div>
  )
}
