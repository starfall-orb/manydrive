package com.starfall.gsadrive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GooglePhotosPage(
    model: Model,
    padding: PaddingValues,
    open: (DriveFile, List<DriveFile>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionTarget by remember { mutableStateOf<DriveFile?>(null) }
    var infoTarget by remember { mutableStateOf<DriveFile?>(null) }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode && actionTarget == null && infoTarget == null) {
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            model.message?.let { message ->
                item(span = { GridItemSpan(3) }) {
                    com.starfall.gsadrive.ui.CopyableError(
                        message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (!model.loading && model.message == null && model.files.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("No photos or videos uploaded by this app to Google Photos yet."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(
                items = model.files,
                key = { _, file -> file.id },
                span = { index, _ -> GridItemSpan(if (isFeaturedPhoto(index)) 3 else 1) }
            ) { index, file ->
                val featured = isFeaturedPhoto(index)
                val selected = file.id in selectedIds
                val shape = if (featured) RoundedCornerShape(22.dp) else RoundedCornerShape(3.dp)
                val itemModifier = if (featured) {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .aspectRatio(1.55f)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(1f)
                }

                Box(
                    itemModifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (selected) selectedIds - file.id else selectedIds + file.id
                                } else {
                                    open(file, model.files)
                                }
                            },
                            onLongClick = {
                                selectedIds = selectedIds + file.id
                                actionTarget = file
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbnailUrl = remember(file.thumbnailUrl, featured) {
                        photoThumbnailUrl(file.thumbnailUrl, featured)
                    }
                    val bitmap by produceState<android.graphics.Bitmap?>(null, thumbnailUrl) {
                        value = withContext(Dispatchers.IO) {
                            thumbnailUrl?.let { runCatching { ThumbnailRepository.load(it) }.getOrNull() }
                        }
                    }

                    bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            file.name,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Icon(
                        Icons.Outlined.Image,
                        null,
                        Modifier.size(if (featured) 42.dp else 28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (file.mimeType.startsWith("video/")) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (featured) 14.dp else 7.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.58f),
                            contentColor = Color.White
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                tr("Videos"),
                                Modifier.padding(4.dp).size(if (featured) 24.dp else 18.dp)
                            )
                        }
                    }

                    if (selectionMode) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(if (featured) 14.dp else 7.dp)
                                .size(if (featured) 32.dp else 28.dp),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.22f),
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f))
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    tr("Selected"),
                                    Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode && actionTarget == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 3.dp
            ) {
                Text(
                    tr("${selectedIds.size} selected"),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    actionTarget?.let { file ->
        PhotoItemActionsSheet(
            file = file,
            selectedCount = selectedIds.size,
            onDismiss = { actionTarget = null },
            onOpen = {
                actionTarget = null
                selectedIds = emptySet()
                open(file, model.files)
            },
            onInfo = {
                actionTarget = null
                infoTarget = file
            },
            onSelectAll = {
                selectedIds = model.files.mapTo(linkedSetOf()) { it.id }
                actionTarget = null
            },
            onDeselect = {
                selectedIds = selectedIds - file.id
                actionTarget = null
            }
        )
    }

    infoTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(file.mimeType)
                    file.modifiedTime?.takeIf { it.isNotBlank() }?.let {
                        Text(it.replace("T", " ").substringBefore("."))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text(tr("Close")) }
            }
        )
    }
}

private fun isFeaturedPhoto(index: Int): Boolean = index >= 6 && (index - 6) % 9 == 0

private fun photoThumbnailUrl(url: String?, featured: Boolean): String? {
    if (url == null) return null
    val suffix = if (featured) "=w1200-h800-c" else "=w520-h520-c"
    return if (url.matches(Regex(".*=w\\d+-h\\d+.*"))) {
        url.replace(Regex("=w\\d+-h\\d+.*$"), suffix)
    } else {
        url
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoItemActionsSheet(
    file: DriveFile,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Text(
            if (selectedCount > 1) tr("$selectedCount selected") else file.name,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PhotoActionButton(Icons.Outlined.Visibility, tr("See"), onOpen)
            PhotoActionButton(Icons.Outlined.Info, tr("Details"), onInfo)
            PhotoActionButton(Icons.Outlined.DoneAll, tr("Select all"), onSelectAll)
            PhotoActionButton(Icons.Outlined.Check, tr("Deselect"), onDeselect)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.PhotoActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.weight(1f).combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(icon, label, Modifier.padding(16.dp).size(26.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
