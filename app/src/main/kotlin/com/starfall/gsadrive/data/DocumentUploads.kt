package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Walk only the tree explicitly granted by Android's document picker. */
class DocumentUploads(
    private val resolver: ContentResolver,
    private val createFolder: suspend (String, String?) -> String,
    private val uploadFile: suspend (Uri, String, String, String?) -> Unit
) {
    suspend fun files(uris: List<Uri>, parent: String?) {
        uris.forEach { uri ->
            currentCoroutineContext().ensureActive()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                check(it.moveToFirst()) { tr("Cannot read file name.") }
                it.getString(0)
            } ?: error(tr("The selected file cannot be read."))
            uploadFile(uri, name, resolver.getType(uri) ?: "application/octet-stream", parent)
        }
    }

    suspend fun tree(tree: Uri, parent: String?) {
        val visited = mutableSetOf<String>()
        suspend fun walk(id: String, destination: String?, depth: Int) {
            currentCoroutineContext().ensureActive()
            check(depth < 128 && visited.add(id)) { tr("The directory structure is too deep or repetitive.") }
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            val name = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                check(it.moveToFirst()) { tr("Cannot read directory.") }; it.getString(0)
            } ?: error(tr("The selected folder cannot be read."))
            val target = createFolder(name, destination)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            resolver.query(children, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val child = cursor.getString(0)
                    val childName = cursor.getString(1)
                    val mime = cursor.getString(2) ?: "application/octet-stream"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(child, target, depth + 1)
                    else uploadFile(DocumentsContract.buildDocumentUriUsingTree(tree, child), childName, mime, target)
                }
            } ?: error(tr("Unable to read the contents of folder $name."))
        }
        walk(DocumentsContract.getTreeDocumentId(tree), parent, 0)
    }
}
