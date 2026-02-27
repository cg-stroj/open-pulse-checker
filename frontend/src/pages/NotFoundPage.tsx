import { Link } from 'react-router-dom'
import { Button } from '../components/ui/Button'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-base p-6">
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-text-primary">Page not found</h1>
        <p className="mt-2 text-text-secondary">The route does not exist in the current foundation scope.</p>
        <Link to="/dashboard" className="mt-4 inline-block">
          <Button>Back to dashboard</Button>
        </Link>
      </div>
    </div>
  )
}
