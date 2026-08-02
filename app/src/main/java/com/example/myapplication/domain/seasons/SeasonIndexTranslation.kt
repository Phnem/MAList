package com.example.myapplication.domain.seasons

/**
 * Трансляция номера сезона между шкалой КАТАЛОГА и шкалой ИСТОЧНИКА просмотра.
 *
 * Это две разные величины, которые до сих пор отождествлялись. Каталог нумерует сезоны по
 * цепочке франшизы AniList и считает расколотый кор отдельным сезоном; источники просмотра
 * держат оба кора внутри одного сезона. У «Повар-боец Сома» отсюда шесть сезонов в каталоге
 * против пяти у сайтов — и запрос шестого уходил в никуда, а четвёртый молча указывал на
 * чужой контент.
 *
 * Связь между шкалами регулярна: сезон источника — это склейка одного или нескольких подряд
 * идущих каталожных сезонов, а суммарное число серий сохраняется. На этом и строится
 * сопоставление.
 */

/** Сезон и число серий в нём — то общее, что есть у обеих шкал. */
internal data class SeasonSpan(val seasonNumber: Int, val episodes: Int)

internal data class SeasonScaleAlignment(
    /** Номер сезона каталога → номер сезона источника. */
    val catalogueToSource: Map<Int, Int>,
    /**
     * Сезоны ИСТОЧНИКА, накрывающие ровно один каталожный сезон. Только для них число серий
     * источника можно переносить в каталожную строку: сезон-склейка знает сумму по нескольким
     * сезонам, и записывать её в один — значит завысить его.
     */
    val oneToOneSourceSeasons: Set<Int>,
)

/**
 * Жадное сопоставление по числу серий: идём по сезонам источника и набираем каталожные,
 * пока их сумма не сойдётся ровно.
 *
 * Возвращает null, если сцепить шкалы не удалось: расхождение сумм, нулевые счётчики или
 * остаток по любой из сторон. Недоказанное сопоставление — это не повод угадывать.
 */
internal fun alignSeasonScales(
    catalogue: List<SeasonSpan>,
    source: List<SeasonSpan>,
): SeasonScaleAlignment? {
    if (catalogue.isEmpty() || source.isEmpty()) return null
    if (catalogue.any { it.episodes <= 0 } || source.any { it.episodes <= 0 }) return null

    val orderedCatalogue = catalogue.sortedBy { it.seasonNumber }
    val orderedSource = source.sortedBy { it.seasonNumber }
    val catalogueToSource = HashMap<Int, Int>()
    val oneToOne = HashSet<Int>()

    var index = 0
    for (sourceSeason in orderedSource) {
        if (index >= orderedCatalogue.size) return null
        var sum = 0
        val covered = ArrayList<Int>()
        while (index < orderedCatalogue.size && sum < sourceSeason.episodes) {
            sum += orderedCatalogue[index].episodes
            covered += orderedCatalogue[index].seasonNumber
            index++
        }
        if (sum != sourceSeason.episodes) return null
        covered.forEach { catalogueToSource[it] = sourceSeason.seasonNumber }
        if (covered.size == 1) oneToOne += sourceSeason.seasonNumber
    }
    // Хвост каталога, не покрытый источником, означает, что шкалы не сцепились целиком.
    if (index != orderedCatalogue.size) return null

    return SeasonScaleAlignment(catalogueToSource, oneToOne)
}

/**
 * Номер сезона каталога в шкале источника. Лестница:
 *
 * 1. Сопоставление по числу серий ([alignSeasonScales]).
 * 2. Тождество, когда длины цепочек равны — сезоны идут один к одному по построению.
 * 3. Отказ: недоказуемую трансляцию не подменяем догадкой.
 */
internal fun translateCatalogueSeason(
    catalogue: List<SeasonSpan>,
    source: List<SeasonSpan>,
    catalogueSeason: Int,
): Int? {
    alignSeasonScales(catalogue, source)?.let { return it.catalogueToSource[catalogueSeason] }
    if (catalogue.size == source.size && catalogueSeason in 1..catalogue.size) {
        return catalogueSeason
    }
    return null
}
