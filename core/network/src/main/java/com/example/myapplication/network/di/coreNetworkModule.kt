package com.example.myapplication.network.di

import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.example.myapplication.network.AniListRemoteDataSource
import com.example.myapplication.network.ApiService
import com.example.myapplication.network.ShikimoriRemoteDataSource
import com.example.myapplication.network.TraceMoeRemoteDataSource
import com.example.myapplication.network.VetroApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.example.myapplication.network.TokenBucketRateLimiter
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val rateHeavy = named("api_rate_heavy")
private val rateSearch = named("api_rate_search")
private val rateBurst = named("api_rate_burst")

val coreNetworkModule = module {
    single(rateHeavy) { TokenBucketRateLimiter(maxTokens = 1.0, refillTokensPerSecond = 1.0 / 1.2) }
    single(rateSearch) { TokenBucketRateLimiter(maxTokens = 1.0, refillTokensPerSecond = 1.0 / 0.4) }
    single(rateBurst) { TokenBucketRateLimiter(maxTokens = 1.0, refillTokensPerSecond = 1.0 / 0.3) }

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            install(UserAgent) {
                agent = "VetroApp/1.0 (https://github.com/2004i/Vetro)"
            }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("Ktor", message)
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
            }
        }
    }
    single {
        ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .addHttpHeader("Accept", "application/json")
            .addHttpHeader("Content-Type", "application/json")
            .build()
    }
    single { ShikimoriRemoteDataSource(get<HttpClient>(), get(rateBurst)) }
    single { TraceMoeRemoteDataSource(get<HttpClient>()) }
    single { AniListRemoteDataSource(get<ApolloClient>()) }
    single<ApiService> {
        VetroApiService(
            httpClient = get<HttpClient>(),
            shikimori = get(),
            aniList = get(),
            heavyRate = get(rateHeavy),
            searchRate = get(rateSearch),
            burstRate = get(rateBurst)
        )
    }
}
