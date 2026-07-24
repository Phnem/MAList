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
import com.example.myapplication.domain.settings.ShikimoriPlaceholderPurge
import com.example.myapplication.domain.titles.AiTitleTranslationUseCase
import com.example.myapplication.domain.titles.RussianTitleEnrichmentUseCase
import com.example.myapplication.domain.titles.TitleDubbingCoordinator
import com.example.myapplication.domain.titles.TitleEnrichmentUseCase
import com.example.myapplication.domain.settings.RepairDbCoordinator
import com.example.myapplication.domain.enrichment.CollectionEnrichmentCoordinator
import com.example.myapplication.domain.enrichment.CollectionGapDetector
import com.example.myapplication.domain.enrichment.EnrichmentGapJournal
import com.example.myapplication.domain.enrichment.weblinks.WebLinkEnrichmentUseCase
import com.example.myapplication.data.local.WebLinksStore
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
    single { ShikimoriPlaceholderPurge(httpClient = get(), imageStorage = get()) }
    single { RepairAnimeDbUseCase(get(), get(), get(), get(), get()) }
    single { RepairDbCoordinator(androidContext()) }
    single { EnrichmentGapJournal(androidContext()) }
    single { CollectionGapDetector(localDataSource = get(), repairUseCase = get(), journal = get()) }
    single { WebLinksStore(androidContext()) }
    single { WebLinkEnrichmentUseCase(resolver = get(), store = get()) }
    // File-based IPC мост к внешнему воркеру скачивания (Vetro_Queue: input.json/output.json).
    single<com.example.myapplication.download.FileIpcManager> {
        com.example.myapplication.download.FileIpcManagerImpl(context = androidContext())
    }
    // Серии по сезонам: файловый стор + фоновый резолвер (AniList → Shikimori → MAL).
    single { com.example.myapplication.data.local.SeasonEpisodesStore(androidContext()) }
    single {
        com.example.myapplication.domain.seasons.SeasonEpisodesResolver(
            repository = get(),
            localDataSource = get(),
            store = get(),
        )
    }
    // Local player (isolated feature — remove these lines to unwire it).
    single { com.example.myapplication.localplayer.data.LocalSourceStore(androidContext()) }
    single {
        com.example.myapplication.localplayer.domain.LocalLibraryUseCase(
            context = androidContext(),
            store = get(),
            aiRouter = get(),
        )
    }
    single { com.example.myapplication.localplayer.domain.FranchiseEpisodeMapper(get()) }
    single {
        com.example.myapplication.localplayer.domain.AniSkipSegmentProvider(get(), get())
    }
    single<com.example.myapplication.localplayer.domain.SkipSegmentProvider> {
        com.example.myapplication.localplayer.domain.PreferSourceTimestampsSkipProvider(
            fallback = get<com.example.myapplication.localplayer.domain.AniSkipSegmentProvider>(),
        )
    }

    // Media engine (stream + download)
    single { okhttp3.OkHttpClient.Builder().build() }
    single { com.example.myapplication.media.source.AniLibriaSource(client = get()) }
    single { com.example.myapplication.media.source.AnimeGoSource(client = get()) }
    single { com.example.myapplication.media.source.JutSuSource(client = get()) }
    single { com.example.myapplication.media.source.KodikSource(client = get()) }
    single { com.example.myapplication.media.source.ConsumetSource(client = get()) }
    single { com.example.myapplication.media.source.UrlSource(context = androidContext()) }
    single {
        com.example.myapplication.media.source.SourceEngine(
            aniLibriaSource = get(),
            animeGoSource = get(),
            jutSuSource = get(),
            kodikSource = get(),
            consumetSource = get(),
            urlSource = get(),
            webLinksStore = get(),
        )
    }
    single { com.example.myapplication.media.cookies.MediaCookieStore(androidContext()) }
    single<com.example.myapplication.media.MediaGateway> {
        com.example.myapplication.media.MediaGatewayImpl(
            context = androidContext(),
            sourceEngine = get(),
            fileIpcManager = get(),
            settingsDataStore = get(named("settings")),
        )
    }
    single { com.example.myapplication.media.download.SeasonBatchDownloader(get()) }
    single { com.example.myapplication.media.metadata.EpisodeArtworkRepository(get()) }
    single { com.example.myapplication.media.progress.EpisodePlaybackStore(get(named("settings"))) }
    single {
        CollectionEnrichmentCoordinator(
            context = androidContext(),
            settingsDataStore = get(named("settings")),
        )
    }
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
