package com.example.myapplication.media.episode

/**
 * Диапазон серий сезона: есть ли соседняя серия ПО НОМЕРУ.
 *
 * Ничего не знает ни об источниках, ни о ссылках. «Соседняя серия существует» и «её ссылку удалось
 * зарезолвить» — разные состояния (правило спеки): номер может быть в пределах сезона, а источник
 * ссылку не отдать. Поэтому неизвестное число серий трактуется как «может быть», а не «конец» —
 * иначе неудача резолва выглядела бы как отсутствие серии.
 */
object EpisodeRange {

    /** Серии нумеруются с единицы: элемента «серия 0» не существует. */
    const val FIRST_EPISODE = 1

    fun hasPrevious(episode: Int): Boolean = episode > FIRST_EPISODE

    /**
     * @param availableEpisodes сколько серий сезона доступно (`SeasonInfo.episodes`); `null` или
     *   `0` = не разрешено, то есть неизвестно.
     */
    fun hasNext(episode: Int, availableEpisodes: Int?): Boolean {
        val known = availableEpisodes?.takeIf { it > 0 } ?: return true
        return episode < known
    }

    fun previousOf(episode: Int): Int? = (episode - 1).takeIf { hasPrevious(episode) }

    fun nextOf(episode: Int, availableEpisodes: Int?): Int? =
        (episode + 1).takeIf { hasNext(episode, availableEpisodes) }
}
