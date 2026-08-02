package com.example.myapplication.media.source

import com.example.myapplication.sync.TitleMatcher
import java.net.URI
import org.jsoup.Jsoup

internal data class JutSuTitleCandidate(
    val title: String,
    val url: String,
    val aliases: List<String> = emptyList(),
)

internal data class JutSuSearchResponse(
    val finalUrl: String,
    val html: String,
)

internal fun orderedJutSuAliases(
    titleRu: String?,
    title: String?,
    titleEn: String?,
): List<String> = listOf(titleRu, title, titleEn)
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .distinctBy(String::lowercase)

/**
 * jut.su's exact redirect search treats punctuation inconsistently. For example,
 * `Повар-боец Сома` lands on its JavaScript search page while `Повар боец Сома` redirects to the
 * title. Keep the authoritative aliases in their requested order, then retry each punctuation-heavy
 * alias as words. Candidate acceptance still uses the original aliases and the strict title score.
 */
internal fun jutSuSearchQueries(aliases: List<String>): List<String> = buildList {
    aliases.forEach { alias ->
        add(alias)
        val asWords = alias
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
        if (asWords.isNotEmpty()) add(asWords)
    }
}.distinctBy(String::lowercase)

internal fun selectJutSuTitleCandidate(
    localTitles: List<String>,
    candidates: List<JutSuTitleCandidate>,
    threshold: Double = JUTSU_TITLE_MATCH_THRESHOLD,
): JutSuTitleCandidate? {
    if (localTitles.isEmpty()) return null
    return candidates
        .distinctBy { it.url }
        .map { candidate ->
            val remoteTitles = (listOf(candidate.title) + candidate.aliases)
                .filter(String::isNotBlank)
            val score = localTitles.maxOfOrNull { TitleMatcher.bestScore(it, remoteTitles) } ?: 0.0
            candidate to score
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second >= threshold }
        ?.first
}

internal fun parseJutSuTitleCandidates(html: String, baseUrl: String): List<JutSuTitleCandidate> {
    val baseHost = runCatching { URI(baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
    val doc = Jsoup.parse(html, baseUrl)
    return doc.select("#dle-content a[href], .content a[href]")
        .mapNotNull { anchor ->
            val url = anchor.absUrl("href")
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.host.orEmpty().lowercase() != baseHost) return@mapNotNull null
            val slug = uri.path.trim('/').takeIf {
                it.isNotEmpty() && '/' !in it && it !in JUTSU_NON_TITLE_PATHS
            } ?: return@mapNotNull null
            val aliases = listOf(
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.parent()?.selectFirst("h1, h2, h3")?.text().orEmpty(),
            ).filter(String::isNotBlank)
            val title = anchor.text().takeIf(String::isNotBlank)
                ?: aliases.firstOrNull()
                ?: slug.replace('-', ' ')
            JutSuTitleCandidate(title, "${uri.scheme}://${uri.authority}/$slug/", aliases)
        }
        .distinctBy { it.url }
}

/**
 * Evaluates network attempts in order. A JavaScript-only search landing page has no static
 * candidate, so the next punctuation-normalized query can still win via jut.su's direct redirect.
 */
internal fun selectJutSuSearchResponse(
    localTitles: List<String>,
    baseUrl: String,
    responses: List<JutSuSearchResponse>,
): JutSuTitleCandidate? {
    val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
    return responses.firstNotNullOfOrNull { response ->
        val finalUri = runCatching { URI(response.finalUrl) }.getOrNull()
        val directSlug = finalUri
            ?.takeIf { it.host.equals(baseUri.host, ignoreCase = true) }
            ?.path
            ?.trim('/')
            ?.takeIf {
                it.isNotEmpty() && '/' !in it && it !in JUTSU_NON_TITLE_PATHS
            }
        val candidates = buildList {
            if (directSlug != null) {
                val doc = Jsoup.parse(response.html, response.finalUrl)
                val remoteTitles = buildList {
                    doc.select("meta[itemprop=name][content], meta[itemprop=alternateName][content]")
                        .mapTo(this) { it.attr("content") }
                    doc.selectFirst("h1, .anime_title, title")?.text()?.let(::add)
                    add(directSlug.replace('-', ' '))
                }.filter(String::isNotBlank)
                add(
                    JutSuTitleCandidate(
                        title = remoteTitles.firstOrNull().orEmpty(),
                        url = "${finalUri.scheme}://${finalUri.authority}/$directSlug/",
                        aliases = remoteTitles.drop(1),
                    )
                )
            }
            addAll(parseJutSuTitleCandidates(response.html, baseUrl))
        }
        selectJutSuTitleCandidate(localTitles, candidates)
    }
}

internal const val JUTSU_TITLE_MATCH_THRESHOLD = 0.91

internal val JUTSU_NON_TITLE_PATHS = setOf(
    "anime", "search", "lookfor", "login", "register", "top", "new", "ongoing",
    "films", "manga", "forum", "user", "pm", "favicon.ico",
)
