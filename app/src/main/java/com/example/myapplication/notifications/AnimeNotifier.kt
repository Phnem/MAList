package com.example.myapplication.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.phnem.vetro.R
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.receiver.AnimeUpdateReceiver
import com.example.myapplication.utils.getNotificationStrings
import java.util.Locale

/** Id уведомления в шторке (совпадает с `NotificationManager.cancel` из приложения). */
fun animeUpdateNotificationId(animeId: String): Int = animeId.hashCode()

const val ANIME_UPDATES_GROUP = "anime_updates_group"
const val ANIME_UPDATES_SUMMARY_ID = 0x5E71E5

/** Абстракция для тестирования и инверсии зависимостей (SOLID). */
interface AnimeNotifier {
    fun showUpdateNotification(update: AnimeUpdate, language: AppLanguage)

    /** Пересобрать/убрать сводку группы после действия пользователя. */
    fun refreshGroupSummary(remainingUpdates: Int, language: AppLanguage)

    /**
     * Убрать из системной шторки ВСЕ пуши обновлений серий (+ групповую сводку).
     * Вызывается, когда приложение на переднем плане: тогда обновления показываются
     * in-app стопкой, а системные уведомления не нужны.
     */
    fun cancelAllUpdateNotifications(animeIds: List<String>)
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
            val strings = getNotificationStrings(AppLanguage.EN)
            val channel = NotificationChannel(
                channelId,
                strings.notifChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = strings.notifChannelDesc
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun showUpdateNotification(update: AnimeUpdate, language: AppLanguage) {
        val strings = getNotificationStrings(language)
        val notifId = animeUpdateNotificationId(update.animeId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, AnimeUpdateReceiver::class.java).apply {
            action = AnimeUpdateReceiver.ACTION_ACCEPT
            putExtra(AnimeUpdateReceiver.EXTRA_ANIME_ID, update.animeId)
            putExtra(AnimeUpdateReceiver.EXTRA_NEW_EPS, update.newEpisodes)
            putExtra(AnimeUpdateReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(AnimeUpdateReceiver.EXTRA_LANG, language.name)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, notifId, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AnimeUpdateReceiver::class.java).apply {
            action = AnimeUpdateReceiver.ACTION_DISMISS
            putExtra(AnimeUpdateReceiver.EXTRA_ANIME_ID, update.animeId)
            putExtra(AnimeUpdateReceiver.EXTRA_NEW_EPS, update.newEpisodes)
            putExtra(AnimeUpdateReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(AnimeUpdateReceiver.EXTRA_LANG, language.name)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notifId + 10000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = String.format(Locale.getDefault(), strings.notifUpdateTitleFormat, update.title)
        val body = String.format(
            Locale.getDefault(),
            strings.notifUpdateBodyFormat,
            update.currentEpisodes,
            update.newEpisodes
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .addAction(0, strings.notifAccept, acceptPendingIntent)
            .addAction(0, strings.notifDecline, dismissPendingIntent)
            .setGroup(ANIME_UPDATES_GROUP)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }

    override fun refreshGroupSummary(remainingUpdates: Int, language: AppLanguage) {
        if (remainingUpdates <= 1) {
            manager.cancel(ANIME_UPDATES_SUMMARY_ID)
            return
        }
        val strings = getNotificationStrings(language)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(strings.notifChannelName)
            .setContentText(
                String.format(Locale.getDefault(), strings.notifGroupSummaryFormat, remainingUpdates)
            )
            .setContentIntent(pendingIntent)
            .setGroup(ANIME_UPDATES_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(ANIME_UPDATES_SUMMARY_ID, summary)
    }

    override fun cancelAllUpdateNotifications(animeIds: List<String>) {
        animeIds.forEach { manager.cancel(animeUpdateNotificationId(it)) }
        manager.cancel(ANIME_UPDATES_SUMMARY_ID)
    }
}
