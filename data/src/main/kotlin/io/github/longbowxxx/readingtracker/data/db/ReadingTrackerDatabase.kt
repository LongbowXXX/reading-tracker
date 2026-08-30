package io.github.longbowxxx.readingtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.longbowxxx.readingtracker.data.db.dao.ReadingRecordDao
import io.github.longbowxxx.readingtracker.data.db.dao.ShelfPlacementDao
import io.github.longbowxxx.readingtracker.data.db.dao.StoreDao
import io.github.longbowxxx.readingtracker.data.db.dao.VolumeDao
import io.github.longbowxxx.readingtracker.data.db.dao.WorkDao
import io.github.longbowxxx.readingtracker.data.db.entity.ReadingRecordEntity
import io.github.longbowxxx.readingtracker.data.db.entity.ShelfPlacementEntity
import io.github.longbowxxx.readingtracker.data.db.entity.StoreEntity
import io.github.longbowxxx.readingtracker.data.db.entity.VolumeEntity
import io.github.longbowxxx.readingtracker.data.db.entity.WorkEntity

/**
 * 端末内のデータベース。外部サーバは持たない（憲法 原則V）。
 *
 * スキーマ JSON は `data/schemas/` にエクスポートし、将来のマイグレーション差分の
 * 根拠とする（data-model.md）。
 */
@Database(
    entities = [
        StoreEntity::class,
        WorkEntity::class,
        VolumeEntity::class,
        ReadingRecordEntity::class,
        ShelfPlacementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ReadingTrackerDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    abstract fun workDao(): WorkDao

    abstract fun volumeDao(): VolumeDao

    abstract fun readingRecordDao(): ReadingRecordDao

    abstract fun shelfPlacementDao(): ShelfPlacementDao

    companion object {
        const val DATABASE_NAME = "reading-tracker.db"
    }
}
