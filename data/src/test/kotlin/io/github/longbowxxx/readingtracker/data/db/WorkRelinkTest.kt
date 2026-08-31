package io.github.longbowxxx.readingtracker.data.db

import androidx.room.Room
import io.github.longbowxxx.readingtracker.data.db.entity.ReadingRecordEntity
import io.github.longbowxxx.readingtracker.data.db.entity.ShelfPlacementEntity
import io.github.longbowxxx.readingtracker.data.db.entity.StoreEntity
import io.github.longbowxxx.readingtracker.data.db.entity.VolumeEntity
import io.github.longbowxxx.readingtracker.data.db.entity.WorkEntity
import io.github.longbowxxx.readingtracker.data.repository.ReadingRepositoryImpl
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

/**
 * 暫定記録から正式な作品への付け替え（FR-008）。
 *
 * **巻と、その巻を参照する全ての配架レコードの作品 ID が同時に更新され、
 * 不整合が残らないこと**を固定する。片方だけが更新されると、来店時の一覧に
 * 出るはずの作品が消える、あるいは古い暫定作品として残る。
 */
@RunWith(RobolectricTestRunner::class)
class WorkRelinkTest {
    private lateinit var db: ReadingTrackerDatabase
    private lateinit var repository: ReadingRepositoryImpl

    private val now: Instant = Instant.parse("2026-08-31T12:00:00Z")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ReadingTrackerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ReadingRepositoryImpl(
                db.storeDao(),
                db.workDao(),
                db.volumeDao(),
                db.readingRecordDao(),
                db.shelfPlacementDao(),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedProvisional(storeIds: List<Long>): Pair<Long, Long> {
        val workId =
            db.workDao().insert(
                WorkEntity(title = "表紙が青いやつ", matchKey = "表紙が青いやつ", isProvisional = true, createdAt = now),
            )
        val volumeId =
            db.volumeDao().insert(
                VolumeEntity(workId = workId, volumeNumber = null, displayTitle = "表紙が青いやつ", createdAt = now),
            )
        storeIds.forEachIndexed { index, storeId ->
            db.shelfPlacementDao().upsert(
                ShelfPlacementEntity(
                    storeId = storeId,
                    workId = workId,
                    volumeId = volumeId,
                    shelfNumber = "D-0$index",
                    updatedAt = now,
                ),
            )
        }
        db.readingRecordDao().upsert(
            ReadingRecordEntity(volumeId = volumeId, status = ReadingStatus.PAUSED, note = "3話の途中", recordedAt = now),
        )
        return workId to volumeId
    }

    @Test
    fun `巻と配架レコードの作品 ID が同時に更新される`() = runTest {
        val storeId = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val (provisionalWorkId, volumeId) = seedProvisional(listOf(storeId))
        val officialWorkId = db.workDao().insert(WorkEntity(title = "ONE PIECE", matchKey = "ONEPIECE", createdAt = now))

        repository.relinkVolumeToWork(volumeId, officialWorkId)

        assertEquals(officialWorkId, db.volumeDao().findById(volumeId)?.workId)
        assertEquals(officialWorkId, db.shelfPlacementDao().find(storeId, volumeId)?.workId)
        assertTrue(db.shelfPlacementDao().listByStoreAndWork(storeId, provisionalWorkId).isEmpty())
    }

    @Test
    fun `複数店舗の配架レコードがすべて更新される`() = runTest {
        val storeA = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val storeB = db.storeDao().insert(StoreEntity(name = "B店", createdAt = now))
        val (_, volumeId) = seedProvisional(listOf(storeA, storeB))
        val officialWorkId = db.workDao().insert(WorkEntity(title = "ONE PIECE", matchKey = "ONEPIECE", createdAt = now))

        repository.relinkVolumeToWork(volumeId, officialWorkId)

        assertEquals(officialWorkId, db.shelfPlacementDao().find(storeA, volumeId)?.workId)
        assertEquals(officialWorkId, db.shelfPlacementDao().find(storeB, volumeId)?.workId)
    }

    @Test
    fun `付け替えても店舗ごとの棚番号は保たれる`() = runTest {
        val storeA = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val storeB = db.storeDao().insert(StoreEntity(name = "B店", createdAt = now))
        val (_, volumeId) = seedProvisional(listOf(storeA, storeB))
        val officialWorkId = db.workDao().insert(WorkEntity(title = "ONE PIECE", matchKey = "ONEPIECE", createdAt = now))

        repository.relinkVolumeToWork(volumeId, officialWorkId)

        assertEquals("D-00", db.shelfPlacementDao().find(storeA, volumeId)?.shelfNumber)
        assertEquals("D-01", db.shelfPlacementDao().find(storeB, volumeId)?.shelfNumber)
    }

    @Test
    fun `付け替えても読書記録とメモは保たれる`() = runTest {
        val storeId = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val (_, volumeId) = seedProvisional(listOf(storeId))
        val officialWorkId = db.workDao().insert(WorkEntity(title = "ONE PIECE", matchKey = "ONEPIECE", createdAt = now))

        repository.relinkVolumeToWork(volumeId, officialWorkId)

        val record = db.readingRecordDao().findByVolume(volumeId)
        assertEquals(ReadingStatus.PAUSED, record?.status)
        assertEquals("3話の途中", record?.note)
    }

    @Test
    fun `巻の書誌情報を更新しても記録は失われない`() = runTest {
        val storeId = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val (_, volumeId) = seedProvisional(listOf(storeId))

        repository.updateVolumeDetails(
            volumeId = volumeId,
            volumeNumber = 12,
            isbn = Isbn.parse("9784088807232").getOrThrow(),
            displayTitle = "ONE PIECE 12",
            publishedDate = "20171204",
        )

        val volume = db.volumeDao().findById(volumeId)
        assertEquals(12, volume?.volumeNumber)
        assertEquals("9784088807232", volume?.isbn13)
        assertEquals(ReadingStatus.PAUSED, db.readingRecordDao().findByVolume(volumeId)?.status)
        assertEquals("D-00", db.shelfPlacementDao().find(storeId, volumeId)?.shelfNumber)
    }

    @Test
    fun `暫定記録の一覧は暫定作品の巻だけを返す`() = runTest {
        val storeId = db.storeDao().insert(StoreEntity(name = "A店", createdAt = now))
        val (_, provisionalVolumeId) = seedProvisional(listOf(storeId))
        val officialWorkId = db.workDao().insert(WorkEntity(title = "ONE PIECE", matchKey = "ONEPIECE", createdAt = now))
        db.volumeDao().insert(
            VolumeEntity(workId = officialWorkId, volumeNumber = 11, displayTitle = "ONE PIECE 11", createdAt = now),
        )

        val provisional = repository.listProvisionalVolumes()

        assertEquals(1, provisional.size)
        assertEquals(provisionalVolumeId, provisional.single().volumeId)
        assertNull(provisional.single().volumeNumber)
    }
}
