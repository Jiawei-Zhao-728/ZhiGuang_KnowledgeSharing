import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  isAccessExpiring,
  shouldAdoptLocalTokensAfterRefreshFailure,
  shouldRefreshAfterFetchUserFailure
} from "./authSession.ts";

describe("authSession", () => {
  it("treats an access token as expiring at the refresh skew window", () => {
    const tokens = {
      accessToken: "a",
      refreshToken: "r",
      expiresAt: 1_000_000
    };
    assert.equal(isAccessExpiring(tokens, 1_000_000 - 5_000), true);
    assert.equal(isAccessExpiring(tokens, 1_000_000 - 5_001), false);
    assert.equal(isAccessExpiring(tokens, 1_000_001), true);
  });

  it("refreshes only on 401 from /auth/me, not on network or 5xx failures", () => {
    assert.equal(shouldRefreshAfterFetchUserFailure(401), true);
    assert.equal(shouldRefreshAfterFetchUserFailure(500), false);
    assert.equal(shouldRefreshAfterFetchUserFailure(undefined), false);
  });

  it("adopts another tab's rotated refresh token instead of logging both tabs out", () => {
    assert.equal(
      shouldAdoptLocalTokensAfterRefreshFailure("old-refresh", {
        accessToken: "new-a",
        refreshToken: "new-refresh",
        expiresAt: 1
      }),
      true
    );
    assert.equal(
      shouldAdoptLocalTokensAfterRefreshFailure("old-refresh", {
        accessToken: "a",
        refreshToken: "old-refresh",
        expiresAt: 1
      }),
      false
    );
    assert.equal(shouldAdoptLocalTokensAfterRefreshFailure("old-refresh", null), false);
  });
});
