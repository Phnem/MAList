package com.example.myapplication.domain.enrichment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Retains one pause token across Activity configuration recreation and releases it only when the
 * Activity's ViewModelStore is actually cleared (finish/back), including after leaving PiP.
 */
class InteractiveMediaPauseViewModel(
    coordinator: CollectionEnrichmentCoordinator,
) : ViewModel() {
    private val token = coordinator.acquireInteractiveMediaPause()

    override fun onCleared() {
        token.close()
    }

    companion object {
        fun factory(
            coordinator: CollectionEnrichmentCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(InteractiveMediaPauseViewModel::class.java))
                return InteractiveMediaPauseViewModel(coordinator) as T
            }
        }
    }
}
