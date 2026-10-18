package com.cyd.timemaster.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResponse(
    val token: String,
    @SerialName("server_version") val serverVersion: String = "",
)

@Serializable
data class CountOk(val ok: Boolean = false)

@Serializable
data class ReviewRequest(val text: String, val purpose: String = "daily_review")

@Serializable
data class UsageInfo(
    val model: String = "",
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
)

@Serializable
data class ReviewResponse(
    val content: String,
    val model: String = "",
    val cost: Double = 0.0,
    val usage: UsageInfo = UsageInfo(),
)

@Serializable
data class SpendingItem(
    val id: Int,
    val ts: String,
    val model: String = "",
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    val cost: Double = 0.0,
    val purpose: String = "",
)

@Serializable
data class SpendingResponse(
    @SerialName("calls_total") val callsTotal: Int = 0,
    @SerialName("total_cost") val totalCost: Double = 0.0,
    @SerialName("today_cost") val todayCost: Double = 0.0,
    @SerialName("month_cost") val monthCost: Double = 0.0,
    @SerialName("price_prompt_per_m") val pricePromptPerM: Double = 0.0,
    @SerialName("price_completion_per_m") val priceCompletionPerM: Double = 0.0,
    val items: List<SpendingItem> = emptyList(),
)

// ---- 同步 / 备份 DTO：时间戳与颜色以字符串传输，客户端负责与 Long 互转 ----

@Serializable
data class CategoryDto(
    val uuid: String,
    val name: String = "",
    val color: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TimeRecordDto(
    val uuid: String,
    @SerialName("category_uuid") val categoryUuid: String? = null,
    @SerialName("start_at") val startAt: String = "0",
    @SerialName("end_at") val endAt: String? = null,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DailyLogDto(
    val uuid: String,
    val date: String = "",
    val content: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LogImageDto(
    val uuid: String,
    @SerialName("log_uuid") val logUuid: String? = null,
    @SerialName("remote_id") val remoteId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TodoDto(
    val uuid: String,
    val content: String = "",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("remind_at") val remindAt: String? = null,
    val status: String = "pending",
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Snapshot(
    val categories: List<CategoryDto> = emptyList(),
    @SerialName("time_records") val timeRecords: List<TimeRecordDto> = emptyList(),
    @SerialName("daily_logs") val dailyLogs: List<DailyLogDto> = emptyList(),
    @SerialName("log_images") val logImages: List<LogImageDto> = emptyList(),
    val todos: List<TodoDto> = emptyList(),
)

@Serializable
data class SyncResponse(val ok: Boolean = false)

@Serializable
data class BackupAck(val ok: Boolean = false, val file: String = "", val ts: String = "")

@Serializable
data class RestoreResponse(val file: String = "", val data: Snapshot = Snapshot())

@Serializable
data class ImageUploadResponse(val id: String = "", val url: String = "")

interface TimeApi {
    @POST("api/v1/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse

    @POST("api/v1/agent/review")
    suspend fun review(@Body body: ReviewRequest): ReviewResponse

    @GET("api/v1/agent/spending")
    suspend fun spending(): SpendingResponse

    @POST("api/v1/sync")
    suspend fun sync(@Body snapshot: Snapshot): SyncResponse

    @POST("api/v1/backup/full")
    suspend fun backupFull(@Body snapshot: Snapshot): BackupAck

    @POST("api/v1/restore")
    suspend fun restore(): RestoreResponse

    @Multipart
    @POST("api/v1/images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ImageUploadResponse

    @Streaming
    @GET("api/v1/images/{id}")
    suspend fun fetchImage(@Path("id") id: String): ResponseBody
}

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun api(base: String, token: String? = null): TimeApi {
        val client = OkHttpClient.Builder()
            .apply {
                if (!token.isNullOrEmpty()) {
                    addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", "Bearer $token")
                                .build()
                        )
                    }
                }
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val base_ = if (base.endsWith("/")) base else "$base/"
        return Retrofit.Builder()
            .baseUrl(base_)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TimeApi::class.java)
    }
}
