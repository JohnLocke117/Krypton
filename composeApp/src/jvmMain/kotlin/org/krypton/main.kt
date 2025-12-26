package org.krypton

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.polymer
import org.krypton.di.allModules
import org.krypton.util.initializeKermit
import org.krypton.util.AppLogger
import org.koin.core.context.startKoin
import java.awt.Color

fun main() = application {
    // Initialize Kermit logging at app startup
    initializeKermit()
    
    // Initialize Koin dependency injection
    startKoin {
        modules(allModules)
    }
    
    AppLogger.i("App", "Application started")
    
    Window(
        onCloseRequest = {
            AppLogger.i("App", "Application closing")
            exitApplication()
        },
        title = "Krypton",
        icon = painterResource(Res.drawable.polymer),
        state = WindowState(size = DpSize(1400.dp, 900.dp))
    ) {
        // Customize title bar appearance on macOS
        LaunchedEffect(Unit) {
            try {
                val osName = System.getProperty("os.name").lowercase()
                if (osName.contains("mac")) {
                    val awtWindow = this@Window.window
                    
                    // Use JNI to access NSWindow and customize title bar
                    // We'll use a native method approach via reflection on the window's native handle
                    try {
                        // Try to get the window's native handle using JNI
                        // First, get the window's peer through a method that might be accessible
                        val windowClass = awtWindow.javaClass
                        
                        // Try to find a method that gives us access to the native window
                        var nsWindow: Any? = null
                        
                        // Method 1: Try using com.apple.laf.AquaNativeResources
                        try {
                            val aquaClass = Class.forName("com.apple.laf.AquaNativeResources")
                            val getWindowMethod = aquaClass.getMethod("getWindow", java.awt.Component::class.java)
                            nsWindow = getWindowMethod.invoke(null, awtWindow)
                        } catch (e: Exception) {
                            // Try alternative: use the window's background color to affect title bar
                            // On macOS, setting the window background might affect the title bar appearance
                            val baseColor = Color(0x1E, 0x1E, 0x2E)
                            awtWindow.background = baseColor
                        }
                        
                        // If we got the NSWindow, try to customize it
                        if (nsWindow != null) {
                            val nsWindowClass = nsWindow.javaClass
                            
                            // Set titlebarAppearsTransparent
                            try {
                                val setTitlebarAppearsTransparentMethod = nsWindowClass.getMethod(
                                    "setTitlebarAppearsTransparent",
                                    Boolean::class.javaPrimitiveType
                                )
                                setTitlebarAppearsTransparentMethod.invoke(nsWindow, true)
                            } catch (e: Exception) {
                                // Method might not exist, continue
                            }
                            
                            // Set titlebarColor to match background (Base: 0xFF1E1E2E)
                            try {
                                val setTitlebarColorMethod = nsWindowClass.getMethod(
                                    "setTitlebarColor",
                                    java.awt.Color::class.java
                                )
                                val baseColor = Color(0x1E, 0x1E, 0x2E)
                                setTitlebarColorMethod.invoke(nsWindow, baseColor)
                            } catch (e: Exception) {
                                // Try alternative: setBackgroundColor
                                try {
                                    val setBackgroundColorMethod = nsWindowClass.getMethod(
                                        "setBackgroundColor",
                                        java.awt.Color::class.java
                                    )
                                    val baseColor = Color(0x1E, 0x1E, 0x2E)
                                    setBackgroundColorMethod.invoke(nsWindow, baseColor)
                                } catch (e2: Exception) {
                                    AppLogger.i("App", "Could not set title bar color: ${e2.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.i("App", "Could not customize title bar: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.i("App", "Error customizing title bar: ${e.message}")
            }
        }
        
        App()
    }
}