package com.example.myapplication.di

import com.example.myapplication.data.local.CollectionPdfGenerator
import com.example.myapplication.data.remote.GeminiStructuredClient
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.domain.inspect.InspectImageUseCase
import com.example.myapplication.domain.search.AddFromApiUseCase
import com.example.myapplication.domain.settings.ImportAnimeDbUseCase
import com.example.myapplication.domain.stats.ResolveStatsFooterPhraseUseCase
import com.example.myapplication.domain.stats.StatsPhraseCatalog
import com.example.myapplication.domain.updates.BatchEpisodeCheckUseCase
import com.example.myapplication.notifications.AnimeNotifier
import com.example.myapplication.notifications.AnimeNotifierImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single { StatsPhraseCatalog(androidContext()) }
    single { ResolveStatsFooterPhraseUseCase(catalog = get(), settingsDataStore = get(named("settings"))) }
    single<AnimeRepository> { AnimeRepository(apiService = get(), localDataSource = get()) }
    single<GenreRepository> { GenreRepository() }
    single { GeminiStructuredClient(get()) }
    single { InspectImageUseCase(get(), get(), get()) }
    single { AddFromApiUseCase(get(), get(), get(), get()) }
    single { BatchEpisodeCheckUseCase(repository = get(), localDataSource = get()) }
    single { ImportAnimeDbUseCase(get()) }
    single { CollectionPdfGenerator(androidContext()) }
    single<AnimeNotifier> { AnimeNotifierImpl(context = androidContext()) }
}
