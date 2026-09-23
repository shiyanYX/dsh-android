package com.dsh.android.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.dsh.android.domain.model.Session
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit = {},
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Navigate to newly created session
    LaunchedEffect(uiState.navigateToSession) {
        uiState.navigateToSession?.let { sessionId ->
            viewModel.clearNavigateToSession()
            onSessionClick(sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作区") },
                actions = {
                    // Toggle subagent visibility
                    TextButton(onClick = viewModel::toggleSubagents) {
                        Text(
                            if (uiState.showSubagents) "隐藏子代理" else "显示子代理",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    IconButton(onClick = onFavoritesClick) {
                        Icon(Icons.Default.Favorite, contentDescription = "收藏")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showCreateDialog) {
                Icon(Icons.Default.Add, contentDescription = "新建会话")
            }
        }
    ) { padding ->
        // Create session dialog
        if (uiState.showCreateDialog) {
            CreateSessionDialog(
                workspaces = uiState.availableWorkspaces,
                selectedWorkspace = uiState.selectedWorkspace,
                onSelectWorkspace = viewModel::selectWorkspace,
                onConfirm = viewModel::createSession,
                onDismiss = viewModel::hideCreateDialog
            )
        }

        // Delete session confirmation dialog
        uiState.sessionToDelete?.let { session ->
            AlertDialog(
                onDismissRequest = viewModel::hideDeleteDialog,
                title = { Text("删除会话") },
                text = { Text("确定要删除「${session.title}」吗？\n此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::deleteSession,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideDeleteDialog) { Text("取消") }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("搜索会话...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.workspaceGroups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无会话", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.workspaceGroups.forEach { group ->
                            // Workspace section header
                            stickyHeader {
                                WorkspaceHeader(
                                    name = group.displayName,
                                    sessionCount = group.sessions.size,
                                    cwd = group.cwd
                                )
                            }

                            // Sessions in this workspace
                            items(group.sessions) { session ->
                                SessionItem(
                                    session = session,
                                    onClick = { onSessionClick(session.id) },
                                    onLongClick = {
                                        viewModel.showDeleteDialog(session)
                                    }
                                )
                            }

                            // Spacer between groups
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceHeader(
    name: String,
    sessionCount: Int,
    cwd: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($sessionCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    session: Session,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (session.isSubagent) Modifier.padding(start = 16.dp) else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (session.isSubagent) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sub-agent indicator
            if (session.isSubagent) {
                Icon(
                    Icons.Default.SubdirectoryArrowRight,
                    contentDescription = "子代理",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.running) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (session.isSubagent && session.subagentLabel != null) {
                            session.subagentLabel
                        } else {
                            session.title
                        },
                        style = if (session.isSubagent) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (session.running) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Subtitle: model + turns
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    if (session.model != null) {
                        Text(
                            text = session.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (session.turnCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${session.turnCount}轮",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sub-agent mode badge
                if (session.isSubagent && session.subagentMode != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = session.subagentMode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                // Time
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(session.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CreateSessionDialog(
    workspaces: List<String>,
    selectedWorkspace: String,
    onSelectWorkspace: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var customPath by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column {
                Text(
                    "选择工作区目录：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (workspaces.isNotEmpty()) {
                    workspaces.forEach { path ->
                        val shortName = path.split("/").lastOrNull() ?: path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !useCustom && selectedWorkspace == path,
                                onClick = {
                                    useCustom = false
                                    onSelectWorkspace(path)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shortName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Custom path option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = useCustom,
                        onClick = {
                            useCustom = true
                            onSelectWorkspace(customPath)
                        }
                    )
                    Text("自定义路径", style = MaterialTheme.typography.bodyMedium)
                }

                if (useCustom) {
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = {
                            customPath = it
                            onSelectWorkspace(it)
                        },
                        label = { Text("目录路径") },
                        placeholder = { Text("/home/user/project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedWorkspace.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
