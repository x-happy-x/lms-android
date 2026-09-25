package ru.mrcrubs.lms.android.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.RouterApi
import ru.mrcrubs.lms.core.downloadFileName

/**
 * Saves a finished job's file on the phone. The file is streamed from the node through
 * the router; Android's DownloadManager shows progress, resumes after drops and notifies.
 */
class DeviceDownloads(private val context: Context) {
    fun enqueue(job: Job, api: RouterApi): Long {
        val request = DownloadManager.Request(Uri.parse(api.fileUrl(job.id)))
            .setTitle(job.downloadFileName)
            .setDescription("LMS")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Shared Downloads/LMS needs no storage permission on Android 10+.
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LMS/${job.downloadFileName}")
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, job.downloadFileName)
        }
        api.authHeaders().forEach { (name, value) -> request.addRequestHeader(name, value) }
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    val locationHint: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Загрузки/LMS" else "папку приложения"
}
