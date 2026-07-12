package com.example.myapplication.di

import com.example.myapplication.ui.addedit.AddEditViewModel
import com.example.myapplication.ui.details.DetailsViewModel
import com.example.myapplication.ui.home.HomeViewModel
import com.example.myapplication.ui.inspect.InspectViewModel
import com.example.myapplication.ui.settings.SettingsViewModel
import com.example.myapplication.ui.splash.SplashViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        SplashViewModel(
            legacyStorageMigrator = get(),
            legacyCollectionSafMigrator = get(),
            migrationManager = get(),
            authRepository = get(),
            appUpdateRepository = get(),
            imageCompressionMigrator = get(),
        )
    }
    viewModel {
        HomeViewModel(
            repository = get(),
            localDataSource = get(),
            notifier = get(),
            imageStorage = get(),
            settingsDataStore = get(named("settings")),
            addFromApiUseCase = get(),
            statsFooterPhraseUseCase = get(),
            batchEpisodeCheckUseCase = get()
        )
    }
    viewModel {
        AddEditViewModel(
            getAnimeUseCase = get(),
            saveAnimeUseCase = get(),
            updateCommentUseCase = get(),
            imageStorage = get(),
            settingsDataStore = get(named("settings"))
        )
    }
    viewModel {
        SettingsViewModel(
            repository = get(),
            appUpdateRepository = get(),
            settingsDataStore = get(named("settings")),
            databaseFactory = get(),
            importAnimeDbUseCase = get(),
            repairAnimeDbUseCase = get(),
            collectionPdfGenerator = get(),
            app = androidApplication()
        )
    }
    viewModel {
        InspectViewModel(
            inspectImageUseCase = get(),
            localDataSource = get(),
            addFromApiUseCase = get(),
            settingsDataStore = get(named("settings")),
            geminiApiKeyRepository = get()
        )
    }
    viewModel { (animeId: String) ->
        DetailsViewModel(
            animeId = animeId,
            repository = get(),
            settingsDataStore = get(named("settings")),
            imageStorage = get()
        )
    }
}
