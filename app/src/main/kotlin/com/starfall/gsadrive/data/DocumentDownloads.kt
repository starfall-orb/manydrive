package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DownloadSource(val url: String, val headers: Map<String, String> = emptyMap())

/** Only discovers files and enqueues requests. Android owns all network transfers and progress. */
internal class DocumentDownloads(
    context: Context,
    private val list: suspend (DriveFile) -> List<DriveFile>,
    private val source: suspend (DriveFile) -> DownloadSource
) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    suspend fun download(file: DriveFile): Int {
        val ancestors = mutableSetOf<String>()
        var queued = 0
        suspend fun walk(item: DriveFile, path: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            check(depth < 128) { tr("The directory is too deep.") }
            if (item.isFolder) {
                check(ancestors.add(item.id)) { tr("Folder structure is duplicated.") }
                val names = mutableSetOf<String>()
                list(item).forEach { child ->
                    walk(child, "$path/${uniqueName(child.name, names)}", depth + 1)
                }
                ancestors.remove(item.id)
            } else {
                val remote = source(item)
                currentCoroutineContext().ensureActive()
                val request = DownloadManager.Request(Uri.parse(remote.url))
                    .setTitle(item.name)
                    .setMimeType(item.mimeType.ifBlank { "application/octet-stream" })
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path)
                remote.headers.forEach { (name, value) -> request.addRequestHeader(name, value) }
                manager.enqueue(request)
                queued++
            }
        }
        // A distinct destination also avoids collisions with requests still waiting in Android's queue.
        val batch = java.util.UUID.randomUUID().toString()
        walk(file, "$batch-${safeName(file.name)}", 0)
        return queued
    }

    companion object {
        internal fun safeName(name: String): String = name.replace(Regex("[\\\\/\\p{Cntrl}]"), "_")
            .trim().take(180).takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "download"

        internal fun uniqueName(name: String, used: MutableSet<String>): String {
            val safe = safeName(name)
            var candidate = safe
            var index = 1
            while (!used.add(candidate)) candidate = "${index++}-$safe"
            return candidate
        }
    }
}
