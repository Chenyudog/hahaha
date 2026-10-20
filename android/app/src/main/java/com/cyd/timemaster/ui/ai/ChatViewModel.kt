package com.cyd.timemaster.ui.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.ai.ChatMessage
import com.cyd.timemaster.ai.DeepSeekClient
import com.cyd.timemaster.ai.DeepSeekException
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.AgentCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * AI 直连对话（v0.8.0）：
 * - NORMAL  普通聊天
 * - ASK_AI  我问 AI：自动把最近学习日志作为上下文
 * - QUIZ    AI 考我：AI 基于日志逐题提问、点评
 * 附加文件内容会拼进 system 上下文。
 */
class ChatViewModel : ViewModel() {
    enum class Mode { NORMAL, ASK_AI, QUIZ }

    data class UiMessage(
        val role: String,            // user / assistant
        val content: String,
        val isError: Boolean = false,
    )

    private val settings = AppGraph.settings
    private val logDao = AppGraph.db.dailyLogDao()
    private val callDao = AppGraph.db.agentCallDao()

    val messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val mode = MutableStateFlow(Mode.NORMAL)
    val busy = MutableStateFlow(false)

    /** 形如 "notes.txt（1234 字）"，null 表示没有附件 */
    val fileInfo = MutableStateFlow<String?>(null)
    private var fileContent: String? = null

    private var history = listOf<ChatMessage>()

    init {
        messages.value = listOf(
            UiMessage(
                "assistant",
                if (settings.aiReady)
                    "我是你的 AI 助手。可以普通聊天；切到「我问AI」我会参考你的学习日志回答；切到「AI考我」我会根据日志出题帮你复习。也可以📎附加一个文本文件让我读。"
                else
                    "还没有配置 AI：请到「我的」页 → AI 直连（DeepSeek）里填入 API Key，填好回来就能用了。"
            )
        )
    }

    fun switchMode(newMode: Mode) {
        if (newMode == mode.value) return
        mode.value = newMode
        history = emptyList()
        messages.value = emptyList()
        when (newMode) {
            Mode.NORMAL -> messages.value = listOf(UiMessage("assistant", "普通聊天模式，随便问。"))
            Mode.ASK_AI -> messages.value = listOf(
                UiMessage("assistant", "已带上你最近 7 天的学习日志，问吧——比如「这周我哪些内容掌握得弱？」")
            )
            Mode.QUIZ -> {
                messages.value = listOf(UiMessage("assistant", "进入考核模式，我会根据你的学习日志逐题提问并点评。"))
                send("开始考我吧")
            }
        }
    }

    fun attachFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { readFile(context, uri) }
            result?.let { (name, text) ->
                fileContent = text
                fileInfo.value = "$name（${text.length} 字）"
                messages.value = messages.value + UiMessage("assistant", "已读取文件 $name（${text.length} 字），接下来的对话会参考它的内容。")
            }
        }
    }

    fun clearFile() {
        fileContent = null
        fileInfo.value = null
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || busy.value) return
        if (!settings.aiReady) {
            messages.value = messages.value + listOf(
                UiMessage("user", text),
                UiMessage("assistant", "还没有配置 AI API Key：去「我的」页 → AI 直连（DeepSeek）填一下。", isError = true),
            )
            return
        }
        messages.value = messages.value + UiMessage("user", text)
        history = history + ChatMessage("user", text)
        busy.value = true

        viewModelScope.launch {
            try {
                val requestMessages = buildRequestMessages()
                val result = DeepSeekClient.chat(settings.aiKey, settings.aiModel, requestMessages)
                history = history + ChatMessage("assistant", result.content)
                messages.value = messages.value + UiMessage("assistant", result.content)
                callDao.insert(
                    AgentCall(
                        ts = System.currentTimeMillis(),
                        model = result.model,
                        promptTokens = result.promptTokens,
                        completionTokens = result.completionTokens,
                        cost = result.costYuan,
                        purpose = when (mode.value) {
                            Mode.NORMAL -> "chat"
                            Mode.ASK_AI -> "ask_ai"
                            Mode.QUIZ -> "quiz"
                        },
                    )
                )
            } catch (e: DeepSeekException) {
                messages.value = messages.value + UiMessage("assistant", e.message ?: "调用失败", isError = true)
            } catch (e: Exception) {
                messages.value = messages.value + UiMessage("assistant", "出错了：${e.message ?: "未知错误"}", isError = true)
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun buildRequestMessages(): List<ChatMessage> {
        val system = when (mode.value) {
            Mode.NORMAL -> "你是简洁实用的中文助手，回答尽量简短。"
            Mode.ASK_AI ->
                "你是复习助手。以下是用户最近的学习日志：\n${recentLogsText()}\n" +
                    "基于日志回答问题，简洁准确；日志未涉及的内容可正常回答，但注明日志未提及。"
            Mode.QUIZ ->
                "你是复习考官。以下是用户最近的学习日志：\n${recentLogsText()}\n" +
                    "规则：每次只出一道基于日志的具体问题；用户回答后先简短点评（对/错及正确要点），再出下一题；不要一次出多道，不要跑题闲聊。"
        }
        val filePart = fileContent?.let { "\n用户附加的文件内容：\n$it" }.orEmpty()
        return listOf(ChatMessage("system", system + filePart)) + history.takeLast(24)
    }

    private suspend fun recentLogsText(): String {
        val since = LocalDate.now().minusDays(7)
        return withContext(Dispatchers.IO) {
            logDao.all()
                .filter { runCatching { LocalDate.parse(it.date) >= since }.getOrDefault(false) }
                .joinToString("\n") { "${it.date}：${it.content}" }
                .take(4000)
                .ifEmpty { "（最近 7 天没有日志，先去日志页写下今天干了什么吧）" }
        }
    }

    private fun readFile(context: Context, uri: Uri): Pair<String, String>? {
        return try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "文件"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (bytes.size > 2_000_000) {
                messages.value = messages.value + UiMessage("assistant", "文件超过 2MB，太大读不了", isError = true)
                return null
            }
            val text = decodeText(bytes)
            if (text.isBlank()) {
                messages.value = messages.value + UiMessage("assistant", "这个文件读出来是空的或不是文本文件", isError = true)
                null
            } else name to text.take(60_000)
        } catch (e: Exception) {
            messages.value = messages.value + UiMessage("assistant", "读文件失败：${e.message}", isError = true)
            null
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        // UTF-8 解码出现大量替换符时按 GBK 再试一次
        return if (utf8.count { it == '\uFFFD' } > 10) String(bytes, charset("GBK")) else utf8
    }
}
