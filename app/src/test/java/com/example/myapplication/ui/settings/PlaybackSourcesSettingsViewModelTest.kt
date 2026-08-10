package com.example.myapplication.ui.settings

import com.example.myapplication.media.source.PersonalMediaServerConfig
import com.example.myapplication.media.source.PersonalMediaServerProvider
import com.example.myapplication.media.source.DefaultPlaybackSourceSettingsService
import com.example.myapplication.media.source.PlaybackSourceConfigStore
import com.example.myapplication.media.source.PlaybackSourceConnectionTester
import com.example.myapplication.media.source.PlaybackSourceKind
import com.example.myapplication.media.source.WebDavConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourcesSettingsViewModelTest {

    @Test
    fun `existing secret is masked and reused when saving blank replacement`() {
        val store = FakeStore(
            jellyfin = PersonalMediaServerConfig(
                baseUrl = "https://media.example/jellyfin",
                userId = "user-1",
                accessToken = "stored-secret",
            )
        )
        val viewModel = viewModel(store)

        viewModel.openEditor(PlaybackSourceKind.JELLYFIN)
        assertEquals("", viewModel.uiState.value.editor?.secret)
        assertTrue(viewModel.uiState.value.editor?.hasStoredSecret == true)
        viewModel.updateEditor { it.copy(downloadAllowed = true) }

        assertTrue(viewModel.saveEditor())
        assertEquals("stored-secret", store.jellyfin?.accessToken)
        assertEquals("https://media.example/jellyfin", store.jellyfin?.baseUrl)
        assertNull(viewModel.uiState.value.editor)
        assertTrue(viewModel.uiState.value.sources.first { it.kind == PlaybackSourceKind.JELLYFIN }.configured)
    }

    @Test
    fun `invalid draft reports non secret error and is not saved`() {
        val store = FakeStore()
        val viewModel = viewModel(store)
        viewModel.openEditor(PlaybackSourceKind.EMBY)
        viewModel.updateEditor {
            it.copy(baseUrl = "not-a-url", userId = "user-1", secret = "do-not-echo")
        }

        assertFalse(viewModel.saveEditor())
        assertEquals(PlaybackSourceSettingsMessage.INVALID_CONFIGURATION, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.message.toString().contains("do-not-echo"))
        assertNull(store.emby)
    }

    @Test
    fun `test result and removal update configured state`() = runBlocking {
        val store = FakeStore(
            webDav = WebDavConfig(
                baseUrl = "https://dav.example",
                rootPath = "Movies",
                username = "owner",
                password = "app-password",
            )
        )
        val viewModel = viewModel(store)
        viewModel.openEditor(PlaybackSourceKind.WEBDAV)

        viewModel.testEditorConnectionNow()
        assertEquals(PlaybackSourceSettingsMessage.CONNECTION_OK, viewModel.uiState.value.message)
        viewModel.remove(PlaybackSourceKind.WEBDAV)

        assertFalse(viewModel.uiState.value.sources.first { it.kind == PlaybackSourceKind.WEBDAV }.configured)
        assertNull(store.webDav)
    }

    @Test
    fun `stored secrets are not reused after WebDAV or personal scope changes`() {
        val store = FakeStore(
            webDav = WebDavConfig("https://dav.example", "Movies", "owner", "dav-secret"),
            jellyfin = PersonalMediaServerConfig(
                "https://media.example/jellyfin", "user-1", "server-secret"
            ),
        )
        val viewModel = viewModel(store)

        viewModel.openEditor(PlaybackSourceKind.WEBDAV)
        viewModel.updateEditor { it.copy(baseUrl = "https://attacker.example") }
        assertFalse(viewModel.uiState.value.editor?.hasStoredSecret == true)
        assertFalse(viewModel.saveEditor())
        assertEquals(PlaybackSourceSettingsMessage.SECRET_REQUIRED, viewModel.uiState.value.message)
        assertEquals("https://dav.example", store.webDav?.baseUrl)

        viewModel.openEditor(PlaybackSourceKind.JELLYFIN)
        viewModel.updateEditor { it.copy(userId = "other-user") }
        assertFalse(viewModel.uiState.value.editor?.hasStoredSecret == true)
        assertFalse(viewModel.saveEditor())
        assertEquals(PlaybackSourceSettingsMessage.SECRET_REQUIRED, viewModel.uiState.value.message)
        assertEquals("user-1", store.jellyfin?.userId)
    }

    @Test
    fun `stale probe cannot publish after editor closes`() = runBlocking {
        val gate = CompletableDeferred<Boolean>()
        val store = FakeStore(
            webDav = WebDavConfig("https://dav.example", "Movies", "owner", "secret")
        )
        val service = DefaultPlaybackSourceSettingsService(store, object : PlaybackSourceConnectionTester {
            override suspend fun testWebDav(config: WebDavConfig): Boolean = gate.await()
            override suspend fun testPersonalServer(
                provider: PersonalMediaServerProvider,
                config: PersonalMediaServerConfig,
            ): Boolean = gate.await()
        })
        val viewModel = PlaybackSourcesSettingsViewModel(service)
        viewModel.openEditor(PlaybackSourceKind.WEBDAV)
        val probe = launch { viewModel.testEditorConnectionNow() }
        yield()

        viewModel.closeEditor()
        gate.complete(true)
        probe.join()

        assertNull(viewModel.uiState.value.editor)
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isTesting)
    }

    private fun viewModel(store: FakeStore) = PlaybackSourcesSettingsViewModel(
        DefaultPlaybackSourceSettingsService(store, FakeTester(true))
    )

    private class FakeTester(private val result: Boolean) : PlaybackSourceConnectionTester {
        override suspend fun testWebDav(config: WebDavConfig): Boolean = result
        override suspend fun testPersonalServer(
            provider: PersonalMediaServerProvider,
            config: PersonalMediaServerConfig,
        ): Boolean = result
    }

    private class FakeStore(
        var webDav: WebDavConfig? = null,
        var jellyfin: PersonalMediaServerConfig? = null,
        var emby: PersonalMediaServerConfig? = null,
    ) : PlaybackSourceConfigStore {
        override fun webDav(): WebDavConfig? = webDav
        override fun saveWebDav(config: WebDavConfig) { webDav = config }
        override fun clearWebDav() { webDav = null }
        override fun personalServer(provider: PersonalMediaServerProvider): PersonalMediaServerConfig? =
            if (provider == PersonalMediaServerProvider.JELLYFIN) jellyfin else emby
        override fun savePersonalServer(
            provider: PersonalMediaServerProvider,
            config: PersonalMediaServerConfig,
        ) {
            if (provider == PersonalMediaServerProvider.JELLYFIN) jellyfin = config else emby = config
        }
        override fun clearPersonalServer(provider: PersonalMediaServerProvider) {
            if (provider == PersonalMediaServerProvider.JELLYFIN) jellyfin = null else emby = null
        }
    }
}
