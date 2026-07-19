package com.example.myapplication.domain.inspect

enum class InspectAiRequirement {
    /** RU: trace.moe → AI (романизация→русское название) → Shikimori */
    RU_ANIME_PATH,
    /** Распознавание фильма/сериала по кадру (нужен vision-capable провайдер) */
    MOVIES_TV
}

/**
 * Нужен подключённый AI-провайдер (AI Connect). Раньше это был отдельный Gemini-ключ Visual Search,
 * теперь Inspect использует центральные ключи из [com.example.myapplication.data.ai.AiCredentialsStore].
 */
class InspectAiKeyRequiredException(
    val requirement: InspectAiRequirement = InspectAiRequirement.MOVIES_TV,
) : Exception()
