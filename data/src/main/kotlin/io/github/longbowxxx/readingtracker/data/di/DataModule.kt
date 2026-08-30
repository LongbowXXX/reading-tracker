package io.github.longbowxxx.readingtracker.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.longbowxxx.readingtracker.data.db.ReadingTrackerDatabase
import io.github.longbowxxx.readingtracker.data.db.dao.ReadingRecordDao
import io.github.longbowxxx.readingtracker.data.db.dao.ShelfPlacementDao
import io.github.longbowxxx.readingtracker.data.db.dao.StoreDao
import io.github.longbowxxx.readingtracker.data.db.dao.VolumeDao
import io.github.longbowxxx.readingtracker.data.db.dao.WorkDao
import io.github.longbowxxx.readingtracker.data.repository.ReadingRepositoryImpl
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import javax.inject.Singleton

/**
 * データ層の依存を提供する Hilt モジュール。
 *
 * 上位（UI・ドメイン）はインターフェースだけを受け取り、Room の存在を知らない。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadingTrackerDatabase = Room
        .databaseBuilder(context, ReadingTrackerDatabase::class.java, ReadingTrackerDatabase.DATABASE_NAME)
        .build()

    @Provides
    fun provideStoreDao(database: ReadingTrackerDatabase): StoreDao = database.storeDao()

    @Provides
    fun provideWorkDao(database: ReadingTrackerDatabase): WorkDao = database.workDao()

    @Provides
    fun provideVolumeDao(database: ReadingTrackerDatabase): VolumeDao = database.volumeDao()

    @Provides
    fun provideReadingRecordDao(database: ReadingTrackerDatabase): ReadingRecordDao = database.readingRecordDao()

    @Provides
    fun provideShelfPlacementDao(database: ReadingTrackerDatabase): ShelfPlacementDao = database.shelfPlacementDao()

    @Provides
    @Singleton
    fun provideReadingRepository(impl: ReadingRepositoryImpl): ReadingRepository = impl
}
