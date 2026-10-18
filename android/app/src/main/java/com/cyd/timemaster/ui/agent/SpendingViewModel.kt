package com.cyd.timemaster.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.remote.ApiClient
import com.cyd.timemaster.data.remote.SpendingResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpendingViewModel : ViewModel() {
    private val _state = MutableStateFlow<SpendingResponse?>(null)
    val state: StateFlow<SpendingResponse?> = _state

    init {
        refresh()
    }

    fun refresh() {
        val settings = AppGraph.settings
        if (!settings.isPaired) return
        AppGraph.scope.launch {
            try {
                _state.value = ApiClient.api(settings.serverBase, settings.token).spending()
            } catch (_: Exception) {
                // 保留上次数据；界面下次刷新再试
            }
        }
    }
}
