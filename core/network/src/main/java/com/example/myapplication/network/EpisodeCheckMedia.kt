package com.example.myapplication.network

/**
 * Компактный снимок Media для батч-проверки новых серий.
 * [airedEpisodes] — сколько серий реально вышло: для онгоингов AniList отдаёт
 * `episodes = null`, поэтому считаем `nextAiringEpisode.episode - 1`.
 */
data class EpisodeCheckMedia(
    val anilistId: Int,
    val malId: Int?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val format: String?,
    val status: String?,
    /** Заявленное число серий (null у онгоингов без анонса). */
    val totalEpisodes: Int?,
    /** Вышедшие серии (см. kdoc класса). */
    val airedEpisodes: Int,
    /** Прямые связи франшизы: только PREQUEL/SEQUEL, только аниме. */
    val relations: List<EpisodeCheckRelation>,
)

data class EpisodeCheckRelation(
    val anilistId: Int,
    /** "PREQUEL" | "SEQUEL" */
    val relationType: String,
    val format: String?,
    val status: String?,
    val totalEpisodes: Int?,
    val airedEpisodes: Int,
)
