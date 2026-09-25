package ru.mrcrubs.lms.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.core.LinkExtractor

/**
 * Invisible entry point for links from other apps: "Open with" (http/https, magnet, .torrent,
 * video/audio), "Share" and selected text ("Скачать через LMS" in the text menu).
 *
 * With "add without confirmation" on, the link goes straight to the router and a toast reports
 * the result; otherwise (or when that fails) the app opens on the add screen with the link.
 */
class LinkReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = linkFrom(intent)
        if (link == null) {
            Toast.makeText(this, "Ссылка не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val app = application as LmsApp
        val container = app.container
        container.scope.launch {
            val settings = container.settings.current()
            withContext(Dispatchers.Main) {
                if (!settings.quickAdd || !settings.isConfigured) {
                    // Started while this activity is in front, so Android lets the app open.
                    openApp(this@LinkReceiverActivity, link)
                } else {
                    toast(app, "Отправляю в LMS…")
                    // The app scope outlives this activity, which finishes right away.
                    container.scope.launch { addQuickly(app, link, settings.defaultSpeedLimit) }
                }
                finish()
            }
        }
    }

    private suspend fun addQuickly(app: LmsApp, link: String, maxSpeedBytes: Long?) {
        try {
            val (job, plan) = app.container.router.addQuickly(link, maxSpeedBytes)
            withContext(Dispatchers.Main) { toast(app, "Добавлено: ${job.title} → ${plan.nodeName}") }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            val reason = ex.message ?: "ошибка"
            // The activity is gone, and a background app may not open screens: offer a notification.
            app.container.notifier.showAddFailed(link, reason)
            withContext(Dispatchers.Main) { toast(app, "Не удалось добавить: $reason") }
        }
    }

    companion object {
        const val ACTION_ADD_LINK = "ru.mrcrubs.lms.action.ADD_LINK"
        const val EXTRA_LINK = "link"

        fun linkFrom(intent: Intent?): String? = when (intent?.action) {
            Intent.ACTION_SEND -> LinkExtractor.extract(
                listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                ).joinToString(" "),
            )
            Intent.ACTION_PROCESS_TEXT -> LinkExtractor.extract(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            Intent.ACTION_VIEW -> intent.dataString?.trim()?.takeIf { LinkExtractor.isSupportedUrl(it) }
            else -> null
        }

        private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

        /** Opens the app's add screen with [link]. */
        fun openApp(context: Context, link: String) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_ADD_LINK)
                    .putExtra(EXTRA_LINK, link)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
