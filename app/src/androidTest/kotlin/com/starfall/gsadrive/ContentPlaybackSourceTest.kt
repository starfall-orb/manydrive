package com.starfall.gsadrive

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.starfall.gsadrive.data.DriveFile
import java.io.File
import org.junit.Assert.*
import org.junit.Test

@OptIn(UnstableApi::class)
class ContentPlaybackSourceTest {
    @Test fun contentUriKeepsAuthorityEncodingAndSeekRange() {
        val uri = "content://test.documents/document/primary%3AMusic%2Fa%20b.mp3"
        val file = DriveFile(uri, "a b.mp3", "audio/mpeg", null)
        val source = PlaybackSource("LOCAL:$uri", file, "CONTENT", null, null, File("unused"))
        PlaybackSourceRegistry.replace(listOf(source))
        try {
            val request = DataSpec.Builder().setUri(source.toMediaItem().localConfiguration!!.uri)
                .setPosition(1024).setLength(2048).build()
            val resolved = resolvePlaybackDataSpec(request)
            assertEquals(uri, resolved.uri.toString())
            assertEquals(1024L, resolved.position)
            assertEquals(2048L, resolved.length)
        } finally {
            PlaybackSourceRegistry.clear()
        }
    }
}
