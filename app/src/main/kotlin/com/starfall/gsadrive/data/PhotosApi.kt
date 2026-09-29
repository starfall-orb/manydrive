package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import org.json.JSONObject

/** Read app-created media and upload using separately authorized Photos scopes. */
object PhotosApi {

    const val READ_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"
    private const val MEDIA_URL = "https://photoslibrary.googleapis.com/v1/mediaItems"

    internal fun parseItem(item: JSONObject): DriveFile {
        val id = item.getString("id")
        return DriveFile(
            id = "photos:$id", name = item.optString("filename").ifBlank { id },
            mimeType = item.optString("mimeType"),
            modifiedTime = item.optJSONObject("mediaMetadata")?.optString("creationTime"),
            thumbnailUrl = item.optString("baseUrl").takeIf { it.isNotBlank() }?.plus("=w400-h400"),
            photosMediaId = id
        )
    }

    fun list(accessToken: String): List<DriveFile> = list { url ->
        JSONObject(GoogleApiClient.request(accessToken, "GET", url).decodeToString())
    }

    internal fun list(loadPage: (String) -> JSONObject): List<DriveFile> {
        val items = mutableListOf<DriveFile>()
        var pageToken = ""
        do {
            val url = com.google.api.client.http.GenericUrl(MEDIA_URL).apply {
                set("pageSize", 100)
                if (pageToken.isNotEmpty()) set("pageToken", pageToken)
            }
            val page = loadPage(url.build())
            val media = page.optJSONArray("mediaItems")
            if (media != null) for (i in 0 until media.length()) {
                val file = parseItem(media.getJSONObject(i))
                if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) items += file
            }
            pageToken = page.optString("nextPageToken")
        } while (pageToken.isNotEmpty())
        return items.distinctBy { it.id }
    }

    // Base URLs expire; resolve again for each playback open/seek instead of persisting URLs.
    fun mediaUrl(accessToken: String, mediaId: String, video: Boolean): String {
        val encoded = java.net.URLEncoder.encode(mediaId, "UTF-8")
        val item = JSONObject(GoogleApiClient.request(accessToken, "GET", "$MEDIA_URL/$encoded").decodeToString())
        return contentUrl(item, video)
    }

    internal fun contentUrl(item: JSONObject, video: Boolean): String {
        if (video) check(item.optJSONObject("mediaMetadata")?.optJSONObject("video")?.optString("status") == "READY") {
            tr("Video Google Photos chưa sẵn sàng. Hãy thử lại sau.")
        }
        return item.getString("baseUrl") + if (video) "=dv" else "=w4096-h4096"
    }

    fun downloadImage(accessToken: String, mediaId: String, target: java.io.File) {
        val request = GoogleApiClient.transport.createRequestFactory()
            .buildGetRequest(com.google.api.client.http.GenericUrl(mediaUrl(accessToken, mediaId, false)))
        request.connectTimeout = 30_000
        request.readTimeout = 60_000
        val response = request.execute()
        try { target.outputStream().use { output -> response.content.use { it.copyTo(output) } } }
        finally { response.disconnect() }
    }
    fun createAlbum(accessToken: String, title: String): String {
        val body = JSONObject().put("album", JSONObject().put("title", title))
        return JSONObject(GoogleApiClient.request(accessToken, "POST",
            "https://photoslibrary.googleapis.com/v1/albums", body.toString().toByteArray()).decodeToString()).getString("id")
    }

    fun upload(accessToken: String, file: java.io.File, filename: String, mimeType: String, albumId: String?) {
        val request = GoogleApiClient.transport.createRequestFactory(GoogleApiClient.initializer(accessToken, 300_000))
            .buildPostRequest(com.google.api.client.http.GenericUrl("https://photoslibrary.googleapis.com/v1/uploads"),
                com.google.api.client.http.FileContent("application/octet-stream", file))
        request.headers.set("X-Goog-Upload-Protocol", "raw")
        request.headers.set("X-Goog-Upload-Content-Type", mimeType)
        val response = request.execute()
        val uploadToken = try { response.parseAsString().trim() } finally { response.disconnect() }
        require(uploadToken.isNotEmpty()) { tr("Google Photos không trả về upload token.") }
        val body = JSONObject().put("newMediaItems", org.json.JSONArray().put(
            JSONObject().put("simpleMediaItem", JSONObject().put("uploadToken", uploadToken).put("fileName", filename))))
        albumId?.let { body.put("albumId", it) }
        val result = JSONObject(GoogleApiClient.request(accessToken, "POST",
            "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate", body.toString().toByteArray()).decodeToString())
            .getJSONArray("newMediaItemResults").getJSONObject(0)
        check(result.optJSONObject("status")?.optInt("code", 0) in listOf(null, 0) && result.has("mediaItem")) {
            result.optJSONObject("status")?.optString("message") ?: tr("Không thể tạo mục Google Photos.")
        }
    }
}
