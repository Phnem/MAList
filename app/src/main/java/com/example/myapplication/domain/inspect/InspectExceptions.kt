package com.example.myapplication.domain.inspect

enum class InspectGeminiRequirement {
    /** RU: trace.moe → Gemini → Shikimori */
    RU_ANIME_PATH,
    /** Распознавание фильма/сериала по кадру */
    MOVIES_TV
}

/** Нужен пользовательский Gemini API key. */
class InspectGeminiRequiredException(
    val requirement: InspectGeminiRequirement = InspectGeminiRequirement.MOVIES_TV
) : Exception()
