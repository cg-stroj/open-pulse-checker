import { Component, type ErrorInfo, type ReactNode } from 'react'
import { ErrorState } from '../components/states/ErrorState'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

export class AppErrorBoundary extends Component<Props, State> {
  public state: State = { hasError: false }

  public static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  public componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled application error', error, info)
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-bg-base p-6">
          <ErrorState title="Unexpected UI error" description="Please refresh the page and try again." />
        </div>
      )
    }

    return this.props.children
  }
}
