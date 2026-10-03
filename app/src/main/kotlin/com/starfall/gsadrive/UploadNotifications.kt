package com.starfall.gsadrive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** Upload progress lives in the system notification shade instead of the browser UI. */
internal class UploadNotifications(context: Context) {
    enum class Kind(val id: Int, val runningTitle: String) {
        DRIVE(3101, tr("Uploading to Drive")),
        PHOTOS(3102, tr("Uploading to Google Photos"))
    }

    // Each batch owns its notification, including concurrent batches of the same kind.
    private val batchTag = java.util.UUID.randomUUID().toString()
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    tr("Upload"),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = tr("Progress of uploading files to the cloud service")
                    setShowBadge(false)
                }
            )
        }
    }

    fun running(
        kind: Kind,
        currentName: String? = null,
        completed: Int = 0,
        failed: Int = 0,
        total: Int? = null
    ) {
        val countText = buildString {
            append(tr("Done $completed"))
            if (failed > 0) append(tr("· error $failed"))
            if (total != null) append(tr("/ $total files")) else append(tr("files"))
        }
        val text = currentName?.takeIf { it.isNotBlank() }?.let { "$it · $countText" } ?: countText
        val builder = base(kind)
            .setContentTitle(kind.runningTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        if (total != null && total > 0) {
            builder.setProgress(total, (completed + failed).coerceIn(0, total), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        runCatching { manager.notify(batchTag, kind.id, builder.build()) }
    }

    fun finished(kind: Kind, message: String, success: Boolean = true) {
        runCatching { manager.notify(
            batchTag, kind.id,
            base(kind)
                .setContentTitle(if (success) tr("Upload complete") else tr("Upload incomplete"))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
                .build()
        ) }
    }

    fun cancel(kind: Kind) {
        runCatching { manager.cancel(batchTag, kind.id) }
    }

    private fun base(kind: Kind): NotificationCompat.Builder =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private companion object {
        const val CHANNEL_ID = "uploads"
    }
}
