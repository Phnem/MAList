package com.example.myapplication.data.ai

/**
 * Определение AI-провайдера по строке ключа, без обращения к сети.
 *
 * Детекция двухуровневая, потому что некоторые префиксы неоднозначны:
 *  - Тир 1 — уникальные префиксы (`sk-ant-`, `sk-or-`, `gsk_`, `AIza`): провайдер известен точно.
 *  - Тир 2 — неоднозначные (`sk-` у OpenAI и DeepSeek): возвращаем нескольких кандидатов,
 *    финальный выбор делает сетевая валидация (см. [AiLlmEndpoint]).
 *  - Фоллбэк — без узнаваемого префикса (Cohere и подобные): провайдеры без префиксных
 *    требований, иначе все.
 *
 * Порядок проверки — от самого длинного/специфичного префикса к короткому, поэтому
 * `sk-ant-...` и `sk-or-...` матчатся раньше, чем общий `sk-`.
 */
object AiKeyDetector {

    private data class PrefixRule(val prefix: String, val providers: List<AiProvider>)

    /** Правила, отсортированные по убыванию длины префикса — сначала специфичные. */
    private val rules: List<PrefixRule> = buildList {
        val byPrefix = LinkedHashMap<String, MutableList<AiProvider>>()
        for (provider in AiProvider.entries) {
            for (prefix in provider.validKeyPrefixes) {
                byPrefix.getOrPut(prefix) { mutableListOf() }.add(provider)
            }
        }
        byPrefix.forEach { (prefix, providers) -> add(PrefixRule(prefix, providers)) }
    }.sortedByDescending { it.prefix.length }

    /** Провайдеры без префиксных требований (детектятся только валидацией). */
    private val prefixlessProviders: List<AiProvider> =
        AiProvider.entries.filter { it.validKeyPrefixes.isEmpty() }

    /**
     * Кандидаты-провайдеры для [rawKey]. Пустой список — ключ пустой/явно битый.
     * Один элемент — провайдер определён однозначно. Несколько — решает валидация.
     */
    fun detectCandidates(rawKey: String): List<AiProvider> {
        val key = rawKey.trim()
        if (key.isEmpty()) return emptyList()

        val matched = rules.firstOrNull { key.startsWith(it.prefix) }
        if (matched != null) return matched.providers

        return prefixlessProviders.ifEmpty { AiProvider.entries.toList() }
    }

    /**
     * Единственный однозначно определённый провайдер, либо null (неоднозначно/не найдено).
     */
    fun detectSingle(rawKey: String): AiProvider? =
        detectCandidates(rawKey).singleOrNull()
}
