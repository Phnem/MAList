package com.example.myapplication.di

import com.phnem.vetro.BuildConfig
import com.example.myapplication.data.remote.GeminiStructuredClient
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.domain.inspect.InspectImageUseCase
import com.example.myapplication.domain.search.AddFromApiUseCase
import com.example.myapplication.notifications.AnimeNotifier
import com.example.myapplication.notifications.AnimeNotifierImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single<AnimeRepository> { AnimeRepository(apiService = get(), localDataSource = get()) }
    single<GenreRepository> { GenreRepository() }
    single { GeminiStructuredClient(get(), BuildConfig.GEMINI_API_KEY) }
    single { InspectImageUseCase(get(), get(), get()) }
    single { AddFromApiUseCase(get(), get(), get(), get()) }
    single<AnimeNotifier> { AnimeNotifierImpl(context = androidContext()) }
}
