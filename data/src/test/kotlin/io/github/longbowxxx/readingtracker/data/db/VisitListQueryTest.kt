package io.github.longbowxxx.readingtracker.data.db

import androidx.room.Room
import io.github.longbowxxx.readingtracker.data.db.entity.ReadingRecordEntity
import io.github.longbowxxx.readingtracker.data.db.entity.ShelfPlacementEntity
import io.github.longbowxxx.readingtracker.data.db.entity.StoreEntity
import io.github.longbowxxx.readingtracker.data.db.entity.VolumeEntity
import io.github.longbowxxx.readingtracker.data.db.entity.WorkEntity
import io.github.longbowxxx.readingtracker.data.repository.ReadingRepositoryImpl
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
 * 来店時の一覧のための集約クエリ（B-1, B-2, B-3 / FR-022, FR-024, SC-007）。
 *
 * 店舗をまたいだ混入が起きないことと、棚番号が未入力でも一覧から消えないことを固定する。
 */
@RunWith(RobolectricTestRunner::class)
class VisitListQueryTest {
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

    private suspend fun seedStore(name: String): Long = db.storeDao().insert(StoreEntity(name = name, createdAt = now))

    private suspend fun seedWork(title: String): Long = db.workDao().insert(WorkEntity(title = title, matchKey = title, createdAt = now))

    private suspend fun seedVolume(workId: Long, volumeNumber: Int?): Long = db.volumeDao().insert(
        VolumeEntity(
            workId = workId,
            volumeNumber = volumeNumber,
            displayTitle = "巻 $volumeNumber",
            createdAt = now,
        ),
    )

    private suspend fun place(storeId: Long, workId: Long, volumeId: Long, shelfNumber: String?) {
        db.shelfPlacementDao().upsert(
            ShelfPlacementEntity(
                storeId = storeId,
                workId = workId,
                volumeId = volumeId,
                shelfNumber = shelfNumber,
                updatedAt = now,
            ),
        )
    }

    private suspend fun read(volumeId: Long, status: ReadingStatus) {
        db.readingRecordDao().upsert(ReadingRecordEntity(volumeId = volumeId, status = status, recordedAt = now))
    }

    @Test
    fun `記録が1件も無ければ空を返す`() = runTest {
        val storeId = seedStore("A店")

        assertTrue(repository.listStoreWorkSnapshots(storeId).isEmpty())
    }

    @Test
    fun `選択した店舗の記録だけを返す`() = runTest {
        val storeA = seedStore("A店")
        val storeB = seedStore("B店")
        val workX = seedWork("作品X")
        val workY = seedWork("作品Y")
        val volumeX = seedVolume(workX, 1)
        val volumeY = seedVolume(workY, 1)
        place(storeA, workX, volumeX, "A-12")
        place(storeB, workY, volumeY, "C-07")

        val snapshots = repository.listStoreWorkSnapshots(storeA)

        assertEquals(1, snapshots.size)
        assertEquals("作品X", snapshots.single().workTitle)
    }

    @Test
    fun `他店舗の棚番号が混ざらない`() = runTest {
        val storeA = seedStore("A店")
        val storeB = seedStore("B店")
        val workId = seedWork("作品X")
        val volumeId = seedVolume(workId, 1)
        place(storeA, workId, volumeId, "A-12")
        place(storeB, workId, volumeId, "C-07")

        val placements = repository.listStoreWorkSnapshots(storeA).single().placements

        assertEquals(1, placements.size)
        assertEquals("A-12", placements.single().shelfNumber?.value)
    }

    @Test
    fun `棚番号が未入力の作品も一覧に残る`() = runTest {
        val storeId = seedStore("A店")
        val workId = seedWork("作品X")
        val volumeId = seedVolume(workId, 1)
        place(storeId, workId, volumeId, null)

        val snapshot = repository.listStoreWorkSnapshots(storeId).single()

        assertEquals("作品X", snapshot.workTitle)
        assertNull(snapshot.placements.single().shelfNumber)
    }

    @Test
    fun `巻番号が不明な暫定記録だけの作品も一覧に残る`() = runTest {
        val storeId = seedStore("A店")
        val workId = seedWork("表紙が青いやつ")
        val volumeId = seedVolume(workId, null)
        place(storeId, workId, volumeId, "D-01")
        read(volumeId, ReadingStatus.PAUSED)

        val snapshot = repository.listStoreWorkSnapshots(storeId).single()

        assertNull(snapshot.placements.single().volumeNumber)
        assertEquals(1, snapshot.readings.size)
    }

    @Test
    fun `他店舗で読んだ巻の読書記録も次巻判定のために含める`() = runTest {
        val storeA = seedStore("A店")
        val storeB = seedStore("B店")
        val workId = seedWork("作品X")
        val volume1 = seedVolume(workId, 1)
        val volume2 = seedVolume(workId, 2)

        // A店では1巻だけ配架を記録し、2巻はB店で読んだ
        place(storeA, workId, volume1, "A-12")
        place(storeB, workId, volume2, "C-07")
        read(volume1, ReadingStatus.READ)
        read(volume2, ReadingStatus.READ)

        val snapshot = repository.listStoreWorkSnapshots(storeA).single()

        // 配架はA店の1件のみ。読書記録は作品全体の2件
        assertEquals(1, snapshot.placements.size)
        assertEquals(2, snapshot.readings.size)
    }

    @Test
    fun `複数作品が作品単位でまとまる`() = runTest {
        val storeId = seedStore("A店")
        val workX = seedWork("作品X")
        val workY = seedWork("作品Y")
        place(storeId, workX, seedVolume(workX, 1), "A-12")
        place(storeId, workX, seedVolume(workX, 2), "A-12")
        place(storeId, workY, seedVolume(workY, 1), "C-07")

        val snapshots = repository.listStoreWorkSnapshots(storeId)

        assertEquals(2, snapshots.size)
        assertEquals(2, snapshots.first { it.workTitle == "作品X" }.placements.size)
    }
}
