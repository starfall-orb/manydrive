package com.starfall.gsadrive

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.ui.ExpandableMediaPlayer
import com.starfall.gsadrive.ui.ImageViewer
import com.starfall.gsadrive.ui.theme.ManyDriveTheme
import com.starfall.gsadrive.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight entry point for media opened from another app.
 *
 * The Activity only hosts the viewer. Audio/video playback is owned by the same
 * [MediaPlaybackService] used by the main app, so notification controls and background playback
 * have identical behavior without initializing the ManyDrive browser/account shell.
 */
class ExternalMediaActivity : ComponentActivity() {
    private var file by mutableStateOf<DriveFile?>(null)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var playback by mutableStateOf<MediaController?>(null)
    private var sources: List<PlaybackSource>? = null
    private var playbackStartedFor: String? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var notificationPermissionPending = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionPending = false
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                tr("Không có quyền thông báo: trình phát vẫn chạy nền nhưng điều khiển media có thể không hiện trên thanh thông báo."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
                    player = playback,
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
                    error = null
                    if (isMediaPreview(resolved)) {
                        ensureNotificationPermission()
                        sources = registerLocalPlaybackSources(listOf(resolved))
                        ensurePlaybackController()
                        maybeStartPlayback()
                    } else {
                        loading = false
                    }
                }
                .onFailure {
                    loading = false
                    error = it.message ?: tr("Không thể mở tệp.")
                }
        }
    }

    private fun ensurePlaybackController() {
        if (playback != null || controllerFuture != null) return
        val sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { controller ->
                        playback = controller
                        maybeStartPlayback()
                    }
                    .onFailure { failure ->
                        controllerFuture = null
                        loading = false
                        error = failure.message ?: tr("Không thể kết nối dịch vụ phát media.")
                    }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun maybeStartPlayback() {
        val selected = file ?: return
        if (!isMediaPreview(selected) || playbackStartedFor == selected.id) return
        val controller = playback ?: return
        val registeredSources = sources ?: return
        runCatching { startLocalPlayback(this, controller, selected, registeredSources) }
            .onSuccess {
                playbackStartedFor = selected.id
                loading = false
                error = null
            }
            .onFailure {
                loading = false
                error = it.message ?: tr("Không thể mở media.")
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

    private fun ensureNotificationPermission() {
        if (notificationPermissionPending) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionPending = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        playback = null
        super.onDestroy()
    }
}

@Composable
private fun ExternalMediaScreen(
    file: DriveFile?,
    loading: Boolean,
    error: String?,
    player: MediaController?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    when {
        loading -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        error != null -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = onBack) { Text(tr("Đóng")) }
            }
        }

        file == null -> Unit

        file.mimeType.startsWith("image/") -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ImageViewer(file.id)
        }

        player != null -> ExpandableMediaPlayer(
            player = player,
            minimized = false,
            topPadding = 0.dp,
            miniBounds = null,
            onMinimize = onBack,
            onExpand = {},
            onClose = onBack,
            queue = listOf(file),
            index = 0,
            previewPaths = emptyMap(),
            error = null,
            onSwipeTo = {}
        )
    }
}
