package org.krypton.util

import android.util.Log

/**
 * Android implementation of Logger using Android Log.
 */
class DefaultLogger(private val tag: String) : Logger {
    override fun debug(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.d(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }
    }

    override fun info(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.i(tag, message, throwable)
        } else {
            Log.i(tag, message)
        }
    }

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}

/**
 * Factory function for creating Logger instances on Android.
 */
actual fun createLogger(tag: String): Logger = DefaultLogger(tag)

