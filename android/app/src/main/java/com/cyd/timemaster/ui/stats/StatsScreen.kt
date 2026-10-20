package com.cyd.timemaster.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.ui.stats.StatsViewModel.Mode
import com.cyd.timemaster.util.formatDuration
import com.cyd.timemaster.util.startOfWeek
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.CHINA)
private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val mode by vm.mode.collectAsState()
    val anchor by vm.anchor.collectAsState()
    val slices by vm.slices.collectAsState()

    val total = slices.sumOf { it.durationMs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // 模式切换
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == Mode.DAY,
                onClick = { vm.setMode(Mode.DAY) },
                label = { Text("日") },
            )
            FilterChip(
                selected = mode == Mode.WEEK,
                onClick = { vm.setMode(Mode.WEEK) },
                label = { Text("周") },
            )
            FilterChip(
                selected = mode == Mode.MONTH,
                onClick = { vm.setMode(Mode.MONTH) },
                label = { Text("月") },
            )
        }

        Spacer(Modifier.height(8.dp))

        // 日期切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.prev() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一时段")
            }
            Text(
                periodTitle(mode, anchor),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一时段")
            }
        }

        if (slices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("该时段还没有计时记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 饼状图
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(vertical = 8.dp),
                factory = { context ->
                    PieChart(context).apply {
                        description.isEnabled = false
                        setUsePercentValues(true)
                        setDrawEntryLabels(false)
                        holeRadius = 58f
                        transparentCircleRadius = 61f
                        setCenterTextSize(15f)
                        legend.textSize = 12f
                        isRotationEnabled = false
                    }
                },
                update = { chart ->
                    val entries = slices.map {
                        PieEntry(it.durationMs / 60_000f, it.categoryName)
                    }
                    val dataSet = PieDataSet(entries, "").apply {
                        colors = slices.map { it.color.toInt() }
                        setDrawValues(false)
                    }
                    chart.data = PieData(dataSet)
                    chart.centerText = formatDuration(total)
                    chart.invalidate()
                },
            )

            Text(
                "合计 ${formatDuration(total)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(0.45f)) {
                items(slices, key = { it.categoryName }) { slice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(Color(slice.color), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(slice.categoryName, modifier = Modifier.weight(1f))
                        Text(
                            formatDuration(slice.durationMs),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${slice.durationMs * 100 / (if (total == 0L) 1 else total)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun periodTitle(mode: Mode, date: LocalDate): String = when (mode) {
    Mode.DAY -> DAY_FMT.format(date)
    Mode.WEEK -> {
        val monday = date.with(java.time.DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val week = date.get(java.time.temporal.WeekFields.of(java.time.DayOfWeek.MONDAY, 1).weekOfWeekBasedYear())
        "第 ${"%02d".format(week)}周（${monday.monthValue}.${monday.dayOfMonth}–${sunday.monthValue}.${sunday.dayOfMonth}）"
    }
    Mode.MONTH -> MONTH_FMT.format(date)
}
