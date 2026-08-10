package com.example.myapplication.media.source

import kotlinx.coroutines.CancellationException

enum class PlaybackSourceKind {
    WEBDAV,
    JELLYFIN,
    EMBY;

    val personalProvider: PersonalMediaServerProvider?
        get() = when (this) {
            WEBDAV -> null
            JELLYFIN -> PersonalMediaServerProvider.JELLYFIN
            EMBY -> PersonalMediaServerProvider.EMBY
        }
}

data class PlaybackSourceConfigurationSummary(
    val kind: PlaybackSourceKind,
    val configured: Boolean,
)

/** Public settings fields only; stored passwords/tokens never cross this boundary. */
data class PlaybackSourcePublicDraft(
    val kind: PlaybackSourceKind,
    val baseUrl: String = "",
    val rootPath: String = "",
    val username: String = "",
    val userId: String = "",
    val hasStoredSecret: Boolean = false,
    val downloadAllowed: Boolean = false,
    val allowInsecureHttp: Boolean = false,
)

interface PlaybackSourceConnectionTester {
    suspend fun testWebDav(config: WebDavConfig): Boolean
    suspend fun testPersonalServer(
        provider: PersonalMediaServerProvider,
        config: PersonalMediaServerConfig,
    ): Boolean
}

interface PlaybackSourceSettingsService {
    fun summaries(): List<PlaybackSourceConfigurationSummary>
    fun draft(kind: PlaybackSourceKind): PlaybackSourcePublicDraft
    fun save(draft: PlaybackSourcePublicDraft, replacementSecret: String): Boolean
    fun remove(kind: PlaybackSourceKind)
    suspend fun test(draft: PlaybackSourcePublicDraft, replacementSecret: String): Boolean?
}

class DefaultPlaybackSourceSettingsService(
    private val store: PlaybackSourceConfigStore,
    private val connectionTester: PlaybackSourceConnectionTester,
) : PlaybackSourceSettingsService {
    override fun summaries(): List<PlaybackSourceConfigurationSummary> = listOf(
        PlaybackSourceConfigurationSummary(PlaybackSourceKind.WEBDAV, store.webDav() != null),
        PlaybackSourceConfigurationSummary(
            PlaybackSourceKind.JELLYFIN,
            store.personalServer(PersonalMediaServerProvider.JELLYFIN) != null,
        ),
        PlaybackSourceConfigurationSummary(
            PlaybackSourceKind.EMBY,
            store.personalServer(PersonalMediaServerProvider.EMBY) != null,
        ),
    )

    override fun draft(kind: PlaybackSourceKind): PlaybackSourcePublicDraft = when (kind) {
        PlaybackSourceKind.WEBDAV -> store.webDav()?.let { config ->
            PlaybackSourcePublicDraft(
                kind = kind,
                baseUrl = config.baseUrl,
                rootPath = config.rootPath,
                username = config.username,
                hasStoredSecret = true,
                downloadAllowed = config.downloadAllowed,
                allowInsecureHttp = config.allowInsecureHttp,
            )
        }
        else -> kind.personalProvider?.let(store::personalServer)?.let { config ->
            PlaybackSourcePublicDraft(
                kind = kind,
                baseUrl = config.baseUrl,
                userId = config.userId,
                hasStoredSecret = true,
                downloadAllowed = config.downloadAllowed,
                allowInsecureHttp = config.allowInsecureHttp,
            )
        }
    } ?: PlaybackSourcePublicDraft(kind)

    override fun save(draft: PlaybackSourcePublicDraft, replacementSecret: String): Boolean =
        runCatching {
            when (draft.kind) {
                PlaybackSourceKind.WEBDAV -> store.saveWebDav(requireNotNull(webDavConfig(draft, replacementSecret)))
                else -> {
                    val provider = requireNotNull(draft.kind.personalProvider)
                    store.savePersonalServer(
                        provider,
                        requireNotNull(personalConfig(draft, replacementSecret, provider)),
                    )
                }
            }
        }.isSuccess

    override fun remove(kind: PlaybackSourceKind) {
        when (kind) {
            PlaybackSourceKind.WEBDAV -> store.clearWebDav()
            else -> store.clearPersonalServer(requireNotNull(kind.personalProvider))
        }
    }

    override suspend fun test(
        draft: PlaybackSourcePublicDraft,
        replacementSecret: String,
    ): Boolean? = try {
        when (draft.kind) {
            PlaybackSourceKind.WEBDAV -> webDavConfig(draft, replacementSecret)
                ?.let { connectionTester.testWebDav(it) }
            else -> draft.kind.personalProvider?.let { provider ->
                personalConfig(draft, replacementSecret, provider)
                    ?.let { connectionTester.testPersonalServer(provider, it) }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun webDavConfig(
        draft: PlaybackSourcePublicDraft,
        replacementSecret: String,
    ): WebDavConfig? {
        val saved = store.webDav()
        val secret = replacementSecret.ifBlank {
            saved?.password?.takeIf { saved.canReuseSecretFor(draft) }.orEmpty()
        }
        return WebDavConfig(
            baseUrl = draft.baseUrl,
            rootPath = draft.rootPath,
            username = draft.username,
            password = secret,
            downloadAllowed = draft.downloadAllowed,
            allowInsecureHttp = draft.allowInsecureHttp,
        ).takeIf(WebDavConfig::isValid)
    }

    private fun personalConfig(
        draft: PlaybackSourcePublicDraft,
        replacementSecret: String,
        provider: PersonalMediaServerProvider,
    ): PersonalMediaServerConfig? {
        val saved = store.personalServer(provider)
        val secret = replacementSecret.ifBlank {
            saved?.accessToken?.takeIf { saved.canReuseSecretFor(draft, provider) }.orEmpty()
        }
        return PersonalMediaServerConfig(
            baseUrl = draft.baseUrl,
            userId = draft.userId,
            accessToken = secret,
            downloadAllowed = draft.downloadAllowed,
            allowInsecureHttp = draft.allowInsecureHttp,
        ).takeIf(PersonalMediaServerConfig::isValid)
    }
}

private fun WebDavConfig.canReuseSecretFor(draft: PlaybackSourcePublicDraft): Boolean {
    val candidate = copy(
        baseUrl = draft.baseUrl,
        rootPath = draft.rootPath,
        username = draft.username,
    )
    return username == draft.username && runCatching { credentialRef() == candidate.credentialRef() }
        .getOrDefault(false)
}

private fun PersonalMediaServerConfig.canReuseSecretFor(
    draft: PlaybackSourcePublicDraft,
    provider: PersonalMediaServerProvider,
): Boolean {
    val candidate = copy(baseUrl = draft.baseUrl, userId = draft.userId)
    return runCatching { credentialRef(provider) == candidate.credentialRef(provider) }
        .getOrDefault(false)
}
