package org.krypton.krypton

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.Atom
import org.krypton.krypton.util.initializeKermit
import org.krypton.krypton.util.AppLogger

fun main() = application {
    // Initialize Kermit logging at app startup
    initializeKermit()
    AppLogger.i("App", "Application started")
    
    Window(
        onCloseRequest = {
            AppLogger.i("App", "Application closing")
            exitApplication()
        },
        title = "Krypton - Text Editor",
        icon = painterResource(Res.drawable.Atom)
    ) {
        App()
    }
}