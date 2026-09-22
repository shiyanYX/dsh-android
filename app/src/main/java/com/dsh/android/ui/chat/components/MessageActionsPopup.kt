package com.dsh.android.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageActionsPopup(
    isUserMessage: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onCopy()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
            }
        )

        if (!isUserMessage) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                onClick = {
                    onRegenerate()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            )
        }

        DropdownMenuItem(
            text = { Text("删除") },
            onClick = {
                onDelete()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
