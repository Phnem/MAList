package com.example.myapplication.manga.domain

import com.example.myapplication.manga.data.ChapterReadingProgress

/**
 * Сводка чтения тайтла для карточки главного экрана: сколько глав прочитано из скольких и
 * вышли ли новые. Та же роль, что у прогресса серий у аниме, только единица счёта — глава.
 */
data class MangaReadingSummary(
    /**
     * Длина прочитанного отрезка оглавления, а не число отметок: прочитана 16-я глава — значит,
     * прочитаны и все до неё. Отдельными отметками помечена бывает пара глав из середины, и
     * счёт по ним показывал бы «1 / 92» человеку, который дочитал до шестнадцатой.
     */
    val readChapters: Int,
    /**
     * Сколько глав в оглавлении источника. `null` = оглавление неизвестно (тайтл не открывали,
     * либо кэш вычищен). Знаменатель в этом случае не рисуем — «5 / 0» врёт, а «5 гл.» нет.
     */
    val totalChapters: Int?,
    /** Главы, вышедшие уже после того, как пользователь читал этот тайтл, и не прочитанные им. */
    val newChapters: Int,
) {
    val hasNewChapters: Boolean get() = newChapters > 0

    /** Есть ли что показывать на карточке вообще. */
    val hasProgress: Boolean get() = readChapters > 0 || hasNewChapters

    /** Доля прочитанного для бара. `null`, когда знаменатель неизвестен — бар не рисуется. */
    val fraction: Float?
        get() = totalChapters
            ?.takeIf { it > 0 }
            ?.let { (readChapters.toFloat() / it).coerceIn(0f, 1f) }
}

/**
 * Оглавление на выбранном языке перевода.
 *
 * Ровно то же правило, по которому список глав отбирается во вкладке «Главы»: если на выбранном
 * языке глав нет, отдаём всё оглавление, а не пустоту. Вынесено сюда, чтобы карточка и вкладка
 * считали по одному списку, а не разошлись в числах.
 */
fun chaptersForLanguage(
    chapters: List<MangaChapter>,
    preferredLanguage: String?,
): List<MangaChapter> = preferredLanguage
    ?.let { lang -> chapters.filter { it.language == lang } }
    ?.takeIf { it.isNotEmpty() }
    ?: chapters

/**
 * Граница прочитанного — номер самой дальней главы с отметкой «прочитано».
 *
 * По номеру, а не по позиции в списке: порядок глав задаёт источник, и завязываться на него значит
 * получить другой ответ, стоит списку прийти перевёрнутым. `null` = прочитанных нумерованных глав
 * нет (или не прочитано вообще ничего).
 */
private fun readingFrontier(
    chapters: List<MangaChapter>,
    readKeys: Set<String>,
): Double? = chapters
    .filter { it.key in readKeys }
    .mapNotNull { it.number }
    .maxOrNull()

/**
 * Лежит ли глава внутри прочитанного отрезка.
 *
 * Ненумерованные главы (пролог, экстра) в отрезок не попадают: их место в оглавлении определяется
 * датой, а не номером, и «всё до неё» для них не определено — такие считаются прочитанными только
 * по собственной отметке.
 */
private fun MangaChapter.isWithinFrontier(frontier: Double?, readKeys: Set<String>): Boolean {
    val n = number ?: return key in readKeys
    return frontier != null && n <= frontier
}

/**
 * Ключи глав, которые прочитаны по границе, но отметки не имеют.
 *
 * Нужны, чтобы список глав не расходился с числом на карточке: карточка считает по отрезку, а
 * галочки стоят только там, где пользователь дочитал до последней страницы. Пустой список = чинить
 * нечего, писать в хранилище не нужно.
 *
 * @param chapters оглавление, уже суженное до нужного языка ([chaptersForLanguage]).
 */
fun chaptersToMarkRead(
    chapters: List<MangaChapter>,
    progress: Map<String, ChapterReadingProgress>,
): List<String> {
    val readKeys = progress.filterValues { it.read }.keys
    if (chapters.isEmpty() || readKeys.isEmpty()) return emptyList()
    val frontier = readingFrontier(chapters, readKeys) ?: return emptyList()
    return chapters
        .filter { it.key !in readKeys && it.isWithinFrontier(frontier, readKeys) }
        .map { it.key }
}

/**
 * Прогресс чтения по главам.
 *
 * Прочитанным считается отрезок оглавления до самой дальней главы с липкой отметкой
 * [ChapterReadingProgress.read] включительно. Открытая, но не дочитанная глава границу не двигает.
 *
 * **Новыми** считаются главы, опубликованные ПОЗЖЕ последнего чтения этого тайтла и ещё не
 * прочитанные. Просто непрочитанная глава новой не является: иначе метка горела бы на каждом
 * брошенном на середине тайтле. Пока пользователь не прочитал ни одной главы, новых нет вовсе —
 * ему не с чем сравнивать.
 *
 * @param chapters оглавление, уже суженное до нужного языка ([chaptersForLanguage]).
 * @param progress отметки чтения тайтла, ключ — [MangaChapter.key].
 */
fun summarizeMangaReading(
    chapters: List<MangaChapter>,
    progress: Map<String, ChapterReadingProgress>,
): MangaReadingSummary {
    val readKeys = progress.filterValues { it.read }.keys
    if (chapters.isEmpty()) {
        // Оглавления нет — сверять не с чем, но прочитанное пользователем известно и без него.
        return MangaReadingSummary(
            readChapters = readKeys.size,
            totalChapters = null,
            newChapters = 0,
        )
    }
    // Считаем по оглавлению, а не по отметкам: после смены источника или языка в прогрессе
    // остаются ключи глав, которых в текущем списке нет, и счётчик уехал бы за знаменатель.
    val frontier = readingFrontier(chapters, readKeys)
    val readInToc = chapters.count { it.isWithinFrontier(frontier, readKeys) }
    val lastReadAt = progress
        .filterValues { it.read }
        .values
        .maxOfOrNull { it.updatedAt }
        ?: 0L
    val newChapters = if (lastReadAt <= 0L) {
        0
    } else {
        chapters.count { it.key !in readKeys && it.publishedAt > lastReadAt }
    }
    return MangaReadingSummary(
        readChapters = readInToc,
        totalChapters = chapters.size,
        newChapters = newChapters,
    )
}
