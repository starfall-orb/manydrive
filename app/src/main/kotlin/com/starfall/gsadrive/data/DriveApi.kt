package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as GoogleFile
import com.google.api.services.drive.model.Permission
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String?,
    val size: Long? = null,
    val thumbnailUrl: String? = null,
    val webViewUrl: String? = null,
    val parents: List<String> = emptyList(),
    val trashed: Boolean = false,
    val sharedWithMeTime: String? = null,
    val photosMediaId: String? = null
) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val description get() = if (isFolder) tr("Thư mục") else {
        val time = modifiedTime?.replace("T", " ")?.substringBefore(".") ?: tr("gần đây")
        tr("Đã sửa đổi $time")
    }
}

data class DriveUserProfile(val displayName: String?, val photoLink: String?)

data class DrivePermission(
    val id: String,
    val type: String,
    val role: String,
    val emailAddress: String?,
    val displayName: String?
)


object DriveApi {
    internal fun client(accessToken: String) = Drive.Builder(
        GoogleApiClient.transport, GoogleApiClient.jsonFactory, GoogleApiClient.initializer(accessToken)
    ).setApplicationName("ManyDrive").build()

    private const val FILE_FIELDS = "id,name,mimeType,modifiedTime,size,thumbnailLink,webViewLink,parents,trashed,sharedWithMeTime"

    fun accountProfile(accessToken: String): DriveUserProfile {
        val user = client(accessToken).about().get().setFields("user(displayName,photoLink)").execute().user
        return DriveUserProfile(user?.displayName?.trim()?.takeIf { it.isNotEmpty() },
            user?.photoLink?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun displayName(accessToken: String): String? = accountProfile(accessToken).displayName

    fun listFiles(accessToken: String, sharedWithMe: Boolean = false, trashed: Boolean = false, parentId: String? = null): List<DriveFile> {
        val query = when {
            trashed -> "trashed = true"
            parentId != null -> "'${escapeQuery(parentId)}' in parents and trashed = false"
            sharedWithMe -> "sharedWithMe = true and trashed = false"
            else -> "'root' in parents and 'me' in owners and trashed = false"
        }
        return list(client(accessToken).files().list().setQ(query).setOrderBy("folder,modifiedTime desc"))
    }

    /** Search across folders, including shared content. */
    fun searchFiles(accessToken: String, text: String): List<DriveFile> {
        val term = text.trim()
        if (term.isEmpty()) return emptyList()
        return list(client(accessToken).files().list()
            .setQ("name contains '${escapeQuery(term)}' and trashed = false")
            .setSpaces("drive").setCorpora("user").setOrderBy("folder,name_natural"))
    }

    internal fun list(request: Drive.Files.List): List<DriveFile> {
        request.setSupportsAllDrives(true).setIncludeItemsFromAllDrives(true)
            .setPageSize(100).setFields("nextPageToken,files($FILE_FIELDS)")
        val result = mutableListOf<DriveFile>()
        do {
            val page = request.execute()
            result += page.files.orEmpty().map { it.toDriveFile() }
            request.pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (request.pageToken != null)
        return result
    }

    fun createFolder(accessToken: String, name: String, parentId: String? = null): String =
        client(accessToken).files().create(GoogleFile().setName(name)
            .setMimeType("application/vnd.google-apps.folder").apply { parentId?.let { parents = listOf(it) } })
            .setSupportsAllDrives(true).setFields("id").execute().id

    fun moveToTrash(accessToken: String, fileId: String) {
        client(accessToken).files().update(fileId, GoogleFile().setTrashed(true)).setSupportsAllDrives(true).execute()
    }

    fun restore(accessToken: String, fileId: String) {
        client(accessToken).files().update(fileId, GoogleFile().setTrashed(false)).setSupportsAllDrives(true).execute()
    }

    fun move(accessToken: String, file: DriveFile, newParentId: String) {
        client(accessToken).files().update(file.id, GoogleFile()).setSupportsAllDrives(true)
            .setAddParents(newParentId).apply {
                if (file.parents.isNotEmpty()) setRemoveParents(file.parents.joinToString(","))
            }.execute()
    }

    fun copy(accessToken: String, file: DriveFile, newParentId: String) {
        client(accessToken).files().copy(file.id, GoogleFile().setName(file.name).setParents(listOf(newParentId)))
            .setSupportsAllDrives(true).execute()
    }

    fun share(accessToken: String, fileId: String, email: String, role: String) {
        client(accessToken).permissions().create(fileId, Permission().setType("user").setRole(role).setEmailAddress(email))
            .setSupportsAllDrives(true).setSendNotificationEmail(true).execute()
    }

    fun rename(accessToken: String, fileId: String, newName: String) {
        client(accessToken).files().update(fileId, GoogleFile().setName(newName.trim())).setSupportsAllDrives(true).execute()
    }

    fun listPermissions(accessToken: String, fileId: String): List<DrivePermission> {
        val request = client(accessToken).permissions().list(fileId).setSupportsAllDrives(true)
            .setFields("nextPageToken,permissions(id,type,role,emailAddress,displayName)")
        val result = mutableListOf<DrivePermission>()
        do {
            val page = request.execute()
            result += page.permissions.orEmpty().map {
                DrivePermission(it.id, it.type.orEmpty(), it.role.orEmpty(), it.emailAddress, it.displayName)
            }
            request.pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (request.pageToken != null)
        return result
    }

    fun deletePermission(accessToken: String, fileId: String, permissionId: String) {
        client(accessToken).permissions().delete(fileId, permissionId).setSupportsAllDrives(true).execute()
    }

    fun upload(accessToken: String, name: String, mimeType: String, input: InputStream, parentId: String? = null) {
        val metadata = GoogleFile().setName(name).apply { parentId?.let { parents = listOf(it) } }
        // The caller owns the stream. The SDK handles resumable upload and bounded buffering.
        val media = InputStreamContent(mimeType, input).setCloseInputStream(false)
        client(accessToken).files().create(metadata, media).setSupportsAllDrives(true).execute()
    }

    fun download(accessToken: String, fileId: String): ByteArray = ByteArrayOutputStream().use { output ->
        client(accessToken).files().get(fileId).setSupportsAllDrives(true).executeMediaAndDownloadTo(output)
        output.toByteArray()
    }

    fun downloadTo(accessToken: String, fileId: String, target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().buffered().use { output ->
            client(accessToken).files().get(fileId).setSupportsAllDrives(true).executeMediaAndDownloadTo(output)
        }
    }

    fun updateContent(accessToken: String, fileId: String, mimeType: String, content: ByteArray) {
        client(accessToken).files().update(fileId, null,
            ByteArrayContent(mimeType.ifBlank { "text/plain; charset=UTF-8" }, content))
            .setSupportsAllDrives(true).execute()
    }

    private fun escapeQuery(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")

    private fun GoogleFile.toDriveFile() = DriveFile(
        id = id, name = name ?: tr("Không tên"), mimeType = mimeType.orEmpty(),
        modifiedTime = modifiedTime?.toStringRfc3339(), size = getSize(),
        thumbnailUrl = thumbnailLink, webViewUrl = webViewLink, parents = parents.orEmpty(),
        trashed = trashed == true, sharedWithMeTime = sharedWithMeTime?.toStringRfc3339()
    )
}
