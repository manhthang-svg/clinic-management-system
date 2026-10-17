import { useSession } from '../hooks/useSession';
import { sessionManager as defaultSessionManager } from '../services/session';
import { AuthContext } from './authContextInstance';

export function AuthProvider({ children, sessionManager = defaultSessionManager }) {
  const session = useSession(sessionManager);

  const value = {
    session,
    user: session?.account || null,
    isAuthenticated: session?.status === 'authenticated',
    login: (username, password) => sessionManager.login(username, password),
    logout: () => sessionManager.logout(),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export default AuthProvider;
