package com.starfall.gsadrive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.ui.FileViewerPage
import com.starfall.gsadrive.ui.ImageViewer
import com.starfall.gsadrive.ui.theme.ManyDriveTheme
import com.starfall.gsadrive.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight entry point for media opened from another app.
 *
 * This intentionally avoids initializing the ManyDrive account/browser shell. Images are decoded
 * directly and audio/video use an Activity-owned ExoPlayer, so Back returns directly to the app
 * that launched the viewer.
 */
class ExternalMediaActivity : ComponentActivity() {
    private var file by mutableStateOf<DriveFile?>(null)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var player by mutableStateOf<ExoPlayer?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences("manydrive_selection", MODE_PRIVATE)
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        val superDark = preferences.getBoolean("superDark", false)

        setContent {
            ManyDriveTheme(themeMode, superDark) {
                ExternalMediaScreen(
                    file = file,
                    loading = loading,
                    error = error,
                    player = player,
                    onBack = ::finish
                )
            }
        }

        open(intent)
    }

    private fun open(incoming: Intent?) {
        val uri = incoming?.data
        if (incoming?.action != Intent.ACTION_VIEW || uri == null || uri.scheme !in setOf("content", "file")) {
            loading = false
            error = tr("Không thể mở tệp.")
            return
        }

        val declaredType = incoming.type
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { resolveFile(uri, declaredType) } }
                .onSuccess { resolved ->
                    file = resolved
                    loading = false
                    if (resolved.mimeType.startsWith("video/") || resolved.mimeType.startsWith("audio/")) {
                        startPlayer(uri, resolved)
                    }
                }
                .onFailure {
                    loading = false
                    error = it.message ?: tr("Không thể mở tệp.")
                }
        }
    }

    private fun resolveFile(uri: Uri, declaredType: String?): DriveFile {
        var name = uri.lastPathSegment ?: tr("Media")
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0)
                }
            }
        }

        val resolvedType = declaredType?.takeUnless {
            it.isBlank() || '*' in it || it == "application/octet-stream"
        } ?: contentResolver.getType(uri)
        val mime = localFileMimeType(name, resolvedType)
        require(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) {
            tr("Không thể xem loại tệp này.")
        }

        val id = if (uri.scheme == "file") requireNotNull(uri.path) else uri.toString()
        return DriveFile(id, name, mime, null)
    }

    private fun startPlayer(uri: Uri, mediaFile: DriveFile) {
        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(if (uri.scheme == "file") Uri.fromFile(File(mediaFile.id)) else uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(mediaFile.name).build())
                    .build()
            )
            prepare()
            playWhenReady = true
        }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun ExternalMediaScreen(
    file: DriveFile?,
    loading: Boolean,
    error: String?,
    player: ExoPlayer?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = onBack) { Text(tr("Đóng")) }
            }

            file == null -> Unit

            file.mimeType.startsWith("image/") -> ImageViewer(file.id)

            player != null -> FileViewerPage(
                padding = PaddingValues(0.dp),
                file = file,
                localPath = file.id,
                text = null,
                loading = false,
                error = null,
                saving = false,
                player = player,
                onBack = onBack,
                onTextChange = {},
                onSaveText = {}
            )

            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
