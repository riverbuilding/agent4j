package com.agent4j.coding.sdk;

import java.time.Instant;

public interface SubscriptionLoginClient {
    SubscriptionLoginStart startBrowserLogin(BrowserSubscriptionLoginRequest request, Instant now);

    SubscriptionLoginStart startDeviceCodeLogin(DeviceCodeSubscriptionLoginRequest request, Instant now);

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
