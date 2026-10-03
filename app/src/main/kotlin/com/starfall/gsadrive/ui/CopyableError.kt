package com.starfall.gsadrive.ui

import com.starfall.gsadrive.tr

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

/** Select part of a message or copy its complete, untruncated text with one tap. */
@Composable
internal fun CopyableError(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val clipboard = LocalClipboardManager.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(text, color = color, style = style)
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
            Icon(Icons.Outlined.ContentCopy, tr("Copy message"), tint = color)
        }
    }
}
