package org.opbutton.sdk;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Internal SDK logger. Delegates to {@link OpButtonLoggerListener} if set, otherwise to android.util.Log.
 */
final class OpButtonLog {
    private static final String TAG = "OpButtonSDK";
    private static OpButtonLogLevel s_level = OpButtonLogLevel.INFO;
    private static volatile OpButtonLoggerListener s_loggerListener = null;

    static void setLevel(@NonNull OpButtonLogLevel level) {
        s_level = level;
    }

    static void setLoggerListener(@Nullable OpButtonLoggerListener loggerListener) {
        s_loggerListener = loggerListener;
    }

    static void d(@NonNull String message, Object... args) {
        if (s_level.ordinal() <= OpButtonLogLevel.DEBUG.ordinal()) {
            String msg = format(message, args);
            OpButtonLoggerListener loggerListener = s_loggerListener;
            if (loggerListener != null) {
                loggerListener.onOpButtonLoggerMessage(OpButtonLogLevel.DEBUG, TAG, msg, null);
            } else {
                Log.d(TAG, msg);
            }
        }
    }

    static void i(@NonNull String message, Object... args) {
        if (s_level.ordinal() <= OpButtonLogLevel.INFO.ordinal()) {
            String msg = format(message, args);
            OpButtonLoggerListener loggerListener = s_loggerListener;
            if (loggerListener != null) {
                loggerListener.onOpButtonLoggerMessage(OpButtonLogLevel.INFO, TAG, msg, null);
            } else {
                Log.i(TAG, msg);
            }
        }
    }

    static void w(@NonNull String message, Object... args) {
        if (s_level.ordinal() <= OpButtonLogLevel.WARNING.ordinal()) {
            String msg = format(message, args);
            OpButtonLoggerListener loggerListener = s_loggerListener;
            if (loggerListener != null) {
                loggerListener.onOpButtonLoggerMessage(OpButtonLogLevel.WARNING, TAG, msg, null);
            } else {
                Log.w(TAG, msg);
            }
        }
    }

    static void e(@NonNull String message, Object... args) {
        if (s_level.ordinal() <= OpButtonLogLevel.ERROR.ordinal()) {
            String msg = format(message, args);
            OpButtonLoggerListener loggerListener = s_loggerListener;
            if (loggerListener != null) {
                loggerListener.onOpButtonLoggerMessage(OpButtonLogLevel.ERROR, TAG, msg, null);
            } else {
                Log.e(TAG, msg);
            }
        }
    }

    static void e(@NonNull String message, @NonNull Throwable t) {
        OpButtonLoggerListener loggerListener = s_loggerListener;
        if (loggerListener != null) {
            loggerListener.onOpButtonLoggerMessage(OpButtonLogLevel.EXCEPTION, TAG, message, t);
        } else {
            Log.e(TAG, message, t);
        }
    }

    private static String format(@NonNull String message, Object... args) {
        if (args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
