import { type ReactNode } from 'react'

interface BaseStateProps {
  title: string
  description?: string
  action?: ReactNode
}

function StateContainer({ title, description, action }: BaseStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-surface-border bg-bg-elevated p-8 text-center">
      <h3 className="text-lg font-semibold text-text-primary">{title}</h3>
      {description ? <p className="mt-2 text-sm text-text-secondary">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}

export function EmptyState(props: BaseStateProps) {
  return <StateContainer {...props} />
}

export function LoadingState({ title = 'Loading data...', description = 'Please wait while we fetch the latest records.' }: Partial<BaseStateProps>) {
  return <StateContainer title={title} description={description} />
}

export function ErrorState({ title = 'Something went wrong', description = 'Please retry or check API availability.', action }: BaseStateProps) {
  return <StateContainer title={title} description={description} action={action} />
}
