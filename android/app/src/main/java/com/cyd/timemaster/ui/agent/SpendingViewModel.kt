package com.cyd.timemaster.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.ai.DeepSeekClient
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.AgentCall
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** v0.8.0 起 AI 手机直连，花费从本地 agent_calls 表统计（不再查电脑服务器） */
class SpendingViewModel : ViewModel() {

    data class UiState(
        val callsTotal: Int = 0,
        val totalCost: Double = 0.0,
        val todayCost: Double = 0.0,
        val monthCost: Double = 0.0,
        val items: List<AgentCall> = emptyList(),
    )

    val state: StateFlow<UiState> = AppGraph.db.agentCallDao().observeAll()
        .map { calls ->
            val dayStart = com.cyd.timemaster.util.startOfDay()
            val monthStart = com.cyd.timemaster.util.startOfMonth()
            UiState(
                callsTotal = calls.size,
                totalCost = calls.sumOf { it.cost },
                todayCost = calls.filter { it.ts >= dayStart }.sumOf { it.cost },
                monthCost = calls.filter { it.ts >= monthStart }.sumOf { it.cost },
                items = calls.take(50),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
