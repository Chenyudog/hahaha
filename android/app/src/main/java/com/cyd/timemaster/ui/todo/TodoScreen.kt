package com.cyd.timemaster.ui.todo

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.data.db.Folder
import com.cyd.timemaster.data.db.FolderFilters
import com.cyd.timemaster.data.db.Todo
import com.cyd.timemaster.util.epochToLocalDateTime
import com.cyd.timemaster.util.parseHm
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(vm: TodoViewModel = viewModel()) {
    val context = LocalContext.current
    val todos by vm.todos.collectAsState()
    val folders by vm.folders.collectAsState()
    val filter by vm.filter.collectAsState()

    var editingTodo by remember { mutableStateOf<Todo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var manageFolder by remember { mutableStateOf<Folder?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建待办")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
        ) {
            // 文件夹过滤条
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filter == FolderFilters.ALL,
                        onClick = { vm.setFilter(FolderFilters.ALL) },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = filter == FolderFilters.UNCAT,
                        onClick = { vm.setFilter(FolderFilters.UNCAT) },
                        label = { Text("未分类") },
                    )
                }
                items(folders, key = { it.uuid }) { folder ->
                    FilterChip(
                        selected = filter == folder.uuid,
                        onClick = { vm.setFilter(folder.uuid) },
                        label = { Text(folder.name, maxLines = 1) },
                        modifier = Modifier.combinedClickableChip { manageFolder = folder },
                    )
                }
                item {
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
                    }
                }
            }

            if (todos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("这里没有待办，点右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(todos, key = { it.uuid }) { todo ->
                        TodoRow(
                            todo = todo,
                            onClick = { editingTodo = todo },
                            onToggle = { vm.toggleDone(todo) },
                            onDelete = { vm.delete(todo, context) },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TodoEditDialog(
            title = "新建待办",
            initial = null,
            folders = folders,
            defaultFolderUuid = if (filter == FolderFilters.ALL || filter == FolderFilters.UNCAT) null else filter,
            onDismiss = { creating = false },
            onConfirm = { content, remind, folder ->
                vm.add(content, remind, folder, context)
                creating = false
            },
        )
    }

    editingTodo?.let { todo ->
        TodoEditDialog(
            title = "编辑待办",
            initial = todo,
            folders = folders,
            defaultFolderUuid = todo.folderUuid,
            onDismiss = { editingTodo = null },
            onConfirm = { content, remind, folder ->
                vm.update(todo, content, remind, folder, context)
                editingTodo = null
            },
        )
    }

    if (showCreateFolder) {
        FolderNameDialog(
            title = "新建文件夹",
            onDismiss = { showCreateFolder = false },
            onConfirm = { vm.createFolder(it); showCreateFolder = false },
        )
    }

    manageFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { manageFolder = null },
            title = { Text("管理「${folder.name}」") },
            text = { Text("可以重命名或删除；删除后里面的待办回到「未分类」，不会丢失") },
            confirmButton = {
                TextButton(onClick = {
                    manageFolder = null
                    folderRenamingTarget = folder
                }) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { vm.deleteFolder(folder); manageFolder = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    folderRenamingTarget?.let { folder ->
        FolderNameDialog(
            title = "重命名文件夹",
            initial = folder.name,
            onDismiss = { folderRenamingTarget = null },
            onConfirm = { vm.renameFolder(folder, it); folderRenamingTarget = null },
        )
    }
}

// 重命名对话框暂存
private var folderRenamingTarget by mutableStateOf<Folder?>(null)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableChip(onLongClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = {},
        onLongClick = onLongClick,
    ).let { this.then(it) }

@Composable
private fun TodoRow(todo: Todo, onClick: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val done = todo.status == "done"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
            ) {
                Text(
                    todo.content,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                todo.remindAt?.let {
                    Text(
                        "提醒：${epochToLocalDateTime(it).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除待办")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditDialog(
    title: String,
    initial: Todo?,
    folders: List<Folder>,
    defaultFolderUuid: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit,
) {
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var remindEnabled by remember { mutableStateOf(initial?.remindAt != null) }
    var remindTime by remember {
        mutableStateOf(
            initial?.remindAt?.let {
                epochToLocalDateTime(it).format(DateTimeFormatter.ofPattern("HH:mm"))
            } ?: "09:00"
        )
    }
    var folderUuid by remember { mutableStateOf(initial?.folderUuid ?: defaultFolderUuid) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("要做的事") },
                    singleLine = true,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text("归属文件夹", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = folderUuid == null,
                            onClick = { folderUuid = null },
                            label = { Text("未分类") },
                        )
                    }
                    items(folders, key = { it.uuid }) { folder ->
                        FilterChip(
                            selected = folderUuid == folder.uuid,
                            onClick = { folderUuid = folder.uuid },
                            label = { Text(folder.name, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("到点提醒我", modifier = Modifier.weight(1f))
                    Switch(checked = remindEnabled, onCheckedChange = { remindEnabled = it })
                }
                if (remindEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒时刻：$remindTime", modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTimePicker = true }) { Text("修改") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(content, if (remindEnabled) remindTime else null, folderUuid) },
                enabled = content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showTimePicker) {
        val initialTime = parseHm(remindTime)
        val state = rememberTimePickerState(
            initialHour = initialTime?.hour ?: 9,
            initialMinute = initialTime?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("提醒时刻") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    remindTime = "%02d:%02d".format(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String = "",
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
                label = { Text("文件夹名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
