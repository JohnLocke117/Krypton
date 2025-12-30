package org.krypton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.krypton.di.allModules
import org.krypton.di.androidPlatformModule
import org.krypton.ui.KryptonApp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Koin dependency injection
        startKoin {
            androidContext(this@MainActivity)
            modules(allModules + androidPlatformModule)
        }
        
        setContent {
            KryptonApp()
        }
    }
}

