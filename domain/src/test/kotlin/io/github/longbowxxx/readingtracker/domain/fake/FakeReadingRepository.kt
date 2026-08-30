package io.github.longbowxxx.readingtracker.domain.fake

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.NewVolume
import io.github.longbowxxx.readingtracker.domain.model.NewWork
import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.model.Store
import io.github.longbowxxx.readingtracker.domain.model.Volume
import io.github.longbowxxx.readingtracker.domain.model.Work
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import java.time.Instant

/**
 * ユースケースのテスト用フェイク。インメモリのコレクションで保持し、
 * Android にも Room にも依存しない。
 *
 * 配架レコードは「店舗 × 巻」をキーとして保持する。これは Room 側の
 * UNIQUE(storeId, volumeId) に対応する（data-model.md）。
 */
class FakeReadingRepository : ReadingRepository {
    private val stores = mutableListOf<Store>()
    private val works = mutableListOf<Work>()
    private val volumes = mutableListOf<Volume>()
    private val readings = mutableMapOf<Long, ReadingSnapshot>()
    private val placements = mutableMapOf<PlacementKey, StoredPlacement>()

    private var nextStoreId = 1L
    private var nextWorkId = 1L
    private var nextVolumeId = 1L

    private data class PlacementKey(val storeId: Long, val volumeId: Long)

    private data class StoredPlacement(
        val storeId: Long,
        val workId: Long,
        val volumeId: Long,
        val shelfNumber: ShelfNumber?,
        val updatedAt: Instant,
    )

    override suspend fun listStores(): List<Store> = stores.toList()

    override suspend fun createStore(name: String): Store = Store(id = nextStoreId++, name = name).also { stores += it }

    override suspend fun findWorkByMatchKey(matchKey: String): Work? = works.firstOrNull { it.matchKey == matchKey }

    override suspend fun findWork(workId: Long): Work? = works.firstOrNull { it.id == workId }

    override suspend fun createWork(work: NewWork): Work = Work(
        id = nextWorkId++,
        title = work.title,
        matchKey = work.matchKey,
        author = work.author,
        publisher = work.publisher,
        isProvisional = work.isProvisional,
    ).also { works += it }

    override suspend fun findVolumeByIsbn(isbn: Isbn): Volume? = volumes.firstOrNull { it.isbn == isbn }

    override suspend fun findVolumeByNumber(workId: Long, volumeNumber: Int): Volume? =
        volumes.firstOrNull { it.workId == workId && it.volumeNumber == volumeNumber }

    override suspend fun findVolume(volumeId: Long): Volume? = volumes.firstOrNull { it.id == volumeId }

    override suspend fun createVolume(volume: NewVolume): Volume = Volume(
        id = nextVolumeId++,
        workId = volume.workId,
        volumeNumber = volume.volumeNumber,
        isbn = volume.isbn,
        displayTitle = volume.displayTitle,
        publishedDate = volume.publishedDate,
    ).also { volumes += it }

    override suspend fun relinkVolumeToWork(volumeId: Long, newWorkId: Long) {
        val index = volumes.indexOfFirst { it.id == volumeId }
        if (index < 0) return
        volumes[index] = volumes[index].copy(workId = newWorkId)

        // 配架レコードの作品 ID も同時に更新する（不整合を残さない）
        placements
            .filterValues { it.volumeId == volumeId }
            .forEach { (key, stored) -> placements[key] = stored.copy(workId = newWorkId) }
    }

    override suspend fun findReading(volumeId: Long): ReadingSnapshot? = readings[volumeId]

    override suspend fun listReadingsByWork(workId: Long): List<ReadingSnapshot> {
        val volumeIds = volumes.filter { it.workId == workId }.map { it.id }.toSet()
        return readings.filterKeys { it in volumeIds }.values.toList()
    }

    override suspend fun upsertReading(volumeId: Long, status: ReadingStatus, note: String?, recordedAt: Instant) {
        val volumeNumber = volumes.firstOrNull { it.id == volumeId }?.volumeNumber
        readings[volumeId] =
            ReadingSnapshot(
                volumeId = volumeId,
                volumeNumber = volumeNumber,
                status = status,
                note = note,
                recordedAt = recordedAt,
            )
    }

    override suspend fun listPlacements(storeId: Long, workId: Long): List<PlacementSnapshot> = placements.values
        .filter { it.storeId == storeId && it.workId == workId }
        .map { stored ->
            PlacementSnapshot(
                volumeId = stored.volumeId,
                volumeNumber = volumes.firstOrNull { it.id == stored.volumeId }?.volumeNumber,
                shelfNumber = stored.shelfNumber,
                updatedAt = stored.updatedAt,
            )
        }

    override suspend fun upsertPlacement(storeId: Long, workId: Long, volumeId: Long, shelfNumber: ShelfNumber?, updatedAt: Instant) {
        placements[PlacementKey(storeId, volumeId)] =
            StoredPlacement(
                storeId = storeId,
                workId = workId,
                volumeId = volumeId,
                shelfNumber = shelfNumber,
                updatedAt = updatedAt,
            )
    }

    override suspend fun listWorkIdsInStore(storeId: Long): List<Long> =
        placements.values.filter { it.storeId == storeId }.map { it.workId }.distinct()

    /** テストからの検証用。指定店舗の配架レコード件数。 */
    fun placementCount(storeId: Long): Int = placements.values.count { it.storeId == storeId }

    /** テストからの検証用。読書記録の総件数。 */
    fun readingCount(): Int = readings.size
}
