package com.example.myapplication.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.notifications.AnimeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Действия «Принять/Отклонить» из системного уведомления. Пишет напрямую в БД:
 * updates-flow наблюдается UI, так что состояние в приложении подхватится само.
 */
class AnimeUpdateReceiver : BroadcastReceiver(), KoinComponent {

    private val localDataSource: AnimeLocalDataSource by inject()
    private val notifier: AnimeNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val animeId = intent.getStringExtra(EXTRA_ANIME_ID) ?: return
        val newEps = intent.getIntExtra(EXTRA_NEW_EPS, -1)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val language = runCatching {
            AppLanguage.valueOf(intent.getStringExtra(EXTRA_LANG) ?: "EN")
        }.getOrDefault(AppLanguage.EN)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Говорим системе: "Не убивай процесс, я еще работаю!"
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_ACCEPT -> {
                        val anime = localDataSource.getAnimeById(animeId)
                        if (anime != null && newEps != -1) {
                            localDataSource.updateAnime(anime.copy(episodes = newEps))
                            localDataSource.removeUpdate(animeId)
                        }
                    }
                    ACTION_DISMISS -> {
                        if (newEps != -1) {
                            localDataSource.addIgnored(animeId, newEps)
                            localDataSource.removeUpdate(animeId)
                        }
                    }
                }
                notificationManager.cancel(notifId)
                notifier.refreshGroupSummary(localDataSource.getUpdates().size, language)
            } catch (e: Exception) {
                android.util.Log.e("AnimeUpdateReceiver", "Error processing action: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "ACTION_ACCEPT_UPDATE"
        const val ACTION_DISMISS = "ACTION_DISMISS_UPDATE"
        const val EXTRA_ANIME_ID = "ANIME_ID"
        const val EXTRA_NEW_EPS = "NEW_EPS"
        const val EXTRA_NOTIF_ID = "NOTIF_ID"
        const val EXTRA_LANG = "LANG"
    }
}
