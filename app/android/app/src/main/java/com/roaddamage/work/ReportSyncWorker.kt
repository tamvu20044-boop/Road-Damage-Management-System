package com.roaddamage.work
import android.content.Context
import androidx.work.*
import com.roaddamage.BuildConfig
import com.roaddamage.data.ReportDatabase
import com.roaddamage.data.SettingsStore
import com.roaddamage.data.SyncState
import com.roaddamage.network.RoadDamageApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ReportSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ReportSyncHelper.syncAllPending(applicationContext)
        val dao = ReportDatabase.getInstance(applicationContext).reports()
        if (dao.pending().isNotEmpty()) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        fun enqueue(context: Context, wifiOnly: Boolean) {
            val constraints = if (wifiOnly) {
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            } else {
                Constraints.NONE
            }
            val request = OneTimeWorkRequestBuilder<ReportSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("report-sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
