package com.cyd.timemaster.ui.log

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.data.db.DailyLog
import com.cyd.timemaster.data.db.Folder
import com.cyd.timemaster.data.db.FolderFilters
import com.cyd.timemaster.util.epochToDateStr
import com.cyd.timemaster.util.rememberThumbnail
import com.cyd.timemaster.util.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onEditLog: (String, String?) -> Unit,
    vm: LogViewModel = viewModel(key = "log_list", factory = LogViewModel.Factory(null)),
) {
    val folders by vm.folders.collectAsState()
    val logs by vm.logs.collectAsState()
    val filter by vm.filter.collectAsState()
    val visibleLogs by vm.visibleLogs.collectAsState()
    val imagesByLog by vm.imagesByLog.collectAsState()

    var showCreateFolder by remember { mutableStateOf(false) }
    var manageFolder by remember { mutableStateOf<Folder?>(null) }
    var deletingLog by remember { mutableStateOf<DailyLog?>(null) }

    val inFolderView = filter != FolderFilters.ALL

    Scaffold(
        floatingActionButton = {
            if (inFolderView) {
                // 文件夹内：写日志（自动归入当前文件夹/未分类）
                FloatingActionButton(onClick = {
                    onEditLog("new", if (filter == FolderFilters.UNCAT) null else filter)
                }) { Icon(Icons.Filled.Add, contentDescription = "写日志") }
            } else {
                FloatingActionButton(onClick = { showCreateFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (inFolderView) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setFilter(FolderFilters.ALL) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回文件夹列表")
                    }
                    Text(
                        if (filter == FolderFilters.UNCAT) "未分类" else
                            folders.firstOrNull { it.uuid == filter }?.name ?: "文件夹",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.size(4.dp))
                LogList(
                    logs = visibleLogs,
                    imagesByLog = imagesByLog,
                    onEdit = { onEditLog(it.uuid, null) },
                    onDelete = { deletingLog = it },
                    emptyText = "这个文件夹还没有日志，点右下角写一篇",
                )
            } else {
                Text("文件夹", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        FolderCard(
                            name = "未分类",
                            count = logs.count { it.folderUuid == null },
                            icon = Icons.Filled.FolderOpen,
                            onClick = { vm.setFilter(FolderFilters.UNCAT) },
                        )
                    }
                    items(folders, key = { it.uuid }) { folder ->
                        FolderCard(
                            name = folder.name,
                            count = logs.count { it.folderUuid == folder.uuid },
                            icon = Icons.Filled.Folder,
                            onClick = { vm.setFilter(folder.uuid) },
                            onLongClick = { manageFolder = folder },
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        FolderNameDialog(
            title = "新建文件夹",
            initial = "",
            onDismiss = { showCreateFolder = false },
            onConfirm = { vm.createFolder(it); showCreateFolder = false },
        )
    }

    manageFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { manageFolder = null },
            title = { Text("管理「${folder.name}」") },
            text = { Text("可以重命名或删除；删除后里面的日志回到「未分类」，不会丢失") },
            confirmButton = {
                TextButton(onClick = { manageFolder = null; folderRenaming = folder }) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder)
                    manageFolder = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    folderRenaming?.let { folder ->
        FolderNameDialog(
            title = "重命名文件夹",
            initial = folder.name,
            onDismiss = { folderRenaming = null },
            onConfirm = { vm.renameFolder(folder, it); folderRenaming = null },
        )
    }

    deletingLog?.let { log ->
        AlertDialog(
            onDismissRequest = { deletingLog = null },
            title = { Text("删除这条日志？") },
            text = { Text("日志文字与图片会一并删除") },
            confirmButton = {
                TextButton(onClick = { vm.deleteLog(log); deletingLog = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingLog = null }) { Text("取消") } },
        )
    }
}

// 重命名对话框的暂存状态
private var folderRenaming by mutableStateOf<Folder?>(null)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    name: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) Modifier.combinedClickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                else Modifier.clickable(onClick = onClick)
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$count 篇", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogList(
    logs: List<DailyLog>,
    imagesByLog: Map<String, List<com.cyd.timemaster.data.db.LogImage>>,
    onEdit: (DailyLog) -> Unit,
    onDelete: (DailyLog) -> Unit,
    emptyText: String,
) {
    if (logs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(logs, key = { it.uuid }) { log ->
            LogCard(
                log = log,
                imagePaths = (imagesByLog[log.uuid].orEmpty()).map { it.localPath },
                onClick = { onEdit(log) },
                onDelete = { onDelete(log) },
            )
        }
    }
}

@Composable
private fun LogCard(
    log: DailyLog,
    imagePaths: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = when (log.date) {
        today() -> "今天"
        epochToDateStr(System.currentTimeMillis() - 24 * 3600_000L) -> "昨天"
        else -> log.date
    }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除日志")
                }
            }
            if (log.content.isNotBlank()) {
                Text(log.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
            if (imagePaths.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imagePaths) { path ->
                        val thumb = rememberThumbnail(path)
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "日志图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文件夹名称（如 2026-10-20）") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
