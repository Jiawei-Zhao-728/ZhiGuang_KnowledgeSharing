import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";
import type { ReactNode } from "react";
import { ApiError } from "@/services/apiClient";
import { authService } from "@/services/authService";
import type {
    AuthenticatedUser,
    LoginRequest,
    RegisterRequest,
    TokenResponse
} from "@/types/auth";
import {
  isAccessExpiring,
  shouldAdoptLocalTokensAfterRefreshFailure,
  shouldRefreshAfterFetchUserFailure,
  type StoredAuthTokens
} from "@/context/authSession";

type AuthTokens = StoredAuthTokens;

type AuthContextValue = {
  user: AuthenticatedUser | null;
  isLoading: boolean;
  tokens: AuthTokens | null;
  login: (payload: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<AuthenticatedUser>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  reloadUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const STORAGE_KEY = "zhiguang_auth_tokens";
const USER_STORAGE_KEY = "zhiguang_current_user";

const readStoredTokens = (): AuthTokens | null => {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthTokens;
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.expiresAt) return null;
    return parsed;
  } catch {
    return null;
  }
};

const persistTokens = (tokens: AuthTokens | null) => {
  if (typeof window === "undefined") {
    return;
  }
  if (!tokens) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
};

const readStoredUser = (): AuthenticatedUser | null => {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthenticatedUser;
    if (!parsed || typeof parsed !== "object") return null;
    return parsed;
  } catch {
    return null;
  }
};

const persistUser = (user: AuthenticatedUser | null) => {
  if (typeof window === "undefined") return;
  if (!user) {
    localStorage.removeItem(USER_STORAGE_KEY);
    return;
  }
  try {
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  } catch {
    // ignore
  }
};

const parseInstantToMillis = (value: string): number => {
  const numeric = Number(value);
  if (!Number.isNaN(numeric)) {
    return numeric > 1e12 ? numeric : numeric * 1000;
  }
  const t = Date.parse(value);
  return Number.isNaN(t) ? Date.now() + 10 * 60 * 1000 : t;
};

const toTokens = (token: TokenResponse): AuthTokens => ({
  accessToken: token.accessToken,
  refreshToken: token.refreshToken,
  expiresAt: parseInstantToMillis(token.accessTokenExpiresAt)
});

const httpStatusOf = (error: unknown): number | undefined =>
  error instanceof ApiError ? error.status : undefined;

type AuthProviderProps = {
  children: ReactNode;
};

let rotateInFlight: Promise<AuthTokens | null> | null = null;

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [tokens, setTokens] = useState<AuthTokens | null>(() => readStoredTokens());
  const [user, setUser] = useState<AuthenticatedUser | null>(() => readStoredUser());
  const [isLoading, setIsLoading] = useState<boolean>(!!tokens);
  const tokensRef = useRef<AuthTokens | null>(tokens);
  const restoringRef = useRef<Promise<void> | null>(null);

  const applyTokens = useCallback((next: AuthTokens | null) => {
    tokensRef.current = next;
    setTokens(next);
    persistTokens(next);
  }, []);

  const clearSession = useCallback(() => {
    tokensRef.current = null;
    setTokens(null);
    setUser(null);
    persistTokens(null);
    persistUser(null);
  }, []);

  const fetchUser = useCallback(async (accessToken: string): Promise<{ ok: true } | { ok: false; status?: number }> => {
    try {
      const profile = await authService.fetchCurrentUser(accessToken);
      if (!tokensRef.current) {
        return { ok: false };
      }
      setUser(profile);
      persistUser(profile);
      return { ok: true };
    } catch (error) {
      console.error("获取用户信息失败", error);
      return { ok: false, status: httpStatusOf(error) };
    }
  }, []);

  const rotateRefresh = useCallback(async (refreshToken: string): Promise<AuthTokens | null> => {
    if (rotateInFlight) {
      return rotateInFlight;
    }
    const task = (async () => {
      try {
        const result = await authService.refresh(refreshToken);
        const current = tokensRef.current ?? readStoredTokens();
        if (!current) {
          return null;
        }
        if (current.refreshToken !== refreshToken) {
          applyTokens(current);
          return current;
        }
        const nextTokens = toTokens(result);
        applyTokens(nextTokens);
        return nextTokens;
      } catch (error) {
        console.error("刷新登录状态失败", error);
        const local = readStoredTokens();
        if (shouldAdoptLocalTokensAfterRefreshFailure(refreshToken, local) && local) {
          applyTokens(local);
          return local;
        }
        return null;
      }
    })().finally(() => {
      rotateInFlight = null;
    });
    rotateInFlight = task;
    return task;
  }, [applyTokens]);

  const restoreSession = useCallback(async (current: AuthTokens) => {
    let tokensToUse = current;
    let didRefresh = false;

    if (isAccessExpiring(tokensToUse)) {
      const refreshed = await rotateRefresh(tokensToUse.refreshToken);
      if (!tokensRef.current) {
        return;
      }
      if (!refreshed) {
        clearSession();
        return;
      }
      tokensToUse = refreshed;
      didRefresh = true;
    }

    const result = await fetchUser(tokensToUse.accessToken);
    if (!tokensRef.current) {
      return;
    }
    if (result.ok) {
      return;
    }

    if (shouldRefreshAfterFetchUserFailure(result.status) && !didRefresh) {
      const refreshed = await rotateRefresh(tokensToUse.refreshToken);
      if (!tokensRef.current) {
        return;
      }
      if (!refreshed) {
        clearSession();
        return;
      }
      const retry = await fetchUser(refreshed.accessToken);
      if (!tokensRef.current) {
        return;
      }
      if (!retry.ok && shouldRefreshAfterFetchUserFailure(retry.status)) {
        clearSession();
      }
      return;
    }
    // Network / 5xx: keep tokens and any cached profile.
  }, [clearSession, fetchUser, rotateRefresh]);

  useEffect(() => {
    if (!tokens) {
      setIsLoading(false);
      return;
    }

    if (!restoringRef.current) {
      const task = restoreSession(tokens).finally(() => {
        restoringRef.current = null;
        setIsLoading(false);
      });
      restoringRef.current = task;
    }
  }, [tokens, restoreSession]);

  const login = useCallback(
    async (payload: LoginRequest) => {
      const response = await authService.login(payload);
      const nextTokens = toTokens(response.token);
      applyTokens(nextTokens);
      setUser(response.user);
      persistUser(response.user);
      await fetchUser(nextTokens.accessToken);
    },
    [applyTokens, fetchUser]
  );

  const register = useCallback(async (payload: RegisterRequest) => {
    const result = await authService.register(payload);
    const nextTokens = toTokens(result.token);
    applyTokens(nextTokens);
    const userInfo = result.user as AuthenticatedUser;
    setUser(userInfo);
    persistUser(userInfo);
    await fetchUser(nextTokens.accessToken);
    return userInfo;
  }, [applyTokens, fetchUser]);

  const logout = useCallback(async () => {
    const current = tokensRef.current;
    if (current) {
      try {
        await authService.logout({ refreshToken: current.refreshToken }, current.accessToken);
      } catch (error) {
        console.warn("注销请求失败，继续清除本地状态", error);
      }
    }
    clearSession();
  }, [clearSession]);

  const refresh = useCallback(async () => {
    const current = tokensRef.current ?? readStoredTokens();
    if (!current) return;
    if (!isAccessExpiring(current)) {
      return;
    }
    const next = await rotateRefresh(current.refreshToken);
    if (!next) {
      clearSession();
      return;
    }
    await fetchUser(next.accessToken);
  }, [clearSession, fetchUser, rotateRefresh]);

  const reloadUser = useCallback(async () => {
    const current = tokensRef.current;
    if (!current) return;
    const result = await fetchUser(current.accessToken);
    if (result.ok) return;
    if (shouldRefreshAfterFetchUserFailure(result.status)) {
      const next = await rotateRefresh(current.refreshToken);
      if (!next) {
        clearSession();
        return;
      }
      const retry = await fetchUser(next.accessToken);
      if (!retry.ok && shouldRefreshAfterFetchUserFailure(retry.status)) {
        clearSession();
      }
    }
  }, [clearSession, fetchUser, rotateRefresh]);

  useEffect(() => {
    if (!tokens) {
      return;
    }
    const timer = window.setInterval(() => {
      void refresh();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [tokens, refresh]);

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === "visible") {
        void refresh();
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [refresh]);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) return;
      const next = readStoredTokens();
      tokensRef.current = next;
      setTokens(next);
      if (!next) {
        setUser(null);
        persistUser(null);
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      tokens,
      isLoading,
      login,
      register,
      logout,
      refresh,
      reloadUser
    }),
    [user, tokens, isLoading, login, register, logout, refresh, reloadUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth 必须在 AuthProvider 内部使用");
  }
  return context;
};
