package com.example.myapplication.media.source

import com.example.myapplication.domain.seasons.SeasonInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniLibriaSeasonSelectionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `food wars third tv release is selected without counting specials`() {
        val selected = selectAniLibriaSeasonRelease(
            franchises = fixture(),
            localTitles = listOf("Повар-боец Сома", "Food Wars!"),
            seasonInfo = SeasonInfo(
                seasonNumber = 3,
                episodes = 24,
                totalEpisodes = 24,
                source = "Kodik",
            ),
            episodeNumber = 6,
        )

        assertEquals("5150", selected?.id)
        assertEquals("shokugeki-no-souma-san-no-sara", selected?.alias)
    }

    @Test
    fun `episode count mismatch fails closed instead of selecting another season`() {
        val selected = selectAniLibriaSeasonRelease(
            franchises = fixture(),
            localTitles = listOf("Повар-боец Сома"),
            seasonInfo = SeasonInfo(
                seasonNumber = 3,
                episodes = 12,
                totalEpisodes = 12,
                source = "Kodik",
            ),
            episodeNumber = 6,
        )

        assertNull(selected)
    }

    @Test
    fun `missing episode count or unrelated release title fails closed`() {
        val missingCount = fixtureJson.replace(
            Regex(""""episodes_total":24,\s*"is_blocked_by_geo""""),
            "\"is_blocked_by_geo\"",
        )
        val unrelatedTitle = fixtureJson.replace(
            "\"Повар-Боец Сома 3\"",
            "\"Совсем другой сериал\"",
        )
        val season = SeasonInfo(3, 24, 24, source = "Kodik")

        assertNull(
            selectAniLibriaSeasonRelease(
                franchises = parseFixture(missingCount),
                localTitles = listOf("Повар-боец Сома"),
                seasonInfo = season,
                episodeNumber = 6,
            ),
        )
        check(missingCount != fixtureJson) { "Fixture mutation must remove episodes_total" }
        assertNull(
            selectAniLibriaSeasonRelease(
                franchises = parseFixture(unrelatedTitle),
                localTitles = listOf("Повар-боец Сома"),
                seasonInfo = season,
                episodeNumber = 6,
            ),
        )
    }

    @Test
    fun `ambiguous matching franchises fail closed`() {
        val duplicate = fixture().first()
        val selected = selectAniLibriaSeasonRelease(
            franchises = fixture() + duplicate,
            localTitles = listOf("Повар-боец Сома"),
            seasonInfo = SeasonInfo(3, 24, 24, source = "Kodik"),
            episodeNumber = 6,
        )

        // Duplicate responses for the same franchise/release are harmless.
        assertEquals("5150", selected?.id)

        val other = json.parseToJsonElement(
            fixtureJson.replace(
                "\"id\":\"food-wars\"",
                "\"id\":\"food-wars-copy\"",
            ).replace(
                "\"release_id\":5150",
                "\"release_id\":95150",
            ).replace(
                "\"id\":5150",
                "\"id\":95150",
            ),
        ).jsonArray.first().jsonObject

        assertNull(
            selectAniLibriaSeasonRelease(
                franchises = fixture() + other,
                localTitles = listOf("Повар-боец Сома"),
                seasonInfo = SeasonInfo(3, 24, 24, source = "Kodik"),
                episodeNumber = 6,
            ),
        )
    }

    @Test
    fun `subset title from another season is not accepted as exact season`() {
        val season = SeasonInfo(
            seasonNumber = 3,
            episodes = 24,
            totalEpisodes = 24,
            title = "Shokugeki no Souma San no Sara",
            source = "Kodik",
        )

        assertEquals(
            false,
            releaseIdentifiesSelectedSeason(
                releaseTitles = listOf("Shokugeki no Souma"),
                alias = "shokugeki-no-souma",
                seasonInfo = season,
            ),
        )
        assertEquals(
            true,
            releaseIdentifiesSelectedSeason(
                releaseTitles = listOf("Shokugeki no Souma San no Sara"),
                alias = "shokugeki-no-souma-san-no-sara",
                seasonInfo = season,
            ),
        )
    }

    /**
     * Регрессия на корень отказа AniLibria: сезонное название приходит по-английски, релизы
     * названы по-русски, поэтому первая ступень (точное равенство) не срабатывает никогда —
     * и раньше обрывала проверку, не пуская к порядковому маркеру.
     */
    @Test
    fun `russian release with an ordinal marker satisfies an english season title`() {
        val season = SeasonInfo(
            seasonNumber = 2,
            episodes = 13,
            totalEpisodes = 13,
            title = "Shokugeki no Souma: Ni no Sara",
            source = "AniList",
        )

        assertEquals(
            true,
            releaseIdentifiesSelectedSeason(
                releaseTitles = listOf("Повар-Боец Сома 2"),
                alias = "shokugeki-no-souma-ni-no-sara",
                seasonInfo = season,
            ),
        )
    }

    @Test
    fun `ordinal marker of a neighbouring season is not accepted`() {
        val season = SeasonInfo(
            seasonNumber = 2,
            episodes = 13,
            title = "Shokugeki no Souma: Ni no Sara",
            source = "AniList",
        )

        assertEquals(
            false,
            releaseIdentifiesSelectedSeason(
                releaseTitles = listOf("Повар-Боец Сома 3"),
                alias = "shokugeki-no-souma-san-no-sara",
                seasonInfo = season,
            ),
        )
        assertEquals(
            false,
            releaseIdentifiesSelectedSeason(
                releaseTitles = listOf("Повар-Боец Сома"),
                alias = "shokugeki-no-souma",
                seasonInfo = season,
            ),
        )
    }

    private fun fixture() = parseFixture(fixtureJson)

    private fun parseFixture(value: String) =
        json.parseToJsonElement(value).jsonArray.map { it.jsonObject }

    private companion object {
        val fixtureJson = """
            [{
              "id":"food-wars",
              "name":"Повар-боец Сома",
              "name_english":"Shokugeki no Souma",
              "franchise_releases":[
                {"sort_order":1,"release_id":421,"release":{
                  "id":421,"alias":"shokugeki-no-souma","name":{"main":"Повар-боец Сома"},
                  "type":{"value":"TV"},"episodes_total":24
                }},
                {"sort_order":2,"release_id":999,"release":{
                  "id":999,"alias":"shokugeki-special","name":{"main":"Повар-боец Сома: Спешл"},
                  "type":{"value":"SPECIAL"},"episodes_total":1
                }},
                {"sort_order":3,"release_id":2628,"release":{
                  "id":2628,"alias":"shokugeki-no-souma-ni-no-sara","name":{"main":"Повар-Боец Сома 2"},
                  "type":{"value":"TV"},"episodes_total":13
                }},
                {"sort_order":4,"release_id":5150,"release":{
                  "id":5150,"alias":"shokugeki-no-souma-san-no-sara","name":{"main":"Повар-Боец Сома 3"},
                  "type":{"value":"TV"},"episodes_total":24,
                  "is_blocked_by_geo":false,"is_blocked_by_copyrights":false
                }}
              ]
            }]
        """.trimIndent()
    }
}
