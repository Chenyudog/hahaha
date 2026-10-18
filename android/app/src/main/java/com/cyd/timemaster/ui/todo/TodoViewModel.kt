package com.cyd.timemaster.ui.todo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.Todo
import com.cyd.timemaster.reminder.ReminderScheduler
import com.cyd.timemaster.util.localTimeToEpochMillisToday
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import com.cyd.timemaster.util.parseHm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel : ViewModel() {
    private val dao = AppGraph.db.todoDao()

    val todos: StateFlow<List<Todo>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(content: String, remindTime: String?, context: Context) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val remindAt = remindTime?.let { parseHm(it) }?.let { localTimeToEpochMillisToday(it) }
        AppGraph.scope.launch {
            dao.upsert(
                Todo(uuid = newId(), content = trimmed, remindAt = remindAt, updatedAt = nowIso())
            )
            if (remindAt != null) ReminderScheduler.scheduleTodos(context)
        }
    }

    fun toggleDone(todo: Todo) {
        AppGraph.scope.launch {
            if (todo.status == "done") {
                dao.upsert(todo.copy(status = "pending", doneAt = null, updatedAt = nowIso()))
            } else {
                dao.upsert(todo.copy(status = "done", doneAt = System.currentTimeMillis(), updatedAt = nowIso()))
            }
        }
    }

    fun delete(todo: Todo, context: Context) {
        AppGraph.scope.launch {
            dao.delete(todo.uuid)
            // 重排剩余提醒（被删待办的 alarm 自然查不到记录，不再触发）
            ReminderScheduler.scheduleTodos(context)
        }
    }
}
