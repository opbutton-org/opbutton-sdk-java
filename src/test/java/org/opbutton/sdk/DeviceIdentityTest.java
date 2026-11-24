package org.opbutton.sdk;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class DeviceIdentityTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void explicitIdentityWinsWithoutConsultingSources() {
        OpButtonDeviceIdentity identity = new OpButtonDeviceIdentity(() -> { fail("Unexpected identity lookup"); return null; });
        assertEquals("host-id", identity.resolve("host-id"));
    }

    @Test public void orderedStrategiesStopAtTheFirstAvailableIdentity() {
        OpButtonDeviceIdentity identity = new OpButtonDeviceIdentity(() -> null, () -> "installation", () -> { fail(); return null; });
        assertEquals("installation", identity.resolve(null));
    }

    @Test public void secureIdentitySurvivesRecreationAndIsScopedToPackageAndDevice() {
        String id = OpButtonDeviceIdentity.scopedAndroidId("game.one", "0123456789abcdef");
        assertNotNull(id);
        assertEquals(id, OpButtonDeviceIdentity.scopedAndroidId("game.one", "0123456789ABCDEF"));
        assertNotEquals(id, OpButtonDeviceIdentity.scopedAndroidId("game.two", "0123456789abcdef"));
        assertNotEquals(id, OpButtonDeviceIdentity.scopedAndroidId("game.one", "fedcba9876543210"));
        assertNull(OpButtonDeviceIdentity.scopedAndroidId("game.one", "00000000"));
        assertNull(OpButtonDeviceIdentity.scopedAndroidId("game.one", "9774d56d682e549c"));
    }

    @Test public void installationIdentityPersistsAndDifferentDirectoriesStayDistinct() throws Exception {
        java.io.File first = directory.newFolder();
        String id = OpButtonDeviceIdentity.installationIdentity(first);
        assertNotNull(id);
        assertEquals(id, OpButtonDeviceIdentity.installationIdentity(first));
        assertNotEquals(id, OpButtonDeviceIdentity.installationIdentity(directory.newFolder()));
    }

    @Test public void reinstallDoesNotReuseAndroidIdentityWithoutItsCredential() throws Exception {
        String androidId = "android-v1:existing-device";
        java.io.File firstDirectory = directory.newFolder();
        java.io.File secondDirectory = directory.newFolder();
        OpButtonDeviceIdentity first = OpButtonDeviceIdentity.withInstallationIdentity(
                () -> androidId, () -> OpButtonDeviceIdentity.installationIdentity(firstDirectory), id -> false);
        OpButtonDeviceIdentity second = OpButtonDeviceIdentity.withInstallationIdentity(
                () -> androidId, () -> OpButtonDeviceIdentity.installationIdentity(secondDirectory), id -> false);

        String firstId = first.resolve(null);
        assertTrue(firstId.startsWith("installation-v1:"));
        assertEquals(firstId, first.resolve(null));
        assertNotEquals(firstId, second.resolve(null));
        assertNotEquals(androidId, second.resolve(null));
    }

    @Test public void existingAndroidIdentityKeepsItsStoredCredential() {
        String androidId = "android-v1:approved-device";
        OpButtonDeviceIdentity identity = OpButtonDeviceIdentity.withInstallationIdentity(
                () -> androidId, () -> { fail("Unexpected installation identity"); return null; }, androidId::equals);

        assertEquals(androidId, identity.resolve(null));
    }

    @Test public void contextlessFallbackIsStableAcrossSdkReinitializationInOneProcess() {
        assertEquals(new OpButtonDeviceIdentity().resolve(null), new OpButtonDeviceIdentity().resolve(null));
    }

    @Test public void unavailableStorageStrategyFallsThroughInsteadOfBreakingConnection() {
        OpButtonLog.setLoggerListener((level, tag, message, error) -> {});
        try {
            OpButtonDeviceIdentity identity = new OpButtonDeviceIdentity(
                () -> { throw new IllegalStateException("Credential storage is locked"); }, () -> "fallback");
            assertEquals("fallback", identity.resolve(null));
        } finally {
            OpButtonLog.setLoggerListener(null);
        }
    }
}
