package com.agent4j.coding.sdk;

import com.agent4j.ai.AiResolvedAuth;

public interface LoginService {
    AuthSession loginApiKey(ApiKeyLoginRequest request);

    AuthSession loginAccessToken(AccessTokenLoginRequest request);

    SubscriptionLoginStart startBrowserSubscriptionLogin(BrowserSubscriptionLoginRequest request);

    SubscriptionLoginStart startDeviceCodeSubscriptionLogin(DeviceCodeSubscriptionLoginRequest request);

    AuthSession completeSubscriptionLogin(SubscriptionLoginCompletion completion);

    SubscriptionLoginPollResult pollSubscriptionLogin(String flowId);

    SubscriptionLoginPollResult completeBrowserSubscriptionLoginCallback(String code, String state);

    AuthStatus status(String providerId);

    AiResolvedAuth resolveAuth(String providerId);

    boolean logout(String providerId);
}
