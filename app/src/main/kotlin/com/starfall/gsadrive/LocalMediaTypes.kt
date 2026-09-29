package com.starfall.gsadrive

import android.webkit.MimeTypeMap
import java.util.Locale

/** Shared MIME resolution for system files and external ACTION_VIEW requests. */
internal fun localFileMimeType(name: String, declaredType: String? = null): String {
    val type = declaredType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    if (!type.isNullOrBlank() && type != "application/octet-stream" && '*' !in type) return type
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: if (extension in setOf("md", "log", "kt", "json", "yaml", "toml")) "text/plain"
        else "application/octet-stream"
}
