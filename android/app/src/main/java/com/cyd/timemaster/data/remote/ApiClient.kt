package com.cyd.timemaster.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
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

interface TimeApi {
    @POST("api/v1/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse
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
