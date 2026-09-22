package com.dsh.android.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.android.domain.model.Message
import com.dsh.android.domain.model.MessageRole

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isUser: Boolean = message.role == MessageRole.USER,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            val textStyle = if (!isUser && message.content.contains("```")) {
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            } else {
                MaterialTheme.typography.bodyMedium
            }

            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = textStyle
            )
        }
    }
}
