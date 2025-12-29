import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
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
    }
}

compose.desktop {
    application {
        mainClass = "org.krypton.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.krypton.krypton"
            packageVersion = "1.0.0"
        }
    }
}

// Task to run the MCP Server
tasks.register<JavaExec>("runMcpServer") {
    group = "application"
    description = "Runs the Krypton MCP Server"
    
    // Ensure classes are compiled before running
    dependsOn(tasks.named("compileKotlinJvm"))
    
    // Set classpath to include compiled classes and dependencies
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("org.krypton.mcp.KryptonMcpServerKt")
    standardInput = System.`in`
    
    // Allow setting port via environment variable
    environment("MCP_PORT", System.getenv("MCP_PORT") ?: "8080")
}
