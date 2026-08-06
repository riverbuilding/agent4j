package com.agent4j.coding.sdk;

import java.io.IOException;
import java.net.URI;

/** Opens an OAuth authorization URI in a user-agent. */
@FunctionalInterface
interface BrowserLauncher {
    void open(URI uri) throws IOException;

    static BrowserLauncher system() {
        return new DesktopBrowserLauncher();
    }
}
