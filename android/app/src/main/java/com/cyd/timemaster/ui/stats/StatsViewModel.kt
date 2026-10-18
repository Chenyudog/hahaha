package com.cyd.timemaster.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.util.endOfDay
import com.cyd.timemaster.util.startOfDay
import com.cyd.timemaster.util.startOfMonth
import com.cyd.timemaster.util.startOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 一块饼图数据：某事项在统计时段内的累计时长 */
data class StatSlice(val categoryName: String, val color: Long, val durationMs: Long)

class StatsViewModel : ViewModel() {
    enum class Mode { DAY, WEEK, MONTH }

    private val recordDao = AppGraph.db.timeRecordDao()
    private val categoryDao = AppGraph.db.categoryDao()

    private val _mode = MutableStateFlow(Mode.DAY)
    val mode: StateFlow<Mode> = _mode

    private val _anchor = MutableStateFlow(LocalDate.now())
    val anchor: StateFlow<LocalDate> = _anchor

    val slices: StateFlow<List<StatSlice>> =
        combine(_mode, _anchor) { m, d -> periodRange(m, d) }
            .flatMapLatest { (from, to) -> recordDao.observeBetween(from, to) }
            .map { records ->
                val categories = categoryDao.all().associateBy { it.uuid }
                records
                    .map { r ->
                        val end = r.endAt ?: System.currentTimeMillis()
                        Triple(r.categoryUuid, (end - r.startAt).coerceAtLeast(0), end)
                    }
                    .filter { it.second > 0 }
                    .groupBy { it.first }
                    .map { (categoryUuid, items) ->
                        val category = categories[categoryUuid]
                        StatSlice(
                            categoryName = category?.name ?: "(已删除事项)",
                            color = category?.color ?: 0xFF9E9E9EUL,
                            durationMs = items.sumOf { it.second },
                        )
                    }
                    .sortedByDescending { it.durationMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(mode: Mode) {
        _mode.value = mode
    }

    fun prev() {
        val unit = when (_mode.value) {
            Mode.DAY -> java.time.Period.ofDays(1)
            Mode.WEEK -> java.time.Period.ofWeeks(1)
            Mode.MONTH -> java.time.Period.ofMonths(1)
        }
        _anchor.value = _anchor.value.minus(unit)
    }

    fun next() {
        val unit = when (_mode.value) {
            Mode.DAY -> java.time.Period.ofDays(1)
            Mode.WEEK -> java.time.Period.ofWeeks(1)
            Mode.MONTH -> java.time.Period.ofMonths(1)
        }
        _anchor.value = _anchor.value.plus(unit)
    }

    companion object {
        /** 统计时段 [from, to)，按记录开始时间归属 */
        fun periodRange(mode: Mode, date: LocalDate): Pair<Long, Long> = when (mode) {
            Mode.DAY -> startOfDay(date) to endOfDay(date)
            Mode.WEEK -> startOfWeek(date) to startOfWeek(date) + 7 * 24 * 3600_000L
            Mode.MONTH -> startOfMonth(date) to startOfMonth(date.plusMonths(1))
        }
    }
}
