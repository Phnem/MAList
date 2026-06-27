package com.example.myapplication

import com.example.myapplication.network.remanga.dto.RemangaResponse
import com.example.myapplication.network.remanga.dto.TitleDetailsDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RemangaTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun avgRating_deserializesFromString() {
        val payload = """{"msg":"","content":{"id":1,"rus_name":"Test","avg_rating":"7.5"}}"""
        val response = json.decodeFromString<RemangaResponse<TitleDetailsDto>>(payload)
        assertEquals(7.5, response.content?.avgRating)
    }

    @Test
    fun avgRating_deserializesFromNumber() {
        val payload = """{"msg":"","content":{"id":1,"rus_name":"Test","avg_rating":0.0}}"""
        val response = json.decodeFromString<RemangaResponse<TitleDetailsDto>>(payload)
        assertEquals(0.0, response.content?.avgRating)
    }

    @Test
    fun testRemangaApi() = runBlocking {
        val client = HttpClient(OkHttp) {
            install(UserAgent) {
                agent = "VetroApp/1.0 (https://github.com/2004i/Vetro)"
            }
        }

        val searchRes = client.get("https://api.remanga.org/api/search/?query=solo%20leveling&count=1").bodyAsText()
        println("--- SEARCH RESPONSE ---")
        println(searchRes)

        val detailsRes = client.get("https://api.remanga.org/api/titles/solo-leveling/").bodyAsText()
        println("--- DETAILS RESPONSE ---")
        println(detailsRes.take(2000))
    }
}
