package com.example.myapplication.sync

/** Внешние сервисы списков (импорт / экспорт / синх). */
enum class ExternalListService {
    SHIKIMORI,
    MYANIMELIST,
    ANILIST
}

enum class ListServiceAction {
    /** Импорт удалённой БД / списка (Pull). */
    PULL,
    /** Экспорт локальных данных (Push). */
    PUSH,
    /** Двусторонняя синхронизация. */
    SYNC
}
