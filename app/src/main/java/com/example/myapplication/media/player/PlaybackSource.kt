package com.example.myapplication.media.player

import android.net.Uri
import com.example.myapplication.media.source.VetroVideo

sealed interface PlaybackSource {
    data class Local(val uri: Uri) : PlaybackSource
    data class Remote(val video: VetroVideo) : PlaybackSource
}
