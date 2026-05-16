package com.example.myapplication.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.receiver.AnimeUpdateReceiver

/** Id уведомления в шторке (совпадает с `NotificationManager.cancel` из приложения). */
fun animeUpdateNotificationId(animeId: String): Int = animeId.hashCode()

/** Абстракция для тестирования и инверсии зависимостей (SOLID). */
interface AnimeNotifier {
    fun showUpdateNotification(update: AnimeUpdate)
}

class AnimeNotifierImpl(
    private val context: Context
) : AnimeNotifier {

    private val channelId = "anime_updates_channel"
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Anime Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о выходе новых серий"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun showUpdateNotification(update: AnimeUpdate) {
        val notifId = animeUpdateNotificationId(update.animeId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, AnimeUpdateReceiver::class.java).apply {
            action = "ACTION_ACCEPT_UPDATE"
            putExtra("ANIME_ID", update.animeId)
            putExtra("NEW_EPS", update.newEpisodes)
            putExtra("NOTIF_ID", notifId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, notifId, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AnimeUpdateReceiver::class.java).apply {
            action = "ACTION_DISMISS_UPDATE"
            putExtra("ANIME_ID", update.animeId)
            putExtra("NEW_EPS", update.newEpisodes)
            putExtra("NOTIF_ID", notifId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notifId + 10000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Новые серии: ${update.title}")
            .setContentText("Доступно: ${update.currentEpisodes} → ${update.newEpisodes} эп.")
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_add, "Принять", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", dismissPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }
}
