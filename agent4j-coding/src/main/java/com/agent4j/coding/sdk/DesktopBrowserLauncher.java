package com.agent4j.coding.sdk;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** Uses the host operating system's default browser. */
public final class DesktopBrowserLauncher implements BrowserLauncher {
    @Override
    public void open(URI uri) throws IOException {
        Objects.requireNonNull(uri, "uri");
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("desktop browser launching is not supported in this environment");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("desktop browser launching is not supported in this environment");
        }
        desktop.browse(uri);
    }
}
