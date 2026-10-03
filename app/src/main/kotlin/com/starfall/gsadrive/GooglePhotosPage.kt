package com.starfall.gsadrive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        val mosaicBlocks = remember(model.files) { buildPhotoMosaic(model.files) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            model.message?.let { message ->
                item(key = "photos-error") {
                    com.starfall.gsadrive.ui.CopyableError(
                        message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (!model.loading && model.message == null && model.files.isEmpty()) {
                item(key = "photos-empty") {
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

            items(mosaicBlocks, key = { it.key }) { block ->
                when (block.kind) {
                    PhotoMosaicKind.ROW -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            block.files.forEach { file ->
                                PhotoMosaicTile(
                                    file = file,
                                    allFiles = model.files,
                                    selectionMode = selectionMode,
                                    selected = file.id in selectedIds,
                                    featured = false,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    onOpen = open,
                                    onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                                    onLongSelect = {
                                        selectedIds = selectedIds + file.id
                                        actionTarget = file
                                    }
                                )
                            }
                        }
                    }

                    PhotoMosaicKind.WIDE -> {
                        val file = block.files.first()
                        PhotoMosaicTile(
                            file = file,
                            allFiles = model.files,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            featured = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.65f),
                            onOpen = open,
                            onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                            onLongSelect = {
                                selectedIds = selectedIds + file.id
                                actionTarget = file
                            }
                        )
                    }

                    PhotoMosaicKind.HERO_LEFT, PhotoMosaicKind.HERO_RIGHT -> {
                        val hero = block.files.first()
                        val side = block.files.drop(1)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val spacing = 2.dp
                            val smallWidth = (maxWidth - spacing * 2) / 3
                            val clusterHeight = smallWidth * 3 + spacing * 2
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                if (block.kind == PhotoMosaicKind.HERO_LEFT) {
                                    PhotoMosaicTile(
                                        file = hero,
                                        allFiles = model.files,
                                        selectionMode = selectionMode,
                                        selected = hero.id in selectedIds,
                                        featured = true,
                                        modifier = Modifier.weight(2f).height(clusterHeight),
                                        onOpen = open,
                                        onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, hero.id) },
                                        onLongSelect = {
                                            selectedIds = selectedIds + hero.id
                                            actionTarget = hero
                                        }
                                    )
                                }

                                Column(
                                    Modifier.weight(1f).height(clusterHeight),
                                    verticalArrangement = Arrangement.spacedBy(spacing)
                                ) {
                                    side.forEach { file ->
                                        PhotoMosaicTile(
                                            file = file,
                                            allFiles = model.files,
                                            selectionMode = selectionMode,
                                            selected = file.id in selectedIds,
                                            featured = false,
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            onOpen = open,
                                            onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                                            onLongSelect = {
                                                selectedIds = selectedIds + file.id
                                                actionTarget = file
                                            }
                                        )
                                    }
                                }

                                if (block.kind == PhotoMosaicKind.HERO_RIGHT) {
                                    PhotoMosaicTile(
                                        file = hero,
                                        allFiles = model.files,
                                        selectionMode = selectionMode,
                                        selected = hero.id in selectedIds,
                                        featured = true,
                                        modifier = Modifier.weight(2f).height(clusterHeight),
                                        onOpen = open,
                                        onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, hero.id) },
                                        onLongSelect = {
                                            selectedIds = selectedIds + hero.id
                                            actionTarget = hero
                                        }
                                    )
                                }
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

private enum class PhotoMosaicKind { ROW, WIDE, HERO_LEFT, HERO_RIGHT }

private data class PhotoMosaicBlock(
    val kind: PhotoMosaicKind,
    val files: List<DriveFile>
) {
    val key: String = kind.name + ":" + files.joinToString("|") { it.id }
}

private fun buildPhotoMosaic(files: List<DriveFile>): List<PhotoMosaicBlock> {
    if (files.isEmpty()) return emptyList()
    val blocks = mutableListOf<PhotoMosaicBlock>()
    var index = 0

    fun addSmallRow() {
        if (index >= files.size) return
        val count = minOf(3, files.size - index)
        blocks += PhotoMosaicBlock(PhotoMosaicKind.ROW, files.subList(index, index + count))
        index += count
    }

    // Match the Google Photos rhythm: two compact rows before the first featured card.
    addSmallRow()
    addSmallRow()

    val featuredCycle = listOf(
        PhotoMosaicKind.WIDE,
        PhotoMosaicKind.HERO_RIGHT,
        PhotoMosaicKind.WIDE,
        PhotoMosaicKind.HERO_LEFT
    )
    var featuredIndex = 0

    while (index < files.size) {
        val kind = featuredCycle[featuredIndex % featuredCycle.size]
        val required = if (kind == PhotoMosaicKind.WIDE) 1 else 4

        if (files.size - index >= required) {
            blocks += PhotoMosaicBlock(kind, files.subList(index, index + required))
            index += required
            featuredIndex++
        } else {
            // Not enough media for a complete featured block: finish with compact rows, no holes.
            while (index < files.size) addSmallRow()
            break
        }

        // Separate featured cards with one or two compact rows. Keep the choice stable for
        // the current media order so recomposition/scrolling never reshuffles the layout.
        val separatorRows = 1 + ((blocks.last().key.hashCode() and Int.MAX_VALUE) % 2)
        repeat(separatorRows) {
            if (index < files.size) addSmallRow()
        }
    }
    return blocks
}

private fun togglePhotoSelection(selectedIds: Set<String>, id: String): Set<String> =
    if (id in selectedIds) selectedIds - id else selectedIds + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoMosaicTile(
    file: DriveFile,
    allFiles: List<DriveFile>,
    selectionMode: Boolean,
    selected: Boolean,
    featured: Boolean,
    modifier: Modifier,
    onOpen: (DriveFile, List<DriveFile>) -> Unit,
    onToggleSelection: () -> Unit,
    onLongSelect: () -> Unit
) {
    val shape = RoundedCornerShape(if (featured) 4.dp else 2.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection() else onOpen(file, allFiles)
                },
                onLongClick = onLongSelect
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
                    .padding(if (featured) 12.dp else 7.dp),
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
                    .padding(if (featured) 12.dp else 7.dp)
                    .size(if (featured) 32.dp else 28.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.22f),
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f))
            ) {
                if (selected) {
                    Icon(Icons.Outlined.Check, tr("Selected"), Modifier.padding(4.dp))
                }
            }
        }
    }
}

private fun photoThumbnailUrl(url: String?, featured: Boolean): String? {
    if (url == null) return null
    val suffix = if (featured) "=w1200-h1800-c" else "=w520-h520-c"
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
