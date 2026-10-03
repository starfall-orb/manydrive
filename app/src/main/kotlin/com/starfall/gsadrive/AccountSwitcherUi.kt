package com.starfall.gsadrive

import com.starfall.gsadrive.ui.CopyableError

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AccountSwitcherDialog(
    accounts: AccountUi,
    active: AccountEntry?,
    onDismiss: () -> Unit,
    onSelect: (AccountEntry) -> Unit,
    onRemove: (AccountEntry) -> Unit,
    onAdd: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!accounts.busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 720.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Account"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, enabled = !accounts.busy) {
                            Icon(Icons.Outlined.Close, tr("Close"))
                        }
                    }

                    accounts.message?.let {
                        CopyableError(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                    if (accounts.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    val ordered = accounts.entries.sortedByDescending { it.key == active?.key }
                    if (ordered.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(tr("No accounts yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(ordered, key = { it.key }) { entry ->
                                AccountSwitcherRow(
                                    entry = entry,
                                    selected = entry.key == active?.key,
                                    enabled = !accounts.busy,
                                    onSelect = { onSelect(entry) },
                                    onRemove = { onRemove(entry) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !accounts.busy, onClick = onAdd)
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, null)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Text(tr("Add another account"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSwitcherRow(
    entry: AccountEntry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val avatarSize = if (selected) 58.dp else 42.dp
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(start = 24.dp, end = 8.dp, top = if (selected) 16.dp else 12.dp, bottom = if (selected) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (entry.type) {
            AccountType.GOOGLE -> AccountAvatar(entry, onClick = onSelect, size = avatarSize)
            AccountType.S3, AccountType.SERVICE -> Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(avatarSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (entry.type == AccountType.S3) Icons.Outlined.Cloud else Icons.Outlined.AccountCircle,
                        null,
                        modifier = Modifier.size(if (selected) 30.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = if (selected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                entry.subtitle,
                style = if (selected) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.AutoMirrored.Outlined.Logout, tr("Sign out of ${entry.title}"))
        }
    }
}
