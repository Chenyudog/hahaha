package com.cyd.timemaster.ui.log

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.cyd.timemaster.util.epochToDateStr
import com.cyd.timemaster.util.rememberThumbnail
import com.cyd.timemaster.util.today

@Composable
fun LogScreen(
    onEditLog: (String) -> Unit,
    vm: LogViewModel = viewModel(key = "log_list", factory = LogViewModel.Factory(null)),
) {
    val logs by vm.logs.collectAsState()
    val imagesByLog by vm.imagesByLog.collectAsState()
    var deleting by remember { mutableStateOf<DailyLog?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditLog("new") }) {
                Icon(Icons.Filled.Add, contentDescription = "写日志")
            }
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有日志，点右下角写下今天干了什么", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(logs, key = { it.uuid }) { log ->
                    LogCard(
                        log = log,
                        imagePaths = (imagesByLog[log.uuid].orEmpty()).map { it.localPath },
                        onClick = { onEditLog(log.uuid) },
                        onDelete = { deleting = log },
                    )
                }
            }
        }
    }

    deleting?.let { log ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条日志？") },
            text = { Text("日志文字与图片会一并删除") },
            confirmButton = {
                TextButton(onClick = { vm.deleteLog(log); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
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
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
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
