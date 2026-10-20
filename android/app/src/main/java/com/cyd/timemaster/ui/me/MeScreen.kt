package com.cyd.timemaster.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.util.parseHm

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(onOpenSpending: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    var alsoLock by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setWallpaper(it, alsoLock, context) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("服务器与配对", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.serverBase,
            onValueChange = { vm.updateServerBase(it) },
            label = { Text("服务器地址（电脑启动时会显示）") },
            placeholder = { Text("http://192.168.x.x:8000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.pairCode,
            onValueChange = { vm.updatePairCode(it) },
            label = { Text("配对码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.pair() }, modifier = Modifier.weight(1f)) {
                Text(if (vm.paired) "重新配对" else "配对")
            }
        }
        vm.pairingMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("提醒时刻", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TimeRow("晨间待办汇总", vm.morningTime) { timePickerTarget = "morning" }
        TimeRow("晚间写计划提醒", vm.nightlyTime) { timePickerTarget = "nightly" }
        TimeRow("未开始计时提醒", vm.noStartTime) { timePickerTarget = "nostart" }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.timerOverMinutes,
            onValueChange = { vm.updateTimerOverMinutes(it) },
            label = { Text("计时超时提醒阈值（分钟，0 为关闭）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("系统壁纸", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = alsoLock, onCheckedChange = { alsoLock = it })
            Text("同时设置锁屏壁纸")
        }
        Button(
            onClick = {
                wallpaperPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("选择图片设为系统壁纸") }
        vm.wallpaperMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("云端备份", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.backupNow(context) },
                enabled = !vm.backupBusy,
                modifier = Modifier.weight(1f),
            ) { Text("立即备份") }
            OutlinedButton(
                onClick = { showRestoreConfirm = true },
                enabled = !vm.backupBusy,
                modifier = Modifier.weight(1f),
            ) { Text("从云端恢复") }
        }
        vm.backupMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动同步（App 启动与每小时，仅 WiFi）", modifier = Modifier.weight(1f))
            Switch(checked = vm.autoSync, onCheckedChange = { vm.updateAutoSync(it) })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSpending, modifier = Modifier.fillMaxWidth()) {
            Text("AI 花费统计")
        }
        if (!vm.paired) {
            Spacer(Modifier.height(4.dp))
            Text(
                "AI 复习与云端备份需要先完成上面的服务器配对",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从云端恢复？") },
            text = { Text("将用服务器上最近一次备份覆盖手机本地全部数据，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    vm.restore(context)
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            },
        )
    }

    timePickerTarget?.let { target ->
        val current = when (target) {
            "morning" -> vm.morningTime
            "nightly" -> vm.nightlyTime
            else -> vm.noStartTime
        }
        val initial = parseHm(current)
        val state = rememberTimePickerState(
            initialHour = initial?.hour ?: 8,
            initialMinute = initial?.minute ?: 30,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text("选择时刻") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val hm = "%02d:%02d".format(state.hour, state.minute)
                    when (target) {
                        "morning" -> vm.updateMorningTime(hm, context)
                        "nightly" -> vm.updateNightlyTime(hm, context)
                        else -> vm.updateNoStartTime(hm, context)
                    }
                    timePickerTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text(value) }
    }
}
