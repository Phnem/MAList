package com.example.myapplication.media.ui

/**
 * The local player indicator bridges both halves of a stream transition:
 * resolving the episode URL and buffering that URL in the player.
 */
internal fun shouldShowStreamLoading(
    requestPending: Boolean,
    playerBuffering: Boolean,
): Boolean = requestPending || playerBuffering
