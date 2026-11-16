package org.opbutton.sdk;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class OpButtonDeviceCredentialStore {
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int MAX_SECRET_LENGTH = 256;
    private final Context m_context;
    private final ConcurrentMap<String, String> m_memory = new ConcurrentHashMap<>();

    OpButtonDeviceCredentialStore(@Nullable Context context) {
        m_context = context == null ? null : context.getApplicationContext();
    }

    @NonNull String getOrCreate(@NonNull String baseUrl, @NonNull String deviceId,
                                @Nullable String configuredSecret) throws OpButtonException {
        if (configuredSecret != null) return configuredSecret;
        if (m_context == null) {
            throw new IllegalStateException(
                    "Automatic device credential persistence requires Android Context");
        }
        String key = key(baseUrl, deviceId);
        String cached = m_memory.get(key);
        if (valid(cached)) return cached;
        File file = credentialFile(key);
        if (file != null && file.isFile()) {
            if (file.length() > MAX_SECRET_LENGTH) {
                throw new OpButtonException("Stored device credential is invalid");
            }
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int count = input.read(bytes);
                String value = new String(bytes, 0, Math.max(count, 0), StandardCharsets.UTF_8).trim();
                if (valid(value)) {
                    m_memory.put(key, value);
                    return value;
                }
            } catch (Exception e) {
                throw new OpButtonException("Unable to read stored device credential", e);
            }
            throw new OpButtonException("Stored device credential is invalid");
        }
        String value = generateSecret();
        saveByKey(key, value);
        return value;
    }

    void save(@NonNull String baseUrl, @NonNull String deviceId,
              @NonNull String value) throws OpButtonException {
        if (!valid(value)) {
            throw new IllegalArgumentException("device credential must contain 32 to 256 characters");
        }
        saveByKey(key(baseUrl, deviceId), value);
    }

    private void saveByKey(@NonNull String key, @NonNull String value) throws OpButtonException {
        File file = credentialFile(key);
        if (file == null) {
            throw new OpButtonException("Android Context is required to persist device credential");
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Exception e) {
            if (temporary.exists() && !temporary.delete()) {
                OpButtonLog.w("Unable to remove temporary device credential");
            }
            throw new OpButtonException("Unable to persist device credential", e);
        }
        if (!temporary.renameTo(file)) {
            // renameTo cannot replace consistently on all supported Android versions.
            if (file.exists() && !file.delete()) {
                throw new OpButtonException("Unable to replace stored device credential");
            }
            if (!temporary.renameTo(file)) {
                throw new OpButtonException("Unable to install stored device credential");
            }
        }
        m_memory.put(key, value);
    }

    @Nullable private File credentialFile(String key) {
        if (m_context == null) return null;
        return new File(m_context.getNoBackupFilesDir(), "opbutton-device-" + key);
    }

    private static String key(String baseUrl, String deviceId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((baseUrl + "\u0000" + deviceId).getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte value : digest) output.append(String.format("%02x", value & 0xff));
            return output.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static boolean valid(@Nullable String value) {
        if (value == null || value.length() < MIN_SECRET_LENGTH
                || value.length() > MAX_SECRET_LENGTH) return false;
        for (int index = 0; index != value.length(); ++index) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) return false;
        }
        return true;
    }
}
