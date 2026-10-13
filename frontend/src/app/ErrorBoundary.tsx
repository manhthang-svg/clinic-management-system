import { Component, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  failed: boolean
}

export class ErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  public state: ErrorBoundaryState = { failed: false }

  public static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  public render(): ReactNode {
    if (this.state.failed) {
      return <p role="alert">The application could not be displayed.</p>
    }

    return this.props.children
  }
}
