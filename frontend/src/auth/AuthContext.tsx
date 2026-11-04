import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { tokenStore } from '../api/client';
import { authApi } from '../api/auth';
import type { RegisterInput } from '../api/auth';
import { usersApi } from '../api/users';
import type { UserResponse } from '../api/types';

interface AuthState {
  user: UserResponse | null;
  loading: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  /** Completes a phone sign-in with a code that has already been sent. */
  loginWithPhone: (phoneNumber: string, code: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export type { RegisterInput };

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // Tokens outlive the page, so on reload the session is restored by asking the
  // API who the caller is rather than by trusting anything cached in the
  // browser. If the token has expired the transport refreshes it transparently;
  // if that fails too, the stale tokens are cleared.
  useEffect(() => {
    let cancelled = false;

    void (async () => {
      if (!tokenStore.accessToken()) {
        setLoading(false);
        return;
      }
      try {
        const me = await usersApi.me();
        if (!cancelled) setUser(me);
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const tokens = await authApi.login(username, password);
    tokenStore.save(tokens);
    setUser(tokens.user);
  }, []);

  const loginWithPhone = useCallback(async (phoneNumber: string, code: string) => {
    const tokens = await authApi.loginWithPhone(phoneNumber, code);
    tokenStore.save(tokens);
    setUser(tokens.user);
  }, []);

  const register = useCallback(
    async (input: RegisterInput) => {
      await authApi.register(input);
      // Registration does not return tokens, so sign in with what was just set.
      await login(input.username, input.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    tokenStore.clear();
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    setUser(await usersApi.me());
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      isAdmin: user?.role === 'ADMIN',
      login,
      loginWithPhone,
      register,
      logout,
      refreshUser,
    }),
    [user, loading, login, loginWithPhone, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
