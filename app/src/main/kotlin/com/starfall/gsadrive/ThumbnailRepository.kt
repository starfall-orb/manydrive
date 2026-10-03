package com.starfall.gsadrive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Shared authenticated thumbnail loader for browser cards and MediaSession artwork. */
internal object ThumbnailRepository {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val locks = ConcurrentHashMap<String, Any>()

    fun cached(url: String): Bitmap? = synchronized(cache) { cache.get(url) }

    @Throws(IOException::class)
    fun load(url: String, accessToken: String? = null): Bitmap {
        cached(url)?.let { return it }
        val lock = locks.getOrPut(url) { Any() }
        synchronized(lock) {
            cached(url)?.let { return it }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Thumbnail HTTP $status")
                val bitmap = connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input) ?: throw IOException(tr("Unable to decode thumbnails"))
                }
                synchronized(cache) { cache.put(url, bitmap) }
                return bitmap
            } finally {
                connection.disconnect()
                locks.remove(url, lock)
            }
        }
    }
}
