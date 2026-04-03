package com.example.myapplication.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.local.DeveloperMirrorCoordinator
import com.example.myapplication.data.local.ImageStorageRepositoryImpl
import com.example.myapplication.data.local.LegacyMigrationRepositoryImpl
import com.example.myapplication.data.local.MigrationManager
import com.example.myapplication.data.local.SQLDelightDatabaseFactory
import com.example.myapplication.data.local.VetroPublicDbExporter
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.data.repository.LegacyMigrationRepository
import com.example.myapplication.data.local.AndroidPermissionChecker
import com.example.myapplication.domain.IdGenerator
import com.example.myapplication.domain.PermissionChecker
import com.example.myapplication.domain.RealIdGenerator
import com.example.myapplication.domain.addedit.GetAnimeForEditUseCase
import com.example.myapplication.domain.addedit.SaveAnimeUseCase
import com.example.myapplication.domain.addedit.UpdateCommentUseCase
import com.example.myapplication.DropboxSyncManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val Context.migrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "migration_prefs")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

val databaseModule = module {
    single { SQLDelightDatabaseFactory(androidContext()) }
    single { VetroPublicDbExporter(androidContext(), get()) }
    single { DeveloperMirrorCoordinator(settingsDataStore = get(named("settings")), exporter = get()) }
    single { AnimeLocalDataSource(get(), get()) }
    single<ImageStorageRepository> { ImageStorageRepositoryImpl(context = androidContext(), httpClient = get()) }
    single<PermissionChecker> { AndroidPermissionChecker(androidContext()) }
    single<IdGenerator> { RealIdGenerator() }
    single { GetAnimeForEditUseCase(get()) }
    single { SaveAnimeUseCase(get(), get(), get()) }
    single { UpdateCommentUseCase(get()) }
    single<LegacyMigrationRepository> {
        LegacyMigrationRepositoryImpl(context = androidContext())
    }

    single<DataStore<Preferences>>(named("migration")) {
        androidContext().migrationDataStore
    }
    single<DataStore<Preferences>>(named("settings")) {
        androidContext().settingsDataStore
    }
    single {
        MigrationManager(
            context = androidContext(),
            localDataSource = get(),
            dataStore = get(named("migration"))
        )
    }

    single<DropboxSyncManager> {
        DropboxSyncManager(
            context = androidContext(),
            databaseFactory = get(),
            animeLocalDataSource = get()
        )
    }
}
