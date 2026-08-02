package com.example.myapplication.domain.recommendations

/**
 * Максимальное сходство Жаккара между дескрипторами кандидата и любой из обложек коллекции —
 * прокси для BALSE-style визуального сходства без настоящего вектора-эмбеддинга (у AiProvider
 * нет embeddings-эндпоинта, см. CoverDescriptorProvider). "Максимум" — кандидат достаточно
 * похож хотя бы на одну обложку из коллекции, не обязан быть похож на все сразу.
 */
fun visualSimilarityScore(candidateTags: Set<String>, referenceTagSets: List<Set<String>>): Float {
    if (candidateTags.isEmpty()) return 0f
    return referenceTagSets
        .filter { it.isNotEmpty() }
        .maxOfOrNull { reference ->
            val intersection = candidateTags.intersect(reference).size
            val union = candidateTags.union(reference).size
            intersection.toFloat() / union.toFloat()
        } ?: 0f
}
