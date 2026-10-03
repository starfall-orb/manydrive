package com.starfall.gsadrive

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

internal const val VIDEO_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
internal const val VIDEO_CACHE_MAX_BYTES = 2L * 1024L * 1024L * 1024L

internal fun isFreshVideoCacheFile(file: File, now: Long = System.currentTimeMillis()): Boolean =
    file.isFile && file.length() > 0L && now - file.lastModified() <= VIDEO_CACHE_TTL_MS

internal fun touchVideoCacheFile(file: File) {
    if (file.isFile) file.setLastModified(System.currentTimeMillis())
}


internal fun pruneVideoFileCache(directory: File) {
    val cutoff = System.currentTimeMillis() - VIDEO_CACHE_TTL_MS
    var total = 0L
    directory.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".tmp") }
        .onEach { if (it.lastModified() < cutoff) it.delete() }
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
        .forEach { file ->
            total += file.length()
            if (total > VIDEO_CACHE_MAX_BYTES) file.delete()
        }
}

/** Persistent byte-range cache for streamed Google Photos/S3 videos. */
internal object VideoStreamCache {
    private var cache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var lastPruneAt = 0L

    @Synchronized
    private fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val appContext = context.applicationContext
        val provider = StandaloneDatabaseProvider(appContext)
        val created = SimpleCache(
            File(appContext.filesDir, "video-stream-cache").apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_MAX_BYTES),
            provider
        )
        databaseProvider = provider
        cache = created
        pruneExpired(created, force = true)
        return created
    }

    @Synchronized
    private fun pruneExpired(cache: SimpleCache, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPruneAt < 60L * 60L * 1000L) return
        lastPruneAt = now
        val cutoff = now - VIDEO_CACHE_TTL_MS
        cache.keys.toList().forEach { key ->
            cache.getCachedSpans(key).toList().forEach { span ->
                if (span.lastTouchTimestamp > 0L && span.lastTouchTimestamp < cutoff) {
                    runCatching { cache.removeSpan(span) }
                }
            }
        }
    }

    fun dataSourceFactory(context: Context, upstreamFactory: DataSource.Factory): DataSource.Factory {
        val simpleCache = get(context)
        synchronized(this) { pruneExpired(simpleCache) }
        val cachedFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory { dataSpec ->
                val mediaId = dataSpec.uri.getQueryParameter("id")
                if (mediaId.isNullOrBlank()) dataSpec.key ?: dataSpec.uri.toString() else "video:$mediaId"
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return RoutingVideoDataSourceFactory(upstreamFactory, cachedFactory)
    }

    @Synchronized
    fun clear(context: Context) {
        cache?.keys?.toList()?.forEach { key -> runCatching { cache?.removeResource(key) } }
        if (cache == null) runCatching { File(context.applicationContext.filesDir, "video-stream-cache").deleteRecursively() }
        runCatching { File(context.applicationContext.filesDir, "video-file-cache").deleteRecursively() }
    }

    @Synchronized
    fun release() {
        cache?.release()
        cache = null
        databaseProvider?.close()
        databaseProvider = null
        lastPruneAt = 0L
    }
}

private class RoutingVideoDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val cacheFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingVideoDataSource(
        upstreamFactory.createDataSource(),
        cacheFactory.createDataSource()
    )
}

private class RoutingVideoDataSource(
    private val direct: DataSource,
    private val cached: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        direct.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val mediaId = dataSpec.uri.takeIf { it.scheme == "manydrive" }?.getQueryParameter("id")
        val source = mediaId?.let(PlaybackSourceRegistry::get)
        val streamCache = source?.file?.mimeType?.startsWith("video/") == true &&
            source.accountType in setOf("PHOTOS", "S3")
        active = if (streamCache) cached else direct
        return requireNotNull(active).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
