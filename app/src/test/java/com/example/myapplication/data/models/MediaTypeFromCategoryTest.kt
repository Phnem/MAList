package com.example.myapplication.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тип записи выводится из раздела поиска, а не из ответа источника: Shikimori проставляет
 * `"ANIME"` и результатам `/api/mangas`, из-за чего манга попадала в коллекцию как аниме и
 * открывала меню серий вместо глав.
 */
class MediaTypeFromCategoryTest {

    @Test
    fun manga_section_gives_manga() {
        assertEquals(MediaType.MANGA, MediaType.fromCategoryType("MANGA"))
        assertEquals(MediaType.MANGA, MediaType.fromCategoryType("manga"))
        assertEquals(MediaType.MANGA, MediaType.fromCategoryType(" Manga "))
    }

    @Test
    fun anime_section_gives_anime() {
        assertEquals(MediaType.ANIME, MediaType.fromCategoryType("ANIME"))
    }

    /** «Фильмы» и «Сериалы» — один тип записи: у MediaType отдельного MOVIE нет. */
    @Test
    fun movies_and_series_share_tv_series() {
        assertEquals(MediaType.TV_SERIES, MediaType.fromCategoryType("SERIES"))
        assertEquals(MediaType.TV_SERIES, MediaType.fromCategoryType("TV_SERIES"))
        assertEquals(MediaType.TV_SERIES, MediaType.fromCategoryType("MOVIE"))
    }

    /** null = строка про тип ничего не говорит; решение (и дефолт ANIME) остаётся за вызывающим. */
    @Test
    fun unknown_or_blank_decides_nothing() {
        assertNull(MediaType.fromCategoryType(""))
        assertNull(MediaType.fromCategoryType("   "))
        assertNull(MediaType.fromCategoryType(null))
        assertNull(MediaType.fromCategoryType("Комедия"))
    }
}
