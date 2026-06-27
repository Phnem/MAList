package com.example.myapplication.data.repository

/**
 * Репозиторий хранения изображений. Изолирует работу с Context и файловой системой в Data-слое.
 */
interface ImageStorageRepository {
    /**
     * Сохраняет изображение по URI в коллекцию (app storage/Vetro/collection).
     * @return Result с именем файла при успехе или ошибкой при неудаче.
     */
    suspend fun saveImage(uri: String, animeId: String): Result<String>

    /**
     * Возвращает абсолютный путь к файлу изображения в коллекции, или null если файл не существует.
     */
    fun getImageFilePath(fileName: String): String?

    /** True if the image exists locally (including compressed-name variants). */
    fun hasLocalImage(fileName: String): Boolean

    /**
     * Удаляет файл изображения из коллекции. Возвращает true при успехе.
     */
    fun deleteImage(fileName: String): Boolean

    /**
     * Скачивает изображение по HTTP URL и сохраняет в коллекцию.
     * @return Result с именем файла при успехе или ошибкой при неудаче.
     */
    suspend fun saveImageFromUrl(url: String, animeId: String): Result<String>
}
