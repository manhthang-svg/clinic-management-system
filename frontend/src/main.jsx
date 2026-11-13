
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { ErrorBoundary } from './components/shared/ErrorBoundary';
import { Toaster } from 'sonner';

import './styles/global.css';
import { AuthProvider } from './context';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Application root element was not found.');
}

createRoot(rootElement).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>

      <Toaster
        position="top-right"
        richColors
        closeButton
        toastOptions={{
          style: {
            fontFamily: 'inherit',
            borderRadius: '1rem',
            padding: '14px 16px',
            fontSize: '13px',
          },
          actionButtonStyle: {
            backgroundColor: '#0d9488',
            color: '#ffffff',
            borderRadius: '0.625rem',
            fontWeight: '700',
            fontSize: '12px',
            padding: '6px 12px',
            boxShadow: '0 1px 2px rgba(13, 148, 136, 0.2)',
          },
        }}
      />
    </ErrorBoundary>
  </StrictMode>,
);
