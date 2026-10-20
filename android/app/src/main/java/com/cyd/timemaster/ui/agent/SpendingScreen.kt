package com.cyd.timemaster.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyd.timemaster.ai.DeepSeekClient
import com.cyd.timemaster.util.epochToLocalDateTime
import java.time.format.DateTimeFormatter

private val TS_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingScreen(onBack: () -> Unit, vm: SpendingViewModel = viewModel()) {
    val s by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 花费") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("今日", s.todayCost, Modifier.weight(1f))
                StatCard("本月", s.monthCost, Modifier.weight(1f))
                StatCard("累计", s.totalCost, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "本机直连 · 共调用 ${s.callsTotal} 次 · 单价：输入 ¥%.2f / 输出 ¥%.2f 每百万 token"
                    .format(DeepSeekClient.PRICE_PROMPT_PER_M, DeepSeekClient.PRICE_COMPLETION_PER_M),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("最近调用", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (s.items.isEmpty()) {
                Text(
                    "还没有调用记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(s.items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    epochToLocalDateTime(item.ts).format(TS_FMT) + " · " + purposeName(item.purpose),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${item.model} · 输入 ${item.promptTokens} + 输出 ${item.completionTokens} token",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("¥%.4f".format(item.cost), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun purposeName(purpose: String): String = when (purpose) {
    "chat" -> "普通对话"
    "ask_ai" -> "我问AI"
    "quiz" -> "AI考我"
    "daily_review" -> "日志复习"
    else -> purpose
}

@Composable
private fun StatCard(label: String, value: Double, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("¥%.2f".format(value), style = MaterialTheme.typography.titleLarge)
        }
    }
}
