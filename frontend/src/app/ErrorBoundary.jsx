import { Component } from 'react';
export class ErrorBoundary extends Component {
    state = { failed: false };
    static getDerivedStateFromError() {
        return { failed: true };
    }
    render() {
        if (this.state.failed) {
            return <p role="alert">The application could not be displayed.</p>;
        }
        return this.props.children;
    }
}
