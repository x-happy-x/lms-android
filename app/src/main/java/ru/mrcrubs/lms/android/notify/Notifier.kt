package ru.mrcrubs.lms.android.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.mrcrubs.lms.android.LinkReceiverActivity
import ru.mrcrubs.lms.android.MainActivity
import ru.mrcrubs.lms.android.R
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.JobEvent

class Notifier(private val context: Context) {

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_downloads),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_downloads_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // checked in canNotify()
    fun show(event: JobEvent) {
        if (!canNotify()) return
        val job = event.job
        val (title, text) = when (event.kind) {
            JobEvent.Kind.COMPLETED -> "Загрузка завершена" to listOfNotNull(
                job.title,
                job.outputSizeBytes?.let { Format.bytes(it) },
            ).joinToString(" · ")
            JobEvent.Kind.FAILED -> "Ошибка загрузки" to listOfNotNull(
                job.title,
                job.errorText ?: job.message,
            ).joinToString(": ")
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(job.id.hashCode(), notification)
    }

    /** A link could not be added right away: tapping opens the add screen with it. */
    @SuppressLint("MissingPermission") // checked in canNotify()
    fun showAddFailed(link: String, reason: String) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            link.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(LinkReceiverActivity.ACTION_ADD_LINK)
                .putExtra(LinkReceiverActivity.EXTRA_LINK, link)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "$reason. Нажмите, чтобы выбрать ноду вручную."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Не удалось добавить ссылку")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$link\n$text"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(link.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
