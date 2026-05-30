package com.example.myapplication.data.local

import android.content.Context
import android.os.Environment
import java.io.File

class VetroStoragePaths(context: Context) {
    val vetroRoot: File =
        context.getExternalFilesDir("Vetro")!!.also { it.mkdirs() }
    val collectionDir: File =
        File(vetroRoot, COLLECTION_DIR).also { it.mkdirs() }

    fun legacyCollectionDir(): File = File(legacyPublicRoot(), COLLECTION_DIR)

    fun legacyPublicRoot(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            ROOT,
        )

    fun legacyMyAnimeListRoot(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            OLD_ROOT_FOLDER,
        )

    private companion object {
        private const val ROOT = "Vetro"
        private const val COLLECTION_DIR = "collection"
        private const val OLD_ROOT_FOLDER = "MyAnimeList"
    }
}
