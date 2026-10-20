package com.cyd.timemaster.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.data.db.Category
import com.cyd.timemaster.data.db.TimeRecord
import com.cyd.timemaster.util.epochToLocalDateTime
import com.cyd.timemaster.util.formatClock
import com.cyd.timemaster.util.formatDuration
import java.time.format.DateTimeFormatter

private val PALETTE = listOf(
    0xFFF44336L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF3F51B5L,
    0xFF1E88E5L, 0xFF009688L, 0xFF4CAF50L, 0xFFFF9800L,
    0xFF795548L, 0xFF607D8BL,
)

private val HM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TimerScreen(vm: TimerViewModel = viewModel()) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsState()
    val running by vm.running.collectAsState()
    val selectedUuid by vm.selectedUuid.collectAsState()
    val records by vm.todayRecords.collectAsState()
    val elapsed by vm.elapsed.collectAsState()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingRecord by remember { mutableStateOf<TimeRecord?>(null) }

    val categoriesMap = categories.associateBy { it.uuid }
    val todayTotal = records.sumOf { ((it.endAt ?: System.currentTimeMillis()) - it.startAt) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editingCategory = null; showCategoryDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建事项")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (running != null) {
                RunningCard(
                    name = running!!.categoryName,
                    color = running!!.categoryColor,
                    elapsedMs = elapsed,
                    onStop = { vm.stop(context) },
                )
            } else {
                Button(
                    onClick = { vm.start(context) },
                    enabled = selectedUuid != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        if (selectedUuid == null) "先选择一个事项" else "开始计时",
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("事项（点按选择，长按编辑）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CategoryChips(
                categories = categories,
                selectedUuid = selectedUuid,
                onSelect = { vm.select(if (selectedUuid == it) null else it) },
                onLongPress = { editingCategory = it; showCategoryDialog = true },
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日记录", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(todayTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (records.isEmpty()) {
                Text(
                    "今天还没有记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                records.sortedByDescending { it.startAt }.forEach { record ->
                    RecordRow(
                        record = record,
                        categoryName = categoriesMap[record.categoryUuid]?.name ?: "(已删除事项)",
                        categoryColor = categoriesMap[record.categoryUuid]?.color,
                        onClick = { editingRecord = record },
                        onDelete = { vm.deleteRecord(record) },
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryDialog(
            initial = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, color ->
                val editing = editingCategory
                if (editing == null) vm.addCategory(name, color)
                else vm.updateCategory(editing, name, color)
                showCategoryDialog = false
            },
            onDelete = {
                editingCategory?.let { vm.deleteCategory(it) }
                showCategoryDialog = false
            },
        )
    }

    editingRecord?.let { record ->
        EndTimeDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onConfirm = { endAt ->
                vm.updateRecordEnd(record, endAt)
                editingRecord = null
            },
        )
    }
}

@Composable
private fun RunningCard(name: String, color: Long, elapsedMs: Long, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(Color(color), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                formatClock(elapsedMs),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("结束计时", fontSize = 16.sp)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedUuid: String?,
    onSelect: (String) -> Unit,
    onLongPress: (Category) -> Unit,
) {
    // FlowRow 不引入额外依赖的简易两列排布
    categories.chunked(3).forEach { rowCategories ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowCategories.forEach { category ->
                val selected = category.uuid == selectedUuid
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = {},
                            onLongClick = { onLongPress(category) },
                        ),
                ) {
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(category.uuid) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(Color(category.color), CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(category.name, maxLines = 1)
                            }
                        },
                    )
                }
            }
            repeat(3 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RecordRow(
    record: TimeRecord,
    categoryName: String,
    categoryColor: Long?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val endText = record.endAt?.let { epochToLocalDateTime(it).format(HM_FMT) } ?: "进行中"
    val duration = formatDuration((record.endAt ?: System.currentTimeMillis()) - record.startAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (categoryColor != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(categoryColor), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(categoryName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${epochToLocalDateTime(record.startAt).format(HM_FMT)} – $endText · $duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除记录")
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: PALETTE.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建事项" else "编辑事项") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("事项名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.take(5).forEach { c -> ColorDot(c, color) { color = c } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.drop(5).forEach { c -> ColorDot(c, color) { color = c } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun ColorDot(color: Long, selected: Long, onSelect: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(color), CircleShape)
            .border(
                width = if (color == selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .clickable { onSelect(color) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndTimeDialog(record: TimeRecord, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val end = record.endAt ?: System.currentTimeMillis()
    val local = epochToLocalDateTime(end)
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改结束时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                val newEnd = local.toLocalDate()
                    .atTime(state.hour, state.minute)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                onConfirm(newEnd)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
