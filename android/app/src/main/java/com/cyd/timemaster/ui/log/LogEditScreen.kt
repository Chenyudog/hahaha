package com.cyd.timemaster.ui.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.util.epochToDateStr
import com.cyd.timemaster.util.rememberThumbnail
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEditScreen(logUuid: String?, onDone: () -> Unit) {
    val vm: LogViewModel = viewModel(key = logUuid ?: "new", factory = LogViewModel.Factory(logUuid))
    val context = LocalContext.current
    val date by vm.date.collectAsState()
    val content by vm.content.collectAsState()
    val images by vm.images.collectAsState()
    val reviewState by vm.reviewState.collectAsState()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) vm.addImages(uris, context) }

    var showDatePicker by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.onClose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习日志") },
                navigationIcon = {
                    IconButton(onClick = { vm.save(); onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "选择日期")
                    }
                    TextButton(onClick = { vm.save(); onDone() }) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("日期：$date", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { vm.updateContent(it) },
                placeholder = { Text("今天干了什么？写下来，之后可以让 AI 帮你复习巩固") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.requestReview() },
                enabled = content.isNotBlank() && reviewState !is LogViewModel.ReviewState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(
                    when (reviewState) {
                        is LogViewModel.ReviewState.Loading -> "AI 正在复习巩固…"
                        else -> "AI 复习巩固"
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.uuid }) { image ->
                        val thumb = rememberThumbnail(image.localPath)
                        Box {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = "日志图片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                            TextButton(
                                onClick = { vm.deleteImage(image) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) { Text("×") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("插入图片（可多选）")
            }
        }
    }

    // AI 复习结果弹窗
    when (val state = reviewState) {
        is LogViewModel.ReviewState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissReview() },
            title = { Text("AI 复习巩固") },
            text = {
                Column {
                    Text(state.content)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本次花费 ¥%.4f".format(state.cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissReview() }) { Text("知道了") } },
        )
        is LogViewModel.ReviewState.Error -> AlertDialog(
            onDismissRequest = { vm.dismissReview() },
            title = { Text("出错了") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = { vm.dismissReview() }) { Text("好的") } },
        )
        else -> Unit
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.updateDate(epochToDateStr(it)) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
