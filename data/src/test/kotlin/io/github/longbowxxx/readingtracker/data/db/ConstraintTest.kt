package io.github.longbowxxx.readingtracker.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import io.github.longbowxxx.readingtracker.data.db.entity.ReadingRecordEntity
import io.github.longbowxxx.readingtracker.data.db.entity.ShelfPlacementEntity
import io.github.longbowxxx.readingtracker.data.db.entity.StoreEntity
import io.github.longbowxxx.readingtracker.data.db.entity.VolumeEntity
import io.github.longbowxxx.readingtracker.data.db.entity.WorkEntity
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

/**
 * スキーマ制約の検証（data-model.md）。
 *
 * Room は Android ランタイムを必要とするため、Robolectric 上でインメモリ DB を用いる。
 * ここで固定するのは「棚番号の店舗独立性」と「巻に対する読書記録の一意性」であり、
 * いずれも憲法 原則III が求める性質をスキーマ側で担保していることの確認にあたる。
 */
@RunWith(RobolectricTestRunner::class)
class ConstraintTest {
    private lateinit var db: ReadingTrackerDatabase

    private val now: Instant = Instant.parse("2026-08-30T12:00:00Z")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ReadingTrackerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedWorkWithVolume(volumeNumber: Int = 1): Pair<Long, Long> {
        val workId = db.workDao().insert(WorkEntity(title = "作品X", matchKey = "作品X", createdAt = now))
        val volumeId =
            db.volumeDao().insert(
                VolumeEntity(
                    workId = workId,
                    volumeNumber = volumeNumber,
                    displayTitle = "作品X $volumeNumber",
                    createdAt = now,
                ),
            )
        return workId to volumeId
    }

    private suspend fun seedStore(name: String): Long = db.storeDao().insert(StoreEntity(name = name, createdAt = now))

    @Test
    fun `同一店舗_同一巻の配架レコードは2件作れない`() = runTest {
        val storeId = seedStore("A店")
        val (workId, volumeId) = seedWorkWithVolume()

        db.shelfPlacementDao().insert(
            ShelfPlacementEntity(storeId = storeId, workId = workId, volumeId = volumeId, shelfNumber = "A-12", updatedAt = now),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.shelfPlacementDao().insert(
                    ShelfPlacementEntity(
                        storeId = storeId,
                        workId = workId,
                        volumeId = volumeId,
                        shelfNumber = "B-03",
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    @Test
    fun `同一巻の読書記録は2件作れない`() = runTest {
        val (_, volumeId) = seedWorkWithVolume()

        db.readingRecordDao().insert(
            ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.PAUSED, recordedAt = now),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.readingRecordDao().insert(
                    ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.READ, recordedAt = now),
                )
            }
        }
    }

    @Test
    fun `再記録は既存の読書記録の更新になる`() = runTest {
        val (_, volumeId) = seedWorkWithVolume()

        db.readingRecordDao().upsert(ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.PAUSED, recordedAt = now))
        db.readingRecordDao().upsert(ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.READ, recordedAt = now))

        val record = db.readingRecordDao().findByVolume(volumeId)
        assertNotNull(record)
        assertEquals(ReadingStatus.READ, record?.status)
    }

    @Test
    fun `棚番号が未入力でも配架レコードを保存できる`() = runTest {
        val storeId = seedStore("A店")
        val (workId, volumeId) = seedWorkWithVolume()

        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeId, workId = workId, volumeId = volumeId, shelfNumber = null, updatedAt = now),
        )

        val placement = db.shelfPlacementDao().find(storeId, volumeId)
        assertNotNull(placement)
        assertNull(placement?.shelfNumber)
        // 行の存在が「その店舗でその巻を記録した」事実を表す（FR-024）
        assertEquals(listOf(workId), db.shelfPlacementDao().listWorkIdsInStore(storeId))
    }

    @Test
    fun `A店の配架更新はB店の同一巻に影響しない`() = runTest {
        val storeA = seedStore("A店")
        val storeB = seedStore("B店")
        val (workId, volumeId) = seedWorkWithVolume()

        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeA, workId = workId, volumeId = volumeId, shelfNumber = "A-12", updatedAt = now),
        )
        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeB, workId = workId, volumeId = volumeId, shelfNumber = "C-07", updatedAt = now),
        )

        // A店の棚番号を変更する
        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeA, workId = workId, volumeId = volumeId, shelfNumber = "D-01", updatedAt = now),
        )

        assertEquals("D-01", db.shelfPlacementDao().find(storeA, volumeId)?.shelfNumber)
        assertEquals("C-07", db.shelfPlacementDao().find(storeB, volumeId)?.shelfNumber)
    }

    @Test
    fun `別店舗で同じ巻を記録しても読書記録は1件のまま`() = runTest {
        val storeA = seedStore("A店")
        val storeB = seedStore("B店")
        val (workId, volumeId) = seedWorkWithVolume()

        db.readingRecordDao().upsert(ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.PAUSED, recordedAt = now))
        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeA, workId = workId, volumeId = volumeId, shelfNumber = "A-12", updatedAt = now),
        )

        db.readingRecordDao().upsert(ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.READ, recordedAt = now))
        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(storeId = storeB, workId = workId, volumeId = volumeId, shelfNumber = "C-07", updatedAt = now),
        )

        assertEquals(1, db.readingRecordDao().listByWork(workId).size)
        assertEquals(
            2,
            db.shelfPlacementDao().listByStoreAndWork(storeA, workId).size +
                db.shelfPlacementDao().listByStoreAndWork(storeB, workId).size,
        )
    }

    @Test
    fun `巻数不明の暫定記録は同一作品に複数作れる`() = runTest {
        val workId = db.workDao().insert(WorkEntity(title = "作品Y", matchKey = "作品Y", isProvisional = true, createdAt = now))

        db.volumeDao().insert(VolumeEntity(workId = workId, volumeNumber = null, displayTitle = "作品Y", createdAt = now))
        db.volumeDao().insert(VolumeEntity(workId = workId, volumeNumber = null, displayTitle = "作品Y", createdAt = now))

        assertEquals(2, db.volumeDao().listByWork(workId).size)
    }

    @Test
    fun `同一作品で同じ巻番号は2件作れない`() = runTest {
        val (workId, _) = seedWorkWithVolume(volumeNumber = 3)

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.volumeDao().insert(VolumeEntity(workId = workId, volumeNumber = 3, displayTitle = "作品X 3", createdAt = now))
            }
        }
    }
}
