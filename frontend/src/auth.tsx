import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, hasToken, setToken, setUnauthorizedHandler, type Role, type User } from './api';

interface Session {
  user: User | null;
  /** True while a stored token is being checked on page load. */
  restoring: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => void;
  can: (...roles: Role[]) => boolean;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState(hasToken());

  const signOut = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    if (!hasToken()) return;
    api
      .me()
      .then(setUser)
      .catch(() => signOut())
      .finally(() => setRestoring(false));
  }, [signOut]);

  const signIn = useCallback(async (username: string, password: string) => {
    const response = await api.login(username, password);
    setToken(response.token);
    setUser(response.user);
  }, []);

  const value = useMemo<Session>(
    () => ({
      user,
      restoring,
      signIn,
      signOut,
      can: (...roles) => user !== null && roles.includes(user.role),
    }),
    [user, restoring, signIn, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside SessionProvider');
  return session;
}
