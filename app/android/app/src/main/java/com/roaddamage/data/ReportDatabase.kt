package com.roaddamage.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
class DatabaseConverters { @TypeConverter fun fromSync(value: SyncState) = value.name; @TypeConverter fun toSync(value: String) = SyncState.valueOf(value) }
@Dao interface ReportDao { @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(report: LocalReport); @Query("SELECT * FROM reports WHERE syncState != 'SYNCED'") suspend fun pending(): List<LocalReport>; @Query("UPDATE reports SET syncState=:state WHERE id=:id") suspend fun updateSync(id: String, state: SyncState); @Query("DELETE FROM reports WHERE id=:id") suspend fun deleteById(id: String); @Query("SELECT * FROM reports ORDER BY createdAt DESC") fun observeAll(): Flow<List<LocalReport>> }
@TypeConverters(DatabaseConverters::class) @Database(entities = [LocalReport::class], version = 2) abstract class ReportDatabase : RoomDatabase() {
    abstract fun reports(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: ReportDatabase? = null

        fun getInstance(context: Context): ReportDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReportDatabase::class.java,
                    "road-damage.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

