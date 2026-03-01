import { type FormEvent, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../app/auth-hooks'
import { LoadingState } from '../components/states/States'
import { Button } from '../components/ui/Button'
import { Field, TextInput } from '../components/ui/FormControls'
import { useSetupStatusQuery } from '../lib/api/setup'

export function LoginPage() {
  const auth = useAuth()
  const location = useLocation()
  const setupStatusQuery = useSetupStatusQuery(true)
  const [username, setUsername] = useState(() => (location.state as { presetUsername?: string } | null)?.presetUsername ?? '')
  const [password, setPassword] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const ok = await auth.login({ username, password })
    if (ok) {
      setPassword('')
    }
  }

  if (setupStatusQuery.isLoading) {
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-3xl items-center p-4">
        <LoadingState title="Loading sign in" description="Checking if initial setup is already completed." />
      </div>
    )
  }

  if (setupStatusQuery.data?.setupRequired && !setupStatusQuery.data.setupLocked) {
    return <Navigate to="/setup" replace />
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-base p-4">
      <section className="w-full max-w-md space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-6">
        <div>
          <h1 className="text-2xl font-semibold">Admin sign in</h1>
          <p className="text-sm text-text-secondary">Use your Open Pulse Checker admin credentials to continue.</p>
        </div>

        <form className="space-y-3" onSubmit={submit}>
          <Field label="Username">
            <TextInput autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} />
          </Field>

          <Field label="Password">
            <TextInput type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </Field>

          {auth.loginError ? <p className="text-sm text-red-400">{auth.loginError}</p> : null}

          <Button type="submit" disabled={auth.isLoggingIn} className="w-full">
            {auth.isLoggingIn ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </section>
    </div>
  )
}
