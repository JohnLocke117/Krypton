package org.krypton.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

actual fun copyToClipboard(text: String) {
    // Try to get context from Koin (set during app initialization via androidContext())
    val context = try {
        // Koin Android extension makes Context available when androidContext() is called
        // We need to use get() with the Context type
        GlobalContext.get().get<Context>()
    } catch (e: Exception) {
        // If context is not available in Koin, we can't copy
        // This should not happen in normal usage as Context is set during Koin initialization
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Krypton Chat", text)
    clipboard.setPrimaryClip(clip)
}

