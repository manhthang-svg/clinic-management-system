import { useSyncExternalStore } from 'react';
import { sessionManager as defaultSessionManager } from '../services/session';

export function useSession(manager = defaultSessionManager) {
  return useSyncExternalStore(manager.subscribe, manager.getState);
}

export default useSession;
