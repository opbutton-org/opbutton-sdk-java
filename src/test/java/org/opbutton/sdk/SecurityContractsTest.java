package org.opbutton.sdk;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SecurityContractsTest {
    @Test public void productionBaseUrlRequiresHttpsAndHasNoAmbiguousSuffix() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("http://example.com", "token").build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://user@example.com", "token").build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com?next=evil", "token").build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com#fragment", "token").build());

        OpButtonConfig local = new OpButtonConfig.Builder("http://127.0.0.1:8000", "token")
                .setAllowInsecureLocalhost(true)
                .build();
        assertEquals("http://127.0.0.1:8000", local.getBaseUrl());
        OpButtonConfig ipv6 = new OpButtonConfig.Builder("http://[::1]:8000", "token")
                .setAllowInsecureLocalhost(true)
                .build();
        assertEquals("http://[::1]:8000", ipv6.getBaseUrl());
    }

    @Test public void deviceSecretUsesProtocolBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com", "token")
                        .setDeviceSecret("short")
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com", "token")
                        .setDeviceSecret(String.join("", Collections.nCopies(257, "x")))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com", "token")
                        .setDeviceSecret(String.join("", Collections.nCopies(32, "x")) + "\n")
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new OpButtonConfig.Builder("https://example.com", "token\r\nInjected: true")
                        .build());
    }

    @Test public void automaticCredentialRequiresAndroidContext() throws Exception {
        OpButtonDeviceCredentialStore store = new OpButtonDeviceCredentialStore(null);
        assertThrows(IllegalStateException.class,
                () -> store.getOrCreate("https://example.com", "device", null));
        String configured = String.join("", Collections.nCopies(64, "x"));
        assertEquals(configured,
                store.getOrCreate("https://example.com", "device", configured));
    }
}
