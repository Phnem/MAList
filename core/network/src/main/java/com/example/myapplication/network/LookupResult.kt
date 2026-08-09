package com.example.myapplication.network

/**
 * Единый контракт для сетевых lookup'ов MOVIE/SERIES-источников (TMDB, Kinopoisk) —
 * различает "кандидатов нет" от "запрос не удался", которые легко перепутать при эвристике
 * по HTTP-коду/тексту исключения (401/403 — не транзиентная ошибка, но и не "не найдено").
 *
 * Правила простановки/сброса `*_not_found_at` и признаков протухшего id опираются ровно на эту
 * границу:
 *  - [NoMatch] (поиск выполнился, кандидатов нет) — единственный вариант, ведущий к простановке
 *    `*_not_found_at`.
 *  - [NotFoundById] (точечный запрос по уже сохранённому id — источник явно сказал "нет такой
 *    записи", например TMDB 404) — сохранённый id считается протухшим и подлежит очистке с
 *    повторным резолвом по названию.
 *  - [Failure] (сеть/авторизация/рейт-лимит/парсинг) — никогда не меняет `*_not_found_at` и
 *    никогда не очищает сохранённый id: отсутствие ответа не свидетельствует об отсутствии
 *    контента.
 */
sealed interface LookupResult<out T> {
    data class Found<T>(val value: T) : LookupResult<T>
    data object NoMatch : LookupResult<Nothing>
    data object NotFoundById : LookupResult<Nothing>
    data class Failure(val cause: Throwable, val retryable: Boolean) : LookupResult<Nothing>
}
