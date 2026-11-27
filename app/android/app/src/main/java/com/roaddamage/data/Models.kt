package com.roaddamage.data
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncState { PENDING_SYNC, SYNCED, FAILED }
@Entity(tableName = "reports") data class LocalReport(@PrimaryKey val id: String, val damageType: String, val confidence: Float, val latitude: Double, val longitude: Double, val heading: Float?, val imagePath: String, val boxesJson: String, val createdAt: Long, val syncState: SyncState = SyncState.PENDING_SYNC)
data class Detection(val label: String, val confidence: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)
data class AppSettings(val autoScan: Boolean = true, val intervalMs: Long = 1500, val confidenceThreshold: Float = .28f, val wifiOnly: Boolean = false)
