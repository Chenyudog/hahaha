package com.cyd.timemaster.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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

interface TimeApi {
    @POST("api/v1/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse

    @POST("api/v1/agent/review")
    suspend fun review(@Body body: ReviewRequest): ReviewResponse

    @GET("api/v1/agent/spending")
    suspend fun spending(): SpendingResponse
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
