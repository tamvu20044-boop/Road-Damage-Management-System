package com.roaddamage.network
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

data class AuthRequest(val email: String, val password: String)
data class AuthUser(val id: String, val email: String, val role: String)
data class AuthResponse(val accessToken: String, val user: AuthUser)
data class ServerUser(val id: String, val email: String)
data class GeoPoint(val type: String, val coordinates: List<Double>)
data class ServerReport(val id: String, val damageType: String, val confidence: Double, val status: String, val occurrenceCount: Int = 1, val createdAt: String, val imageUrl: String? = null, val location: GeoPoint? = null, val heading: Double? = null, val user: ServerUser? = null)
data class ReportListResponse(val items: List<ServerReport>, val total: Int)
data class ReportUpdateRequest(val status: String? = null, val damageType: String? = null)
typealias ReportStatusRequest = ReportUpdateRequest

interface RoadDamageApi {
    @POST("auth/register") suspend fun register(@Body body: AuthRequest): Response<AuthResponse>
    @POST("auth/login") suspend fun login(@Body body: AuthRequest): Response<AuthResponse>
    @Multipart @POST("reports") suspend fun upload(@Header("Authorization") authorization: String, @Part image: MultipartBody.Part, @Part("lat") lat: RequestBody, @Part("lng") lng: RequestBody, @Part("damageType") damageType: RequestBody, @Part("confidence") confidence: RequestBody, @Part("heading") heading: RequestBody?): Response<Any>
    @GET("reports") suspend fun reports(
        @Header("Authorization") authorization: String,
        @Query("bbox") bbox: String? = null,
        @Query("status") status: String? = null,
        @Query("damageType") damageType: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ReportListResponse>
    @PATCH("reports/{id}/status") suspend fun updateStatus(@Header("Authorization") authorization: String, @Path("id") id: String, @Body body: ReportUpdateRequest): Response<Any>
    @DELETE("reports/{id}") suspend fun deleteReport(@Header("Authorization") authorization: String, @Path("id") id: String): Response<Any>
}

object ApiFactory {
    fun create(baseUrl: String): RoadDamageApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
        .build()
        .create(RoadDamageApi::class.java)
}
