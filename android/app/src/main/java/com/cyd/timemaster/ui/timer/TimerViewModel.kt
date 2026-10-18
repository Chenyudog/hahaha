package com.cyd.timemaster.ui.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.Category
import com.cyd.timemaster.data.db.TimeRecord
import com.cyd.timemaster.service.TimerService
import com.cyd.timemaster.util.endOfDay
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import com.cyd.timemaster.util.startOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {
    private val recordDao = AppGraph.db.timeRecordDao()
    private val categoryDao = AppGraph.db.categoryDao()

    val categories: StateFlow<List<Category>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val running = TimerService.running

    private val _selectedUuid = MutableStateFlow<String?>(null)
    val selectedUuid: StateFlow<String?> = _selectedUuid

    val todayRecords: StateFlow<List<TimeRecord>> = recordDao
        .observeBetween(startOfDay(), endOfDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 计时中每秒刷新的已计时长（毫秒） */
    val elapsed = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                running.value?.let { elapsed.value = System.currentTimeMillis() - it.startAt }
            }
        }
        viewModelScope.launch {
            running.collect { if (it == null) elapsed.value = 0L }
        }
    }

    fun select(uuid: String?) {
        _selectedUuid.value = uuid
    }

    fun start(context: Context) {
        val category = categories.value.firstOrNull { it.uuid == _selectedUuid.value } ?: return
        TimerService.start(context, category)
    }

    fun stop(context: Context) = TimerService.stop(context)

    fun addCategory(name: String, color: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch {
            categoryDao.upsert(Category(uuid = newId(), name = trimmed, color = color, updatedAt = nowIso()))
        }
    }

    fun updateCategory(category: Category, newName: String, newColor: Long) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch {
            categoryDao.upsert(category.copy(name = trimmed, color = newColor, updatedAt = nowIso()))
        }
    }

    fun deleteCategory(category: Category) {
        AppGraph.scope.launch { categoryDao.delete(category) }
    }

    fun deleteRecord(record: TimeRecord) {
        AppGraph.scope.launch { recordDao.delete(record.uuid) }
    }

    /** 补改结束时间（忘记点结束时） */
    fun updateRecordEnd(record: TimeRecord, endAt: Long) {
        AppGraph.scope.launch {
            recordDao.upsert(record.copy(endAt = endAt, updatedAt = nowIso()))
        }
    }
}
