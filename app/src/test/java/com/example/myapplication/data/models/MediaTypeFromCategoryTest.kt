package com.example.myapplication.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тип записи выводится из раздела поиска, а не из ответа источника: Shikimori проставляет
 * `"ANIME"` и результатам `/api/mangas`, из-за чего манга попадала в коллекцию как аниме и
 * открывала меню серий вместо глав.
 *
 * `MOVIE`/`SERIES` разделены (было — единый `TV_SERIES`); `fromCategoryType` понимает legacy
 * `"TV_SERIES"` как алиас `SERIES`, чтобы старые API-результаты/сохранённые категории не терялись.
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

    @Test
    fun movie_section_gives_movie() {
        assertEquals(MediaType.MOVIE, MediaType.fromCategoryType("MOVIE"))
        assertEquals(MediaType.MOVIE, MediaType.fromCategoryType("movie"))
    }

    @Test
    fun series_section_gives_series() {
        assertEquals(MediaType.SERIES, MediaType.fromCategoryType("SERIES"))
        assertEquals(MediaType.SERIES, MediaType.fromCategoryType("TV"))
    }

    /** Legacy: до разделения MOVIE/SERIES оба типа схлопывались в TV_SERIES. */
    @Test
    fun legacy_tv_series_string_maps_to_series() {
        assertEquals(MediaType.SERIES, MediaType.fromCategoryType("TV_SERIES"))
    }

    /** null = строка про тип ничего не говорит; решение (и дефолт ANIME) остаётся за вызывающим. */
    @Test
    fun unknown_or_blank_decides_nothing() {
        assertNull(MediaType.fromCategoryType(""))
        assertNull(MediaType.fromCategoryType("   "))
        assertNull(MediaType.fromCategoryType(null))
        assertNull(MediaType.fromCategoryType("Комедия"))
    }

    /**
     * `fromPersistedValue` — граница чтения уже сохранённого значения (SQLDelight-маппер,
     * sync). В отличие от `fromCategoryType`, никогда не возвращает null: любая строка
     * резолвится в конкретный тип, неизвестная — в ANIME (тот же дефолт, что раньше давал
     * `runCatching { valueOf }.getOrDefault(ANIME)`).
     */
    @Test
    fun fromPersistedValue_resolves_current_enum_names() {
        assertEquals(MediaType.ANIME, MediaType.fromPersistedValue("ANIME"))
        assertEquals(MediaType.MANGA, MediaType.fromPersistedValue("MANGA"))
        assertEquals(MediaType.MOVIE, MediaType.fromPersistedValue("MOVIE"))
        assertEquals(MediaType.SERIES, MediaType.fromPersistedValue("SERIES"))
    }

    /** Старые БД/старые клиенты синка ещё какое-то время пишут "TV_SERIES" — не должно падать. */
    @Test
    fun fromPersistedValue_maps_legacy_tv_series_to_series() {
        assertEquals(MediaType.SERIES, MediaType.fromPersistedValue("TV_SERIES"))
    }

    @Test
    fun fromPersistedValue_falls_back_to_anime_for_garbage() {
        assertEquals(MediaType.ANIME, MediaType.fromPersistedValue(""))
        assertEquals(MediaType.ANIME, MediaType.fromPersistedValue("garbage"))
    }
}
