package com.agent4j.coding.sdk;

import java.time.Instant;

public interface SubscriptionLoginClient {
    SubscriptionLoginStart startBrowserLogin(BrowserSubscriptionLoginRequest request, Instant now);

    SubscriptionLoginStart startDeviceCodeLogin(DeviceCodeSubscriptionLoginRequest request, Instant now);

    default SubscriptionLoginPollResult pollLogin(String flowId, Instant now) {
        throw new UnsupportedOperationException("subscription login polling is not configured");
    }

    default SubscriptionLoginPollResult completeBrowserLoginCallback(String code, String state, Instant now) {
        throw new UnsupportedOperationException("subscription browser callback login is not configured");
    }

    static SubscriptionLoginClient unsupported() {
        return new SubscriptionLoginClient() {
            @Override
            public SubscriptionLoginStart startBrowserLogin(BrowserSubscriptionLoginRequest request, Instant now) {
                throw new UnsupportedOperationException("subscription browser login is not configured");
            }

            @Override
            public SubscriptionLoginStart startDeviceCodeLogin(DeviceCodeSubscriptionLoginRequest request, Instant now) {
                throw new UnsupportedOperationException("subscription device-code login is not configured");
            }
        };
    }
}
