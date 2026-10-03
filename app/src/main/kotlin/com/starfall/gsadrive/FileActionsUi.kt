package com.starfall.gsadrive

import com.starfall.gsadrive.ui.CopyableError

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.DrivePermission
import com.starfall.gsadrive.data.LocalFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class FileActionMode { MAIN, PERMISSIONS, COPY, MOVE, PHOTOS, INFO }
private enum class MultiFileActionMode { MAIN, MOVE, PHOTOS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiFileActionsSheet(
    files: List<DriveFile>,
    account: AccountEntry,
    actions: FileActionCallbacks,
    onDismiss: () -> Unit,
    onActionDone: () -> Unit
) {
    var mode by remember(files.map { it.id }) { mutableStateOf(MultiFileActionMode.MAIN) }
    val driveActions = account.type != AccountType.S3

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        when (mode) {
            MultiFileActionMode.MAIN -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(
                        tr("${files.size} items selected"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (driveActions) {
                    ActionRow(Icons.Outlined.DriveFileMove, tr("Move")) {
                        mode = MultiFileActionMode.MOVE
                    }
                }
                ActionRow(Icons.Outlined.AddPhotoAlternate, tr("Upload to Photos")) {
                    if (files.any { it.isFolder }) mode = MultiFileActionMode.PHOTOS
                    else {
                        actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.RAW)
                        onActionDone()
                    }
                }
                if (actions.trashMany != null) {
                    ActionRow(Icons.Outlined.Delete, tr(if (driveActions) "Move to trash" else "Delete")) {
                        actions.trashMany.invoke(files)
                        onActionDone()
                    }
                }
            }
            MultiFileActionMode.MOVE -> MoveManyPanel(
                files = files,
                actions = actions,
                onBack = { mode = MultiFileActionMode.MAIN },
                onDone = onActionDone
            )
            MultiFileActionMode.PHOTOS -> PhotosFolderModePanel(
                files = files,
                actions = actions,
                multiple = true,
                onBack = { mode = MultiFileActionMode.MAIN },
                onDone = onActionDone
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileActionsSheet(
    file: DriveFile,
    account: AccountEntry,
    actions: FileActionCallbacks,
    onDismiss: () -> Unit
) {
    var mode by remember(file.id) { mutableStateOf(FileActionMode.MAIN) }
    var showShare by remember(file.id) { mutableStateOf(false) }
    var showRename by remember(file.id) { mutableStateOf(false) }
    val driveActions = account.type != AccountType.S3

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        when (mode) {
            FileActionMode.MAIN -> MainActions(
                file = file,
                driveActions = driveActions,
                actions = actions,
                onShare = { showShare = true },
                onPermissions = { mode = FileActionMode.PERMISSIONS },
                onRename = { showRename = true },
                onCopy = { mode = FileActionMode.COPY },
                onMove = { mode = FileActionMode.MOVE },
                onUploadPhotos = {
                    if (file.isFolder) mode = FileActionMode.PHOTOS
                    else {
                        actions.uploadToPhotos?.invoke(file, PhotosFolderUploadMode.RAW)
                        onDismiss()
                    }
                },
                onInfo = { mode = FileActionMode.INFO },
                onDismiss = onDismiss
            )
            FileActionMode.PERMISSIONS -> PermissionsPanel(
                file = file,
                actions = actions,
                onBack = { mode = FileActionMode.MAIN }
            )
            FileActionMode.COPY -> TransferPanel(
                file = file,
                account = account,
                actions = actions,
                move = false,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.MOVE -> TransferPanel(
                file = file,
                account = account,
                actions = actions,
                move = true,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.PHOTOS -> PhotosFolderModePanel(
                files = listOf(file),
                actions = actions,
                multiple = false,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.INFO -> InfoPanel(file = file, onBack = { mode = FileActionMode.MAIN })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showShare) ShareDialog(
        file = file,
        actions = actions,
        onDismiss = { showShare = false },
        onDone = { showShare = false }
    )
    if (showRename) RenameDialog(
        file = file,
        actions = actions,
        onDismiss = { showRename = false },
        onDone = { showRename = false; onDismiss() }
    )
}

@Composable
private fun MainActions(
    file: DriveFile,
    driveActions: Boolean,
    actions: FileActionCallbacks,
    onShare: () -> Unit,
    onPermissions: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onUploadPhotos: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description, null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(18.dp))
        Text(file.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))

    if (driveActions) {
        ActionRow(Icons.Outlined.PersonAdd, tr("Share"), onShare)
        ActionRow(Icons.Outlined.ManageAccounts, tr("Manage access"), onPermissions)
        HorizontalDivider(Modifier.padding(start = 72.dp))
        ActionRow(Icons.Outlined.Link, tr("Copy link")) {
            val link = file.webViewUrl ?: "https://drive.google.com/open?id=${file.id}"
            clipboard.setText(AnnotatedString(link))
            Toast.makeText(context, tr("Link copied."), Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        HorizontalDivider(Modifier.padding(start = 72.dp))
        ActionRow(Icons.Outlined.Edit, tr("Rename"), onRename)
    }
    if (actions.copy != null || actions.transferToLocal != null) {
        ActionRow(Icons.Outlined.ContentCopy, tr("Copy"), onCopy)
    }
    if (driveActions || actions.transferToLocal != null) {
        ActionRow(Icons.Outlined.DriveFileMove, tr("Move"), onMove)
    }
    if (actions.uploadToPhotos != null && (file.isFolder || file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/"))) {
        ActionRow(Icons.Outlined.AddPhotoAlternate, tr("Upload to Google Photos"), onUploadPhotos)
    }
    actions.download?.let { download ->
        ActionRow(Icons.Outlined.Download, tr("Download")) {
            onDismiss()
            download(file)
        }
    }
    ActionRow(Icons.Outlined.Info, tr("View details"), onInfo)
    if (actions.trash != null) {
        ActionRow(Icons.Outlined.Delete, tr(if (driveActions) "Move to trash" else "Delete")) {
            actions.trash.invoke(file)
            onDismiss()
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ShareDialog(file: DriveFile, actions: FileActionCallbacks, onDismiss: () -> Unit, onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("reader") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Share ${file.name}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text(tr("Email")) }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "reader", onClick = { role = "reader" }, enabled = !busy)
                    Text(tr("Viewer"))
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = role == "writer", onClick = { role = "writer" }, enabled = !busy)
                    Text(tr("Editor"))
                }
                error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && email.contains('@'), onClick = {
                busy = true; error = null
                actions.share(file, email.trim(), role) { result ->
                    busy = false
                    result.onSuccess { onDone() }.onFailure { error = it.message ?: tr("Cannot be shared.") }
                }
            }) { Text(tr("Share")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun RenameDialog(file: DriveFile, actions: FileActionCallbacks, onDismiss: () -> Unit, onDone: () -> Unit) {
    var name by remember(file.id) { mutableStateOf(file.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Rename")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("New name")) }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && name.trim() != file.name, onClick = {
                busy = true; error = null
                actions.rename(file, name.trim()) { result ->
                    busy = false
                    result.onSuccess { onDone() }.onFailure { error = it.message ?: tr("Cannot change name.") }
                }
            }) { Text(tr("Rename")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun PermissionsPanel(file: DriveFile, actions: FileActionCallbacks, onBack: () -> Unit) {
    var permissions by remember(file.id) { mutableStateOf<List<DrivePermission>>(emptyList()) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true; error = null
        actions.loadPermissions(file) { result ->
            loading = false
            result.onSuccess { permissions = it }.onFailure { error = it.message ?: tr("Could not get permissions.") }
        }
    }
    LaunchedEffect(file.id) { reload() }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
        Text(tr("Manage access"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
    if (!loading && error == null && permissions.isEmpty()) {
        Text(tr("No individual sharing permissions."), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        items(permissions, key = { it.id }) { permission ->
            ListItem(
                headlineContent = { Text(permission.displayName ?: permission.emailAddress ?: permission.type) },
                supportingContent = { Text("${permission.emailAddress.orEmpty()}${if (permission.emailAddress != null) " · " else ""}${roleLabel(permission.role)}") },
                leadingContent = { Icon(if (permission.type == "anyone") Icons.Outlined.Public else Icons.Outlined.Person, null) },
                trailingContent = {
                    if (permission.role != "owner") {
                        IconButton(onClick = {
                            actions.removePermission(file, permission) { result ->
                                result.onSuccess { reload() }.onFailure { error = it.message ?: tr("Cannot remove permissions.") }
                            }
                        }) { Icon(Icons.Outlined.PersonRemove, tr("Remove access")) }
                    }
                }
            )
        }
    }
}

@Composable
private fun TransferPanel(
    file: DriveFile,
    account: AccountEntry,
    actions: FileActionCallbacks,
    move: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val canUseCloud = account.type != AccountType.S3 && (move || actions.copy != null)
    val localRoot = actions.localRootPath
    val canUseSystem = localRoot != null && actions.transferToLocal != null
    var scope by remember(file.id, move) { mutableStateOf(if (canUseCloud) "cloud" else "system") }
    var cloudPath by remember(file.id, move) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudFolders by remember(file.id, move) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudLoading by remember(file.id, move) { mutableStateOf(false) }
    var cloudValid by remember(file.id, move) { mutableStateOf(false) }
    var localDestination by remember(file.id, move, localRoot) { mutableStateOf(localRoot.orEmpty()) }
    var localFolders by remember(file.id, move) { mutableStateOf<List<File>>(emptyList()) }
    var localLoading by remember(file.id, move) { mutableStateOf(false) }
    var localValid by remember(file.id, move) { mutableStateOf(false) }
    var transferring by remember(file.id, move) { mutableStateOf(false) }
    var error by remember(file.id, move) { mutableStateOf<String?>(null) }

    fun loadCloud(parentId: String?) {
        cloudLoading = true
        cloudValid = false
        error = null
        actions.loadFolders(parentId) { result ->
            cloudLoading = false
            result.onSuccess {
                cloudFolders = it.filterNot { folder -> folder.id == file.id }
                cloudValid = true
            }.onFailure { error = it.message ?: tr("Unable to load directory listing.") }
        }
    }

    LaunchedEffect(file.id, move, canUseCloud) {
        if (canUseCloud) loadCloud(null)
    }
    LaunchedEffect(scope, localDestination, localRoot) {
        if (scope != "system" || localRoot == null) return@LaunchedEffect
        localLoading = true
        localValid = false
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { LocalFileAccess(File(localRoot)).list(localDestination).filter { it.isDirectory } }
        }
        localFolders = result.getOrDefault(emptyList())
        localValid = result.isSuccess
        error = result.exceptionOrNull()?.message
        localLoading = false
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = !transferring) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back"))
        }
        Column(Modifier.weight(1f)) {
            Text(
                tr(if (move) "Move" else "Copy"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(file.name, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (canUseCloud && canUseSystem) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = scope == "cloud",
                onClick = { scope = "cloud"; error = null },
                label = { Text(account.title, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = scope == "system",
                onClick = { scope = "system"; error = null },
                label = { Text(tr("System Files")) },
                leadingIcon = { Icon(Icons.Outlined.Storage, null, Modifier.size(18.dp)) }
            )
        }
    }

    if (transferring || cloudLoading || localLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        CopyableError(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }

    if (scope == "cloud" && canUseCloud) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !transferring && cloudPath.isNotEmpty(), onClick = {
                cloudPath = cloudPath.dropLast(1)
                loadCloud(cloudPath.lastOrNull()?.id)
            }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
            Text(cloudPath.lastOrNull()?.name ?: account.title, Modifier.weight(1f), maxLines = 1)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp)) {
            items(cloudFolders, key = { it.id }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    modifier = Modifier.clickable(enabled = !transferring && !cloudLoading) {
                        cloudPath = cloudPath + folder
                        loadCloud(folder.id)
                    }
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(enabled = !transferring && !cloudLoading && cloudValid, onClick = {
                transferring = true
                error = null
                val destination = cloudPath.lastOrNull()?.id ?: "root"
                val complete: (Result<Unit>) -> Unit = { result ->
                    transferring = false
                    result.onSuccess { onDone() }
                        .onFailure { error = it.message ?: tr("File transfer could not be completed.") }
                }
                if (move) actions.move(file, destination, complete)
                else actions.copy?.invoke(file, destination, complete)
                    ?: complete(Result.failure(UnsupportedOperationException()))
            }) { Text(tr(if (move) "Move here" else "Copy here")) }
        }
    } else if (canUseSystem) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !transferring && localDestination != localRoot,
                onClick = { localDestination = File(localDestination).parent ?: localRoot }
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
            Text(localDestination, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp)) {
            items(localFolders, key = { it.path }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    modifier = Modifier.clickable(enabled = !transferring && !localLoading) {
                        localDestination = folder.path
                    }
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(enabled = !transferring && !localLoading && localValid, onClick = {
                transferring = true
                error = null
                actions.transferToLocal.invoke(file, localRoot, localDestination, move) { result ->
                    transferring = false
                    result.onSuccess { onDone() }
                        .onFailure { error = it.message ?: tr("File transfer could not be completed.") }
                }
            }) { Text(tr(if (move) "Move here" else "Copy here")) }
        }
    } else {
        CopyableError(
            tr("There are no target locations available."),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun PhotosFolderModePanel(
    files: List<DriveFile>,
    actions: FileActionCallbacks,
    multiple: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
        Column(Modifier.weight(1f)) {
            Text(tr("Upload to Photos"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (multiple) tr("${files.size} items selected") else files.firstOrNull()?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ActionRow(Icons.Outlined.PhotoLibrary, tr("Upload directly")) {
        if (multiple) actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.RAW)
        else files.firstOrNull()?.let { actions.uploadToPhotos?.invoke(it, PhotosFolderUploadMode.RAW) }
        onDone()
    }
    Text(
        tr("Photos and videos in folders are uploaded directly to the Photos library without creating an album."),
        modifier = Modifier.padding(start = 76.dp, end = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionRow(Icons.Outlined.PhotoAlbum, tr("Upload as album")) {
        if (multiple) actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.ALBUM)
        else files.firstOrNull()?.let { actions.uploadToPhotos?.invoke(it, PhotosFolderUploadMode.ALBUM) }
        onDone()
    }
    Text(
        tr("Each selected root folder creates an album with the same name, containing its photos and videos."),
        modifier = Modifier.padding(start = 76.dp, end = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MoveManyPanel(
    files: List<DriveFile>,
    actions: FileActionCallbacks,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val selectionKey = remember(files) { files.map { it.id }.sorted().joinToString(":") }
    val selectedFolderIds = remember(files) { files.filter { it.isFolder }.map { it.id }.toSet() }
    var path by remember(selectionKey) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var folders by remember(selectionKey) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var loading by remember(selectionKey) { mutableStateOf(true) }
    var moving by remember(selectionKey) { mutableStateOf(false) }
    var error by remember(selectionKey) { mutableStateOf<String?>(null) }

    fun load(parentId: String?) {
        loading = true
        error = null
        actions.loadFolders(parentId) { result ->
            loading = false
            result.onSuccess { folders = it.filterNot { folder -> folder.id in selectedFolderIds } }
                .onFailure { error = it.message ?: tr("Unable to load directory listing.") }
        }
    }

    LaunchedEffect(selectionKey) { load(null) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (path.isEmpty()) onBack() else {
                path = path.dropLast(1)
                load(path.lastOrNull()?.id)
            }
        }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
        Column(Modifier.weight(1f)) {
            Text(tr("Move ${files.size} items"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                path.lastOrNull()?.name ?: tr("My Drive"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (loading || moving) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        CopyableError(
            it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp)) {
        items(folders, key = { it.id }) { folder ->
            ListItem(
                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Outlined.Folder, null) },
                modifier = Modifier.clickable(enabled = !loading && !moving) {
                    path = path + folder
                    load(folder.id)
                }
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Button(enabled = !loading && !moving, onClick = {
            moving = true
            error = null
            val destination = path.lastOrNull()?.id ?: "root"
            actions.moveMany(files, destination) { result ->
                moving = false
                result.onSuccess { onDone() }
                    .onFailure { error = it.message ?: tr("Selected items cannot be moved.") }
            }
        }) { Text(tr("Move here")) }
    }
}

@Composable
private fun InfoPanel(file: DriveFile, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
        Text(tr("Details"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoLine(tr("Name"), file.name)
        InfoLine(tr("Type"), if (file.isFolder) tr("Folder") else file.mimeType.ifBlank { tr("Unknown") })
        file.size?.let { InfoLine(tr("Size"), formatFileSize(it)) }
        file.modifiedTime?.let { InfoLine(tr("Modified"), it.replace('T', ' ').substringBefore('.')) }
        InfoLine("ID", file.id)
        file.webViewUrl?.let { InfoLine(tr("Link"), it) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun roleLabel(role: String): String = when (role) {
    "owner" -> tr("Owner")
    "writer" -> tr("Editor")
    "commenter" -> tr("Commenter")
    "reader" -> tr("Viewer")
    else -> role
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
