package com.starfall.gsadrive.data

import com.google.api.client.http.GenericUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhotosApiTest {
    @Test fun followsAllPagesAndKeepsPhotosSeparateFromDrive() {
        val urls = mutableListOf<String>()
        val pages = ArrayDeque(listOf(
            """{"mediaItems":[{"id":"same-id","filename":"photo.jpg","mimeType":"image/jpeg","baseUrl":"https://example.test/photo"}],"nextPageToken":"next +/"}""",
            """{"mediaItems":[{"id":"video","filename":"movie.mp4","mimeType":"video/mp4"},{"id":"ignored","mimeType":"text/plain"},{"id":"same-id","mimeType":"image/jpeg"}]}"""
        ))
        val files = PhotosApi.list { url -> urls += url; JSONObject(pages.removeFirst()) }
        assertEquals(listOf("photos:same-id", "photos:video"), files.map { it.id })
        assertEquals("same-id", files[0].photosMediaId)
        assertEquals("https://example.test/photo=w400-h400", files[0].thumbnailUrl)
        assertEquals("100", GenericUrl(urls[0]).getFirst("pageSize"))
        assertEquals("next +/", GenericUrl(urls[1]).getFirst("pageToken"))
        assertNull(files[1].thumbnailUrl)
    }

    @Test fun emptyLibraryIsValid() {
        assertTrue(PhotosApi.list { JSONObject("{}") }.isEmpty())
    }

    @Test fun readyVideoUsesVideoBytesAndPhotoUsesBoundedImage() {
        val video = JSONObject("""{"baseUrl":"https://example.test/new","mediaMetadata":{"video":{"status":"READY"}}}""")
        assertEquals("https://example.test/new=dv", PhotosApi.contentUrl(video, true))
        assertEquals("https://example.test/new=w4096-h4096", PhotosApi.contentUrl(video, false))
    }

    @Test(expected = IllegalStateException::class)
    fun processingVideoDoesNotPlayItsThumbnail() {
        PhotosApi.contentUrl(JSONObject("""{"baseUrl":"https://example.test/preview","mediaMetadata":{"video":{"status":"PROCESSING"}}}"""), true)
    }
}
