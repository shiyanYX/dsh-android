package com.dsh.android.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsh.android.domain.model.ToolCall
import com.dsh.android.domain.model.ToolCallStatus

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔧 ${toolCall.toolName}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (toolCall.status) {
                        ToolCallStatus.PENDING -> "待确认"
                        ToolCallStatus.APPROVED -> "已允许"
                        ToolCallStatus.REJECTED -> "已拒绝"
                        ToolCallStatus.COMPLETED -> "已完成"
                        ToolCallStatus.ERROR -> "错误"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show tool arguments
            Text(
                text = toolCall.args.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (toolCall.status == ToolCallStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("拒绝")
                    }
                    Button(onClick = onApprove) {
                        Text("允许")
                    }
                }
            }
        }
    }
}
