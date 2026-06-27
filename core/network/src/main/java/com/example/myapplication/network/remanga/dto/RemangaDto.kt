package com.example.myapplication.network.remanga.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Remanga API returns avg_rating as either a JSON string ("7.5") or number (0.0). */
@OptIn(ExperimentalSerializationApi::class)
object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeDouble()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonPrimitive -> element.content.toDoubleOrNull()
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

@Serializable
data class RemangaResponse<T>(
    @SerialName("msg")
    val msg: String? = null,
    
    @SerialName("content")
    val content: T? = null
)

@Serializable
data class SearchTitleDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("en_name") val enName: String? = null,
    @SerialName("rus_name") val rusName: String? = null,
    @SerialName("dir") val dir: String? = null,
    @SerialName("issue_year") val issueYear: Int? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("avg_rating") val avgRating: Double? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("img") val img: ImageDto? = null,
    @SerialName("count_chapters") val countChapters: Int? = null
)

@Serializable
data class TitleDetailsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("en_name") val enName: String? = null,
    @SerialName("rus_name") val rusName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("count_chapters") val countChapters: Int? = null,
    @SerialName("dir") val dir: String? = null,
    @SerialName("img") val img: ImageDto? = null,
    @SerialName("genres") val genres: List<CategoryDto>? = null,
    @SerialName("categories") val categories: List<CategoryDto>? = null,
    @SerialName("status") val status: StatusDto? = null,
    @SerialName("branches") val branches: List<BranchDto>? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("avg_rating") val avgRating: Double? = null
)

@Serializable
data class ImageDto(
    @SerialName("high") val high: String? = null,
    @SerialName("mid") val mid: String? = null,
    @SerialName("low") val low: String? = null
)

@Serializable
data class CategoryDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class StatusDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class BranchDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("count_chapters") val countChapters: Int? = null
)

@Serializable
data class ChapterDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("tome") val tome: Int? = null,
    @SerialName("chapter") val chapter: String? = null,
    @SerialName("name") val name: String? = null
)
