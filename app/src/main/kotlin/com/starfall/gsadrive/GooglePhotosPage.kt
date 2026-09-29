package com.starfall.gsadrive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GooglePhotosPage(model: Model, padding: PaddingValues, reload: () -> Unit,
    open: (DriveFile, List<DriveFile>) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Ảnh và video do ứng dụng tải lên"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = reload, enabled = !model.loading) { Text(tr("Làm mới")) }
        }
        if (model.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.message?.let { com.starfall.gsadrive.ui.CopyableError(it, modifier = Modifier.padding(16.dp)) }
        if (!model.loading && model.message == null && model.files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(tr("Chưa có ảnh hoặc video được ứng dụng này tải lên Google Photos."))
            }
        } else LazyVerticalGrid(columns = GridCells.Adaptive(144.dp), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(model.files, key = { it.id }) { file ->
                Card(Modifier.fillMaxWidth().clickable { open(file, model.files) }) {
                    val bitmap by produceState<android.graphics.Bitmap?>(null, file.thumbnailUrl) {
                        value = withContext(Dispatchers.IO) {
                            file.thumbnailUrl?.let { runCatching { ThumbnailRepository.load(it) }.getOrNull() }
                        }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center) {
                        bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            ?: Icon(Icons.Outlined.Image, null)
                        if (file.mimeType.startsWith("video/")) Surface(
                            Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Outlined.PlayArrow, tr("Video"), Modifier.padding(4.dp))
                        }
                    }
                    Text(file.name, Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
