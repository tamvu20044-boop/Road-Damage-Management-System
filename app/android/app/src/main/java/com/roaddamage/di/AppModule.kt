package com.roaddamage.di
import android.content.Context
import androidx.room.Room
import com.roaddamage.data.ReportDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
    @Provides @Singleton fun db(@ApplicationContext context: Context): ReportDatabase = ReportDatabase.getInstance(context)
    @Provides @Singleton fun reportDao(database: ReportDatabase) = database.reports()
}
