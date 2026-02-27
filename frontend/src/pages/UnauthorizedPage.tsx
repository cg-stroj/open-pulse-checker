import { Link } from 'react-router-dom'
import { ErrorState } from '../components/states/ErrorState'

export function UnauthorizedPage() {
  return (
    <div className="mx-auto max-w-xl py-12">
      <ErrorState
        title="Admin access required"
        description="You are signed in, but this account is missing the ADMIN role needed for this area."
        action={
          <Link
            to="/dashboard"
            className="inline-flex items-center justify-center rounded-md bg-accent px-4 py-2 text-sm font-medium text-bg-base transition hover:bg-accent-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            Back to dashboard
          </Link>
        }
      />
    </div>
  )
}
