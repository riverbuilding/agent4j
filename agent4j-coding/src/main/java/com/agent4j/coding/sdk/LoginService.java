package com.agent4j.coding.sdk;

import com.agent4j.ai.AiResolvedAuth;

public interface LoginService {
    AuthSession loginApiKey(ApiKeyLoginRequest request);

    AuthSession loginAccessToken(AccessTokenLoginRequest request);

    AuthStatus status(String providerId);

    AiResolvedAuth resolveAuth(String providerId);

    boolean logout(String providerId);
}
