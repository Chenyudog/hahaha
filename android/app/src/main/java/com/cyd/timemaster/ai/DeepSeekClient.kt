package com.cyd.timemaster.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 手机直连 DeepSeek（OpenAI 兼容 /chat/completions），不经过电脑服务器 */

@Serializable
data class ChatMessage(val role: String, val content: String)

data class ChatResult(
    val content: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val costYuan: Double,
)

object DeepSeekClient {

    private const val BASE_URL = "https://api.deepseek.com/chat/completions"

    /** 元/百万 token，与服务端一致（deepseek-flash 峰值·缓存未命中折算） */
    const val PRICE_PROMPT_PER_M = 2.13
    const val PRICE_COMPLETION_PER_M = 8.52

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Serializable
    private class DsRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_tokens: Int = 800,
        val temperature: Double = 0.6,
        val stream: Boolean = false,
    )

    @Serializable
    private class DsChoice(val message: ChatMessage? = null)

    @Serializable
    private class DsUsage(
        val prompt_tokens: Int = 0,
        val completion_tokens: Int = 0,
    )

    @Serializable
    private class DsResponse(
        val model: String = "",
        val choices: List<DsChoice> = emptyList(),
        val usage: DsUsage = DsUsage(),
    )

    /** @throws DeepSeekException 网络/鉴权/格式错误时抛出，message 已是可展示中文 */
    suspend fun chat(apiKey: String, model: String, messages: List<ChatMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw DeepSeekException("还没有配置 AI API Key，去「我的」页填写")
            val body = json.encodeToString(
                DsRequest.serializer(),
                DsRequest(model = model.ifBlank { "deepseek-flash" }, messages = messages),
            ).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            val resp = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                throw DeepSeekException("网络错误：${e.message ?: "无法连接 DeepSeek"}（手机需要能上网）")
            }
            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val hint = when (r.code) {
                        401 -> "API Key 无效或已过期"
                        402 -> "DeepSeek 账户余额不足"
                        429 -> "请求太频繁，稍等几秒再试"
                        else -> "DeepSeek 返回 ${r.code}"
                    }
                    throw DeepSeekException("$hint：${text.take(150)}")
                }
                val parsed = try {
                    json.decodeFromString(DsResponse.serializer(), text)
                } catch (e: Exception) {
                    throw DeepSeekException("响应格式异常：${text.take(150)}")
                }
                val content = parsed.choices.firstOrNull()?.message?.content
                    ?: throw DeepSeekException("AI 没有返回内容，请重试")
                val cost = parsed.usage.prompt_tokens / 1e6 * PRICE_PROMPT_PER_M +
                    parsed.usage.completion_tokens / 1e6 * PRICE_COMPLETION_PER_M
                ChatResult(
                    content = content,
                    model = parsed.model.ifBlank { model },
                    promptTokens = parsed.usage.prompt_tokens,
                    completionTokens = parsed.usage.completion_tokens,
                    costYuan = cost,
                )
            }
        }
}

class DeepSeekException(message: String) : Exception(message)
