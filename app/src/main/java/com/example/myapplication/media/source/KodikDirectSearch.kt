package com.example.myapplication.media.source

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.DiscoveredSeason
import com.example.myapplication.sync.TitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Iframe-кандидат Kodik: адрес плеера плюс имя озвучки для подписи хостера.
 * Общий тип для обоих путей поиска (YummyAnime и прямой kodik-api), чтобы [KodikSource]
 * мог дедуплицировать их одним списком до запуска экстрактора.
 */
internal data class KodikIframeCandidate(
    val iframeUrl: String,
    val dubbing: String,
)

/**
 * Второй, независимый от YummyAnime путь к iframe Kodik: прямой поиск через `kodik-api.com`.
 *
 * Нужен потому, что YummyAnime — узкое место: если он не проиндексировал тайтл, отдал чужой slug
 * или просто лёг, Kodik выпадал целиком, хотя сам Kodik жив. Здесь мы спрашиваем Kodik напрямую.
 *
 * Формат поля `link` в ответе (`//kodik.info/serial/7497/<hash>/720p`) совпадает с тем, что уже
 * разбирает `KodikExtractor`, поэтому экстрактор не трогаем — только поставляем ему iframe.
 */
class KodikDirectSearch(
    private val client: OkHttpClient,
    appContext: Context,
) {
    private val assets = appContext.applicationContext.assets

    /**
     * Кандидаты на конкретную серию. Ошибки наружу не выпускаем: этот путь — дополнение,
     * его отказ не должен ронять резолв Kodik целиком.
     */
    internal suspend fun findEpisodeCandidates(
        anime: Anime,
        episodeNumber: Int,
        limit: Int,
    ): List<KodikIframeCandidate> {
        if (episodeNumber <= 0 || limit <= 0) return emptyList()
        val queries = listOfNotNull(anime.titleRu, anime.title, anime.titleEn)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        if (queries.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            for (query in queries) {
                val payload = search(query) ?: continue
                val candidates = pickCandidates(payload, queries, episodeNumber, limit)
                if (candidates.isNotEmpty()) {
                    Log.i(TAG, "Kodik direct '$query' ep=$episodeNumber candidates=${candidates.size}")
                    return@withContext candidates
                }
            }
            emptyList()
        }
    }

    /**
     * Разложение тайтла по сезонам глазами Kodik — для «Найти ещё» (§ [StreamingSeasonDiscovery]).
     *
     * Тот же самый поисковый запрос, что и для серий: `with_episodes=true` возвращает
     * `results[].seasons.{N}.episodes.{M}`, то есть готовую карту «сезон → серии». Каждая озвучка
     * приходит отдельным `result`, и залиты они по-разному, поэтому по каждому сезону берём
     * максимум по всем результатам.
     */
    suspend fun findSeasons(anime: Anime): List<DiscoveredSeason> {
        val queries = listOfNotNull(anime.titleRu, anime.title, anime.titleEn)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        if (queries.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            val episodesBySeason = LinkedHashMap<Int, Int>()
            for (query in queries) {
                val payload = search(query) ?: continue
                payload.optJSONArray("results").objects()
                    .filter { result -> score(queries, result) >= TitleMatcher.MATCH_THRESHOLD }
                    .forEach { result -> collectSeasons(result, episodesBySeason) }
                if (episodesBySeason.isNotEmpty()) break
            }
            episodesBySeason
                .map { (number, episodes) -> DiscoveredSeason(number, episodes, SOURCE_NAME) }
                .sortedBy { it.seasonNumber }
                .also { Log.i(TAG, "Kodik seasons for '${anime.title}': ${it.size}") }
        }
    }

    private fun collectSeasons(result: JSONObject, into: MutableMap<Int, Int>) {
        val seasons = result.optJSONObject("seasons")
        if (seasons != null) {
            val keys = seasons.keys()
            var found = false
            while (keys.hasNext()) {
                val key = keys.next()
                // Сезон «0» у Kodik — спецвыпуски и OVA, в нумерацию сезонов они не входят.
                val number = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
                val episodes = seasons.optJSONObject(key)?.optJSONObject("episodes")?.length() ?: 0
                if (episodes > 0) {
                    into.merge(number, episodes, ::maxOf)
                    found = true
                }
            }
            if (found) return
        }
        // Без карты серий Kodik всё равно сообщает, до какого сезона/серии докатился релиз.
        val lastSeason = result.optInt("last_season", 0)
        val lastEpisode = result.optInt("last_episode", 0)
        if (lastSeason > 0 && lastEpisode > 0) into.merge(lastSeason, lastEpisode, ::maxOf)
    }

    // region Отбор кандидатов

    private fun pickCandidates(
        payload: JSONObject,
        localTitles: List<String>,
        episode: Int,
        limit: Int,
    ): List<KodikIframeCandidate> = payload.optJSONArray("results").objects()
        // Kodik по названию охотно отдаёт «похожее»: без порога матчера в выдачу уедет чужой тайтл.
        .filter { result -> score(localTitles, result) >= TitleMatcher.MATCH_THRESHOLD }
        .mapNotNull { result -> toCandidate(result, episode) }
        .distinctBy { it.iframeUrl }
        .take(limit)

    private fun score(localTitles: List<String>, result: JSONObject): Double {
        val remote = buildList {
            result.optString("title").trim().takeIf(String::isNotBlank)?.let(::add)
            result.optString("title_orig").trim().takeIf(String::isNotBlank)?.let(::add)
            // other_title — одна строка со всеми синонимами через " / ", а матчер сравнивает
            // названия целиком, поэтому режем её на отдельные кандидаты.
            result.optString("other_title").split(" / ")
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(::add)
        }
        return localTitles.maxOfOrNull { TitleMatcher.bestScore(it, remote) } ?: 0.0
    }

    private fun toCandidate(result: JSONObject, episode: Int): KodikIframeCandidate? {
        val baseLink = normalizeUrl(result.optString("link")) ?: return null
        val isSerial = result.optString("type").contains("serial", ignoreCase = true)

        val iframe = if (!isSerial) {
            // Фильм/OVA одной серией: отдаём только когда просят первую — иначе это не та серия.
            if (episode != 1) return null
            baseLink
        } else {
            // Релиз ещё не докатился до нужной серии — не гоняем экстрактор впустую.
            val lastEpisode = result.optInt("last_episode", 0)
            if (lastEpisode > 0 && episode > lastEpisode) return null
            // with_episodes даёт прямую ссылку на серию; если её нет — сезонный плеер умеет
            // переключаться сам по query-параметрам (так же делает референсный парсер).
            episodeLink(result.optJSONObject("seasons"), episode)
                ?: baseLink.withEpisodeParams(result.optInt("last_season", 1), episode)
        }

        val translation = result.optJSONObject("translation")
        val name = translation?.optString("title")?.trim()?.takeIf(String::isNotBlank) ?: "Kodik"
        val dubbing = if (translation?.optString("type") == "subtitles") "$name (субтитры)" else name
        return KodikIframeCandidate(iframeUrl = iframe, dubbing = dubbing)
    }

    /** Ссылка на конкретную серию из `seasons.{N}.episodes.{M}`; значение — строка либо объект. */
    private fun episodeLink(seasons: JSONObject?, episode: Int): String? {
        val root = seasons ?: return null
        val key = episode.toString()
        val seasonKeys = root.keys()
        while (seasonKeys.hasNext()) {
            val episodes = root.optJSONObject(seasonKeys.next())?.optJSONObject("episodes") ?: continue
            val raw = episodes.opt(key) ?: continue
            val link = when (raw) {
                is String -> raw
                is JSONObject -> raw.optString("link")
                else -> ""
            }
            normalizeUrl(link)?.let { return it }
        }
        return null
    }

    private fun String.withEpisodeParams(season: Int, episode: Int): String {
        val separator = if (contains('?')) '&' else '?'
        return "$this${separator}season=${season.coerceAtLeast(1)}&episode=$episode"
    }

    // endregion

    // region Сеть и токены

    private fun search(title: String): JSONObject? {
        // Сначала токен, который уже сработал в этом процессе: перебор — это лишние запросы
        // на каждую серию, а живой токен меняется куда реже.
        val cached = KodikTokenCache.working
        if (cached != null) {
            when (val outcome = requestSearch(cached, title)) {
                is SearchOutcome.Ok -> return outcome.payload
                SearchOutcome.TokenRejected -> KodikTokenCache.working = null
                SearchOutcome.Failed -> return null
            }
        }
        if (KodikTokenCache.exhausted) return null

        val tokens = loadTokens()
        var allRejected = true
        for (token in tokens) {
            if (token == cached) continue // только что отвергнут — второй раз не спрашиваем
            when (val outcome = requestSearch(token, title)) {
                is SearchOutcome.Ok -> {
                    KodikTokenCache.working = token
                    return outcome.payload
                }
                // Токен протух — пробуем следующий.
                SearchOutcome.TokenRejected -> Unit
                // Сеть/сервис: токен, возможно, живой, просто не повезло — не хороним путь целиком.
                SearchOutcome.Failed -> allRejected = false
            }
        }
        if (allRejected) {
            // Все токены протухли: путь выключается до перезапуска, YummyAnime продолжает работать.
            KodikTokenCache.exhausted = true
            Log.i(TAG, "All Kodik tokens rejected (${tokens.size}), direct search disabled")
        }
        return null
    }

    private fun requestSearch(token: String, title: String): SearchOutcome = runCatching {
        val url = "$API_ORIGIN/search".toHttpUrl().newBuilder()
            .addQueryParameter("token", token)
            .addQueryParameter("title", title)
            .addQueryParameter("limit", SEARCH_LIMIT)
            .addQueryParameter("types", "anime,anime-serial")
            .addQueryParameter("with_episodes", "true")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .header("Accept", "application/json,*/*")
            .header("User-Agent", KodikSource.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val payload = body.takeIf { it.trimStart().startsWith("{") }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            when {
                payload == null -> {
                    Log.i(TAG, "Kodik api HTTP ${response.code}, non-json body")
                    SearchOutcome.Failed
                }
                payload.has("error") -> {
                    val error = payload.optString("error")
                    // Единственная причина идти к следующему токену — именно отказ по токену.
                    if (error.contains("токен", ignoreCase = true)) {
                        SearchOutcome.TokenRejected
                    } else {
                        Log.i(TAG, "Kodik api error: $error")
                        SearchOutcome.Failed
                    }
                }
                !payload.has("results") -> SearchOutcome.Failed
                else -> SearchOutcome.Ok(payload)
            }
        }
    }.onFailure { Log.i(TAG, "Kodik api request failed: ${it.message}") }
        .getOrDefault(SearchOutcome.Failed)

    /**
     * Апстрим отдаёт токены в ассете закодированными (половинки base64, склеенные задом наперёд) —
     * раскодируем в том же виде, в каком они там лежат.
     *
     * Тир `dead` пропускаем целиком, внутри тира вперёд идут токены с доступной функцией `search`.
     * Токены чужие и датированы апрелем: когда они протухнут, путь просто выключится, а поиск
     * через YummyAnime продолжит работать — на это рассчитан весь метод.
     */
    private fun loadTokens(): List<String> {
        KodikTokenCache.tokens?.let { return it }
        val raw = runCatching {
            assets.open(TOKENS_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        }.onFailure { Log.i(TAG, "Kodik tokens asset unavailable: ${it.message}") }.getOrNull()
        val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
        val decoded = buildList<String> {
            root ?: return@buildList
            TIER_ORDER.forEach { tier ->
                root.optJSONArray(tier).objects()
                    .sortedByDescending { entry ->
                        entry.optJSONObject("functions_availability")?.optBoolean("search") == true
                    }
                    .forEach { entry -> decryptToken(entry.optString("tokn"))?.let(::add) }
            }
        }.distinct()
        KodikTokenCache.tokens = decoded
        return decoded
    }

    // endregion

    private sealed interface SearchOutcome {
        data class Ok(val payload: JSONObject) : SearchOutcome
        /** Сервер явно отказал по токену — имеет смысл взять следующий. */
        data object TokenRejected : SearchOutcome
        /** Сеть/сервис/мусор в ответе — смена токена тут не поможет. */
        data object Failed : SearchOutcome
    }

    companion object {
        private const val TAG = "KodikDirectSearch"
        /** Совпадает со значением в [com.example.myapplication.domain.seasons.StreamingSeasonDiscovery.STREAMING_SOURCES]. */
        private const val SOURCE_NAME = "Kodik"
        private const val API_ORIGIN = "https://kodik-api.com"
        private const val TOKENS_ASSET = "kodik_tokens.json"
        private const val SEARCH_LIMIT = "100"
        /** `dead` в списке нет намеренно: эти токены заведомо мертвы. */
        private val TIER_ORDER = listOf("stable", "unstable", "legacy")
    }
}

/**
 * Процессный кэш токенов: список ассета парсится один раз, рабочий токен переживает
 * пересоздание [KodikDirectSearch]. @Volatile — резолв идёт из параллельных корутин на IO.
 */
private object KodikTokenCache {
    @Volatile
    var tokens: List<String>? = null

    @Volatile
    var working: String? = null

    /** Все токены отвергнуты — не долбим сеть на каждой серии. */
    @Volatile
    var exhausted: Boolean = false
}

/**
 * Обратная операция к шифрованию из референса: половины токена кодируются в base64 по отдельности
 * и склеиваются перевёрнутыми в обратном порядке.
 */
private fun decryptToken(raw: String): String? {
    val value = raw.trim()
    if (value.length < 4 || value.length % 2 != 0) return null
    val half = value.length / 2
    val first = value.substring(0, half).reversed().decodeTokenPart() ?: return null
    val second = value.substring(half).reversed().decodeTokenPart() ?: return null
    return (second + first).takeIf { it.matches(TOKEN_SHAPE) }
}

/** Ожидаемый вид расшифрованного токена — 32 hex-символа; всё прочее считаем битым ассетом. */
private val TOKEN_SHAPE = Regex("^[0-9a-fA-F]{32}$")

private fun String.decodeTokenPart(): String? = runCatching {
    String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)
}.getOrNull()
