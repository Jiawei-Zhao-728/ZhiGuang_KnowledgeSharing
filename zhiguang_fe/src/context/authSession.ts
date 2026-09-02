export const ACCESS_TOKEN_REFRESH_SKEW_MS = 5_000;

export type StoredAuthTokens = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
};

export function isAccessExpiring(
  tokens: StoredAuthTokens,
  now = Date.now(),
  skewMs = ACCESS_TOKEN_REFRESH_SKEW_MS
): boolean {
  return now >= tokens.expiresAt - skewMs;
}

/**
 * /auth/me failed. 401 means the access token is unusable and a refresh
 * should be attempted. Any other failure (network, 5xx) must not discard
 * a still-valid refresh session.
 */
export function shouldRefreshAfterFetchUserFailure(httpStatus: number | undefined): boolean {
  return httpStatus === 401;
}

/**
 * Refresh failed with the token this tab sent. If localStorage already holds a
 * different refresh token, another tab rotated successfully — adopt that
 * session instead of wiping both tabs.
 */
export function shouldAdoptLocalTokensAfterRefreshFailure(
  attemptedRefreshToken: string,
  localTokens: StoredAuthTokens | null
): boolean {
  return Boolean(localTokens && localTokens.refreshToken !== attemptedRefreshToken);
}
