package com.example.myapplication.di

import com.example.myapplication.data.ai.AiLlmEndpoint
import com.example.myapplication.data.ai.AiLlmFallbackRouter
import com.example.myapplication.data.ai.AiProviderLatencyProber
import com.example.myapplication.data.local.CollectionPdfGenerator
import com.example.myapplication.data.remote.GeminiStructuredClient
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.data.local.RecommendationCacheStore
import com.example.myapplication.domain.inspect.InspectImageUseCase
import com.example.myapplication.domain.recommendations.RecommendationEngine
import com.example.myapplication.domain.search.AddFromApiUseCase
import com.example.myapplication.domain.settings.ImportAnimeDbUseCase
import com.example.myapplication.domain.settings.RepairAnimeDbUseCase
import com.example.myapplication.domain.titles.AiTitleTranslationUseCase
import com.example.myapplication.domain.titles.RussianTitleEnrichmentUseCase
import com.example.myapplication.domain.titles.TitleDubbingCoordinator
import com.example.myapplication.domain.titles.TitleEnrichmentUseCase
import com.example.myapplication.domain.settings.RepairDbCoordinator
import com.example.myapplication.data.local.StatsExplanationCacheStore
import com.example.myapplication.domain.stats.ResolveStatsFooterPhraseUseCase
import com.example.myapplication.domain.stats.StatsCardExplanationUseCase
import com.example.myapplication.domain.stats.StatsExplanationCoordinator
import com.example.myapplication.domain.stats.StatsPhraseCatalog
import com.example.myapplication.updates.BatchEpisodeCheckUseCase
import com.example.myapplication.notifications.AnimeNotifier
import com.example.myapplication.notifications.AnimeNotifierImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single { StatsPhraseCatalog(androidContext()) }
    single { ResolveStatsFooterPhraseUseCase(catalog = get(), settingsDataStore = get(named("settings"))) }
    single<AnimeRepository> { AnimeRepository(apiService = get(), localDataSource = get()) }
    single { AppUpdateRepository(settingsDataStore = get(named("settings")), animeRepository = get()) }
    single<GenreRepository> { GenreRepository() }
    single { GeminiStructuredClient(get()) }
    single { AiLlmEndpoint(get()) }
    single { AiProviderLatencyProber(get(), get()) }
    single { AiLlmFallbackRouter(get(), get(), get()) }
    single { InspectImageUseCase(get(), get(), get(), get()) }
    single { AddFromApiUseCase(get(), get(), get(), get(), get()) }
    single { BatchEpisodeCheckUseCase(repository = get(), localDataSource = get()) }
    single { TitleEnrichmentUseCase(repository = get(), localDataSource = get()) }
    single { RussianTitleEnrichmentUseCase(repository = get(), localDataSource = get()) }
    single { AiTitleTranslationUseCase(router = get(), localDataSource = get()) }
    single { TitleDubbingCoordinator(androidContext()) }
    single { ImportAnimeDbUseCase(get()) }
    single { RepairAnimeDbUseCase(get(), get(), get(), get()) }
    single { RepairDbCoordinator(androidContext()) }
    single { CollectionPdfGenerator(androidContext()) }
    single<AnimeNotifier> { AnimeNotifierImpl(context = androidContext()) }
    single { RecommendationCacheStore(androidContext()) }
    single { StatsExplanationCacheStore(androidContext()) }
    single { StatsCardExplanationUseCase(router = get(), genreRepository = get()) }
    single {
        StatsExplanationCoordinator(
            localDataSource = get(),
            explanationUseCase = get(),
            credentialsStore = get(),
            cacheStore = get(),
            settingsDataStore = get(named("settings")),
        )
    }
    single {
        RecommendationEngine(
            apiService = get(),
            localDataSource = get(),
            genreRepository = get(),
            cache = get(),
        )
    }
}
