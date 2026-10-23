import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { ErrorBoundary } from './components/shared/ErrorBoundary';
import { Toaster } from 'sonner';
import './styles/global.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
    throw new Error('Application root element was not found.');
}
createRoot(rootElement).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <App />
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
        }}
      />
    </ErrorBoundary>
  </StrictMode>
);
