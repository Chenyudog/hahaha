package com.cyd.timemaster.ui.todo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.Folder
import com.cyd.timemaster.data.db.FolderFilters
import com.cyd.timemaster.data.db.Todo
import com.cyd.timemaster.reminder.ReminderScheduler
import com.cyd.timemaster.util.localTimeToEpochMillisToday
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import com.cyd.timemaster.util.parseHm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel : ViewModel() {
    private val dao = AppGraph.db.todoDao()
    private val folderDao = AppGraph.db.folderDao()

    val folders: StateFlow<List<Folder>> = folderDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow(FolderFilters.ALL)
    val todos: StateFlow<List<Todo>> = filter
        .flatMapLatest { f ->
            when (f) {
                FolderFilters.ALL -> dao.observeAll()
                FolderFilters.UNCAT -> dao.observeUncategorized()
                else -> dao.observeInFolder(f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: String) {
        filter.value = f
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch { folderDao.upsert(Folder(uuid = newId(), name = trimmed, createdAt = nowIso())) }
    }

    fun renameFolder(folder: Folder, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch { folderDao.upsert(folder.copy(name = trimmed)) }
    }

    /** 删除文件夹：里面待办回到未分类，不删内容 */
    fun deleteFolder(folder: Folder) {
        AppGraph.scope.launch {
            dao.clearFolder(folder.uuid)
            AppGraph.db.dailyLogDao().clearFolder(folder.uuid)
            folderDao.delete(folder.uuid)
        }
    }

    fun add(content: String, remindTime: String?, folderUuid: String?, context: Context) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val remindAt = remindTime?.let { parseHm(it) }?.let { localTimeToEpochMillisToday(it) }
        AppGraph.scope.launch {
            dao.upsert(
                Todo(
                    uuid = newId(), content = trimmed, remindAt = remindAt,
                    updatedAt = nowIso(), folderUuid = folderUuid,
                )
            )
            if (remindAt != null) ReminderScheduler.scheduleTodos(context)
        }
    }

    fun update(todo: Todo, content: String, remindTime: String?, folderUuid: String?, context: Context) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val remindAt = remindTime?.let { parseHm(it) }?.let { localTimeToEpochMillisToday(it) }
        AppGraph.scope.launch {
            dao.upsert(
                todo.copy(content = trimmed, remindAt = remindAt, folderUuid = folderUuid, updatedAt = nowIso())
            )
            ReminderScheduler.scheduleTodos(context)
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
            ReminderScheduler.scheduleTodos(context)
        }
    }
}
