package com.roaddamage.work

import android.content.Context
import com.roaddamage.BuildConfig
import com.roaddamage.data.LocalReport
import com.roaddamage.data.ReportDatabase
import com.roaddamage.data.SettingsStore
import com.roaddamage.data.SyncState
import com.roaddamage.network.ApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

object ReportSyncHelper {
    suspend fun syncReport(context: Context, report: LocalReport): Boolean = withContext(Dispatchers.IO) {
        val token = SettingsStore(context).token()
        if (token == null) {
            android.util.Log.e("ReportSyncHelper", "Cannot sync: token is null")
            return@withContext false
        }
        val dao = ReportDatabase.getInstance(context).reports()
        val file = File(report.imagePath)
        if (!file.exists()) {
            android.util.Log.e("ReportSyncHelper", "Image file not found: ${report.imagePath}")
            dao.updateSync(report.id, SyncState.FAILED)
            return@withContext false
        }
        val api = ApiFactory.create(BuildConfig.API_BASE_URL)
        fun body(value: String) = value.toRequestBody("text/plain".toMediaTypeOrNull())

        val image = MultipartBody.Part.createFormData(
            "image",
            file.name,
            file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        try {
            android.util.Log.d("ReportSyncHelper", "Uploading report ${report.id} to ${BuildConfig.API_BASE_URL}reports...")
            val response = api.upload(
                "Bearer $token",
                image,
                body(report.latitude.toString()),
                body(report.longitude.toString()),
                body(report.damageType),
                body(report.confidence.toString()),
                report.heading?.let { body(it.toString()) }
            )
            if (response.isSuccessful) {
                android.util.Log.d("ReportSyncHelper", "Upload SUCCESS for ${report.id}!")
                dao.updateSync(report.id, SyncState.SYNCED)
                true
            } else {
                val code = response.code()
                val errBody = response.errorBody()?.string()
                android.util.Log.e("ReportSyncHelper", "Upload FAILED for ${report.id}: HTTP $code, error: $errBody")
                if (code in 400..499 && code != 408) {
                    dao.updateSync(report.id, SyncState.FAILED)
                }
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("ReportSyncHelper", "Upload EXCEPTION for ${report.id}: ${e.message}", e)
            false
        }
    }

    suspend fun syncAllPending(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = ReportDatabase.getInstance(context).reports()
        val pending = dao.pending()
        android.util.Log.d("ReportSyncHelper", "Found ${pending.size} pending reports to sync")
        var count = 0
        for (item in pending) {
            if (syncReport(context, item)) {
                count++
            }
        }
        count
    }
}

