package com.example.myapplication.localplayer.domain

/**
 * Разбор имени видеофайла в номер эпизода — «алгоритм» первого прохода. По духу близок к Anitomy:
 * снимаем расширение и технический «шум» (релиз-группа/качество/кодеки в скобках, хеши), затем ищем
 * номер эпизода по набору приоритетных паттернов. Если уверенно определить не удалось — возвращаем
 * `null` в [ParsedFilename.episodeNumber], и такие файлы уходят на ИИ-фолбэк.
 *
 * Реализация намеренно самодостаточна (без внешней зависимости), чтобы фича оставалась изолированной
 * и не завязывалась на конкретный артефакт Anitomy; интерфейс [parse] позволяет при желании
 * заменить движок.
 */
class EpisodeFilenameParser {

    data class ParsedFilename(
        val episodeNumber: Int?,
        val season: Int?,
        val cleanTitle: String?,
    )

    fun parse(fileName: String): ParsedFilename {
        val noExt = stripExtension(fileName)
        // Убираем содержимое [] и (), но запоминаем «голый» текст без них для титульной эвристики.
        val bracketFree = noExt
            .replace(BRACKET_GROUP, " ")
            .replace(PAREN_GROUP, " ")
            .replace(HASH_HEX, " ")
            .trim()

        val season = SEASON.find(noExt)?.groupValues?.get(1)?.toIntOrNull()
        val episode = extractEpisode(noExt, bracketFree)
        val title = extractTitle(bracketFree, episode)
        return ParsedFilename(episodeNumber = episode, season = season, cleanTitle = title)
    }

    private fun extractEpisode(raw: String, bracketFree: String): Int? {
        // Порядок = приоритет: явные маркеры надёжнее «просто числа».
        // Явные маркеры ищем по «сырой» строке; «- 05» и голое число — по строке без скобок,
        // чтобы не хватать 1080/264/x265 и т.п. из технических тегов.
        val marked = listOf(
            SxxExx to raw,       // S01E05
            EpMarker to raw,     // E05 / EP.05 / Ep 05
            EpisodeWord to raw,  // episode 5 / эпизод 5
            SeriyaWord to raw,   // 5 серия / серия 5
            BracketNumber to raw, // [23] / (05) — скобка только с числом = обычно эпизод
            HashNumber to raw,   // #05
            DashNumber to bracketFree, // " - 05"
        )
        for ((rx, source) in marked) {
            val n = rx.find(source)?.groupValues?.lastOrNull { it.isNotBlank() }?.toIntOrNull()
            if (n != null && n in EPISODE_RANGE) return n
        }
        // Последний шанс: единственное «разумное» голое число (не год/качество/кодек).
        return StandaloneNum.findAll(bracketFree)
            .mapNotNull { it.value.trim().toIntOrNull() }
            .filter { it in EPISODE_RANGE && it !in TECHNICAL_NUMBERS }
            .lastOrNull()
    }

    private fun extractTitle(bracketFree: String, episode: Int?): String? {
        var t = bracketFree
        // Отрезаем всё, начиная с маркера эпизода — обычно название идёт до него.
        val cut = TITLE_CUT.find(t)
        if (cut != null) t = t.substring(0, cut.range.first)
        t = t.replace(SEPARATORS, " ").replace(MULTISPACE, " ").trim(' ', '-', '_', '.')
        return t.takeIf { it.length >= 2 }
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        val ext = name.substring(dot + 1).lowercase()
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) name.substring(0, dot) else name
    }

    private companion object {
        val EPISODE_RANGE = 0..3000
        // Числа, которые почти всегда «технические», а не номер эпизода.
        val TECHNICAL_NUMBERS = setOf(264, 265, 480, 720, 1080, 2160, 60, 10, 8)

        val BRACKET_GROUP = Regex("""\[[^\[\]]*]""")
        val PAREN_GROUP = Regex("""\([^()]*\)""")
        val HASH_HEX = Regex("""\b[0-9a-fA-F]{8}\b""")

        val SEASON = Regex("""(?i)\bS(?:eason)?\s*0*(\d{1,2})""")

        val SxxExx = Regex("""(?i)\bS\d{1,2}\s*E\s*0*(\d{1,4})\b""")
        val EpMarker = Regex("""(?i)\bE(?:P)?\.?\s*0*(\d{1,4})\b""")
        val EpisodeWord = Regex("""(?i)\b(?:episode|эпизод)\s*0*(\d{1,4})\b""")
        val SeriyaWord = Regex("""(?i)\b0*(\d{1,4})\s*(?:серия|серии)\b|(?:серия)\s*0*(\d{1,4})\b""")
        val DashNumber = Regex("""[-–—]\s*0*(\d{1,4})(?:\s|$|[-–—._])""")
        val HashNumber = Regex("""#\s*0*(\d{1,4})\b""")
        // Скобка/скобки, содержащие ТОЛЬКО число (напр. [23], (05)) — обычно это номер эпизода,
        // а не тех.тег вроде [1080p]/[x265], где есть буквы.
        val BracketNumber = Regex("""[\[(]\s*0*(\d{1,4})\s*[\])]""")
        val StandaloneNum = Regex("""\b\d{1,4}\b""")

        // Точка отреза названия — первый «эпизодный» маркер.
        val TITLE_CUT = Regex("""(?i)(\bS\d{1,2}\s*E|\bE(?:P)?\.?\s*\d|\bepisode\b|\bэпизод\b|[-–—]\s*\d|#\d|\bсерия\b)""")
        val SEPARATORS = Regex("""[._]+""")
        val MULTISPACE = Regex("""\s{2,}""")
    }
}
