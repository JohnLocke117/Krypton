package org.krypton.krypton.di

import io.ktor.client.engine.*
import org.krypton.krypton.util.SecretsLoader
import org.krypton.krypton.web.WebSearchClient
import org.krypton.krypton.web.impl.TavilyClient
import org.krypton.krypton.util.AppLogger
import org.koin.dsl.module

/**
 * Web search dependency injection module.
 * 
 * Provides web search client (Tavily) if API key is available.
 */
val webModule = module {
    // WebSearchClient (optional - null if API key not available)
    single<WebSearchClient?> {
        val apiKey = SecretsLoader.loadSecret("TAVILLY_API_KEY")
        if (apiKey != null && apiKey.isNotBlank()) {
            AppLogger.i("WebModule", "Tavily API key found - enabling web search")
            val httpEngine: HttpClientEngine = get()
            TavilyClient(
                apiKey = apiKey,
                baseUrl = "https://api.tavily.com",
                httpClientEngine = httpEngine
            )
        } else {
            AppLogger.i("WebModule", "Tavily API key not found - web search disabled")
            null
        }
    }
}

