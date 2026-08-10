package com.example.myapplication.media.download

/**
 * Target quality for an offline copy.
 *
 * Lives in the media layer, not in `ui.details`: `MediaGateway` and `SeasonBatchDownloader` take it
 * as a parameter, so declaring it beside a wizard ViewModel pointed the dependency from the media
 * layer at the UI. The download wizard is one caller of this concept, not its owner.
 */
enum class DownloadQuality(val label: String) {
    P480("480p"),
    P720("720p"),
    P1080("1080p"),
}
