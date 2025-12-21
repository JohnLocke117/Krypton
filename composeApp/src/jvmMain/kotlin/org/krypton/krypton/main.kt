package org.krypton.krypton

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.Atom

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Krypton - Text Editor",
        icon = painterResource(Res.drawable.Atom)
    ) {
        App()
    }
}