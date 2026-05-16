package com.example.myapplication.network

data class GithubReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val body: String? = null,
    /** Первый asset релиза (APK): URL совпадает с [downloadUrl], размер — для UI. */
    val apkAsset: GithubAsset? = null,
)
