package com.example.myapplication.manga.domain

import com.example.myapplication.manga.data.ChapterReadingProgress

/**
 * Сводка чтения тайтла для карточки главного экрана: сколько глав прочитано из скольких и
 * вышли ли новые. Та же роль, что у прогресса серий у аниме, только единица счёта — глава.
 */
data class MangaReadingSummary(
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
 * Прогресс чтения по главам.
 *
 * Прочитанной считается только глава с липкой отметкой [ChapterReadingProgress.read] — открытая,
 * но не дочитанная в счёт не идёт.
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
    val readInToc = chapters.count { it.key in readKeys }
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
