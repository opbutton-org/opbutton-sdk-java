package org.opbutton.sdk;

import android.content.Context;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/** Ordered identity strategies: host override, scoped Android ID, installation, process. */
final class OpButtonDeviceIdentity {
    interface Source { String resolve(); }

    private static final String PROCESS_ID = "process-v1:" + UUID.randomUUID();
    private final Source[] sources;

    OpButtonDeviceIdentity(Source... sources) { this.sources = sources.clone(); }

    String resolve(String override) {
        if (override != null && !override.trim().isEmpty()) return override;
        for (Source source : sources) {
            try {
                String id = source.resolve();
                if (id != null && !id.isEmpty()) return id;
            } catch (RuntimeException e) {
                OpButtonLog.w("Device identity source unavailable; trying fallback");
            }
        }
        return PROCESS_ID;
    }

    static OpButtonDeviceIdentity forContext(Context context) {
        if (context == null) return new OpButtonDeviceIdentity();
        Context application = context.getApplicationContext();
        Context owner = application != null ? application : context;
        return new OpButtonDeviceIdentity(
            () -> secureIdentity(owner),
            () -> installationIdentity(owner.getNoBackupFilesDir())
        );
    }

    static String scopedAndroidId(String packageName, String androidId) {
        if (androidId == null || !androidId.matches("(?i)[0-9a-f]{1,16}")
                || androidId.matches("0+") || androidId.equalsIgnoreCase("9774d56d682e549c")) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                ("opbutton-device:v1\u0000" + packageName + "\u0000" + androidId.toLowerCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("android-v1:");
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Android", e);
        }
    }

    private static String secureIdentity(Context context) {
        try {
            return scopedAndroidId(context.getPackageName(), Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID));
        } catch (RuntimeException e) {
            OpButtonLog.w("Android ID unavailable; using installation identity");
            return null;
        }
    }

    // noBackupFilesDir prevents a backup restored onto another phone from cloning its ID.
    static synchronized String installationIdentity(File directory) {
        File temporary = null;
        try {
            File file = new File(directory, "opbutton-installation-id");
            if (file.isFile()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8))) {
                    return "installation-v1:" + UUID.fromString(reader.readLine().trim());
                }
            }
            String id = UUID.randomUUID().toString();
            temporary = File.createTempFile("opbutton-identity-", ".tmp", directory);
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(id.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            if (!temporary.renameTo(file)) throw new IllegalStateException("Unable to save installation identity");
            return "installation-v1:" + id;
        } catch (Exception e) {
            OpButtonLog.w("Installation identity unavailable; using process identity");
            return null;
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                OpButtonLog.w("Unable to remove temporary identity file");
            }
        }
    }
}
