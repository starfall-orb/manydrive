package com.starfall.gsadrive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Shared two-level thumbnail cache for browser cards and MediaSession artwork. */
internal object ThumbnailRepository {
    private const val MEMORY_CACHE_KB = 24 * 1024
    private const val DISK_CACHE_BYTES = 256L * 1024L * 1024L

    private val memory = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val locks = ConcurrentHashMap<String, Any>()

    fun cached(cacheKey: String): Bitmap? = synchronized(memory) { memory.get(cacheKey) }

    @Throws(IOException::class)
    fun load(
        context: Context,
        url: String,
        accessToken: String? = null,
        cacheKey: String = url
    ): Bitmap {
        cached(cacheKey)?.let { return it }
        val lock = locks.getOrPut(cacheKey) { Any() }
        synchronized(lock) {
            cached(cacheKey)?.let { return it }

            val target = diskFile(context.applicationContext, cacheKey)
            decodeDisk(target)?.let { bitmap ->
                synchronized(memory) { memory.put(cacheKey, bitmap) }
                target.setLastModified(System.currentTimeMillis())
                return bitmap
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Thumbnail HTTP $status")
                val bytes = connection.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IOException(tr("Unable to decode thumbnails"))
                writeDisk(target, bytes)
                synchronized(memory) { memory.put(cacheKey, bitmap) }
                trimDiskCache(target.parentFile)
                return bitmap
            } finally {
                connection.disconnect()
                locks.remove(cacheKey, lock)
            }
        }
    }


    fun clear(context: Context) {
        synchronized(memory) { memory.evictAll() }
        runCatching { diskDirectory(context.applicationContext).deleteRecursively() }
    }

    private fun diskDirectory(context: Context): File =
        File(context.filesDir, "thumbnail-cache").apply { mkdirs() }

    private fun diskFile(context: Context, cacheKey: String): File =
        File(diskDirectory(context), sha256(cacheKey) + ".img")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun decodeDisk(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) file.delete()
        return bitmap
    }

    private fun writeDisk(target: File, bytes: ByteArray) {
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.path + ".tmp")
            temporary.outputStream().use { it.write(bytes) }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target.setLastModified(System.currentTimeMillis())
        }
    }

    private fun trimDiskCache(directory: File?) {
        if (directory == null || !directory.isDirectory) return
        runCatching {
            var total = 0L
            directory.listFiles().orEmpty()
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .sortedByDescending { it.lastModified() }
                .forEach { file ->
                    total += file.length()
                    if (total > DISK_CACHE_BYTES) file.delete()
                }
        }
    }
}
