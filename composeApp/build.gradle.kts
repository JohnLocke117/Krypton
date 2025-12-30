import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
}

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting
        val jvmMain by getting
        val androidMain by getting
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kermit)
            implementation("org.jetbrains:markdown:0.5.2")
            // Material Icons Extended (for Android, JVM will use compose.desktop which includes icons)
            implementation("androidx.compose.material:material-icons-extended:1.7.6")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // MCP SDK requires Ktor 3.2.3, so we need to align all Ktor dependencies
            // MCP SDK - single package that includes both client and server
            implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
            // Ktor client - use version compatible with MCP SDK (3.2.3)
            implementation("io.ktor:ktor-client-core:3.2.3")
            implementation("io.ktor:ktor-client-cio:3.2.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
            // Ktor server for HTTP transport (required by MCP SDK) - use 3.2.3 to match
            implementation("io.ktor:ktor-server-core:3.2.3")
            implementation("io.ktor:ktor-server-netty:3.2.3")
            implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
            implementation(libs.koin.core)
        }
        
        androidMain.dependencies {
            implementation(libs.koin.core)
            implementation("io.insert-koin:koin-android:3.5.6")
            implementation("androidx.activity:activity-compose:1.9.3")
            // DocumentFile for SAF (Storage Access Framework)
            implementation("androidx.documentfile:documentfile:1.0.1")
            // Ktor client for Android (same version as JVM for compatibility)
            implementation("io.ktor:ktor-client-core:3.2.3")
            implementation("io.ktor:ktor-client-cio:3.2.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
        }
    }
}

android {
    namespace = "org.krypton.krypton"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.krypton.krypton"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

// Configure Compose Desktop application
// Note: compose.desktop configuration is temporarily disabled to fix build issues
// Desktop application will still run via :composeApp:run task
// Native distributions can be configured later once the build is stable
// For now, desktop builds work without this configuration

// Task to run the Desktop application
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the Krypton Desktop application"
    
    // Ensure classes are compiled before running
    val compileTask = tasks.named("compileKotlinJvm")
    dependsOn(compileTask)
    
    // Set classpath to include compiled classes, processed resources, and dependencies
    val runtimeClasspath = project.configurations.getByName("jvmMainRuntimeClasspath")
    val outputDirs = compileTask.get().outputs.files
    // Add processed resources directory to classpath (Compose resources are processed here)
    val processedResourcesDir = project.layout.buildDirectory.dir("processedResources/jvm/main").get().asFile
    classpath = runtimeClasspath + outputDirs + files(processedResourcesDir)
    mainClass.set("org.krypton.MainKt")
}

// Task to run the MCP Server
tasks.register<JavaExec>("runMcpServer") {
    group = "application"
    description = "Runs the Krypton MCP Server"
    
    // Ensure classes are compiled before running
    val compileTask = tasks.named("compileKotlinJvm")
    dependsOn(compileTask)
    
    // Set classpath to include compiled classes and dependencies
    // Use the standard runtime classpath configuration for the JVM target
    val runtimeClasspath = project.configurations.getByName("jvmMainRuntimeClasspath")
    // Get the output directory from the compilation task - it's a collection of directories
    val outputDirs = compileTask.get().outputs.files
    classpath = runtimeClasspath + outputDirs
    mainClass.set("org.krypton.mcp.KryptonMcpServerKt")
    standardInput = System.`in`
    
    // Allow setting port via environment variable
    environment("MCP_PORT", System.getenv("MCP_PORT") ?: "8080")
}
