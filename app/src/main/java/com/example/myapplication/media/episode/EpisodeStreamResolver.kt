package com.example.myapplication.media.episode

import com.example.myapplication.media.source.VetroVideo

/**
 * Узкий интерфейс «дай ссылки для серии»: плеер знает только номер серии и желаемое разрешение,
 * ни о хостерах, ни об источниках, ни о том, как строится [com.example.myapplication.data.models.Anime]
 * для резолва, он не осведомлён.
 *
 * Возвращает кандидатов, лучший первым (порядок — от `rankVideosForResolution`). **Пустой список
 * означает «ссылку достать не удалось», а не «серии не существует»** — существование серии решает
 * [EpisodeRange] и только по номеру.
 */
fun interface EpisodeStreamResolver {
    suspend fun resolve(episode: Int, preferredResolution: Int): List<VetroVideo>
}
