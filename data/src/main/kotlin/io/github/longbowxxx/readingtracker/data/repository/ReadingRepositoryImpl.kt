package io.github.longbowxxx.readingtracker.data.repository

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
import io.github.longbowxxx.readingtracker.domain.port.StoreWorkSnapshot
import java.time.Instant
import javax.inject.Inject

/**
 * Room を用いた [ReadingRepository] の実装。DAO を束ね、ドメインの型へ変換する。
 *
 * ドメイン層は Room を知らない。エンティティとドメインモデルの相互変換はこの層で閉じる。
 */
class ReadingRepositoryImpl
@Inject
constructor(
    private val storeDao: StoreDao,
    private val workDao: WorkDao,
    private val volumeDao: VolumeDao,
    private val readingRecordDao: ReadingRecordDao,
    private val shelfPlacementDao: ShelfPlacementDao,
) : ReadingRepository {
    override suspend fun listStores(): List<Store> = storeDao.listAll().map { Store(id = it.id, name = it.name) }

    override suspend fun createStore(name: String): Store {
        val id = storeDao.insert(StoreEntity(name = name, createdAt = Instant.now()))
        return Store(id = id, name = name)
    }

    override suspend fun findWorkByMatchKey(matchKey: String): Work? = workDao.findByMatchKey(matchKey)?.toDomain()

    override suspend fun findWork(workId: Long): Work? = workDao.findById(workId)?.toDomain()

    override suspend fun createWork(work: NewWork): Work {
        val entity =
            WorkEntity(
                title = work.title,
                matchKey = work.matchKey,
                author = work.author,
                publisher = work.publisher,
                isProvisional = work.isProvisional,
                createdAt = Instant.now(),
            )
        val id = workDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun findVolumeByIsbn(isbn: Isbn): Volume? = volumeDao.findByIsbn(isbn.value)?.toDomain()

    override suspend fun findVolumeByNumber(workId: Long, volumeNumber: Int): Volume? =
        volumeDao.findByNumber(workId, volumeNumber)?.toDomain()

    override suspend fun findVolume(volumeId: Long): Volume? = volumeDao.findById(volumeId)?.toDomain()

    override suspend fun createVolume(volume: NewVolume): Volume {
        val entity =
            VolumeEntity(
                workId = volume.workId,
                volumeNumber = volume.volumeNumber,
                isbn13 = volume.isbn?.value,
                displayTitle = volume.displayTitle,
                publishedDate = volume.publishedDate,
                createdAt = Instant.now(),
            )
        val id = volumeDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun relinkVolumeToWork(volumeId: Long, newWorkId: Long) {
        workDao.relinkVolumeToWork(volumeId, newWorkId)
    }

    override suspend fun findReading(volumeId: Long): ReadingSnapshot? {
        val record = readingRecordDao.findByVolume(volumeId) ?: return null
        return record.toDomain(volumeDao.findById(volumeId)?.volumeNumber)
    }

    override suspend fun listReadingsByWork(workId: Long): List<ReadingSnapshot> {
        val volumeNumbers = volumeDao.listByWork(workId).associate { it.id to it.volumeNumber }
        return readingRecordDao.listByWork(workId).map { it.toDomain(volumeNumbers[it.volumeId]) }
    }

    override suspend fun upsertReading(volumeId: Long, status: ReadingStatus, note: String?, recordedAt: Instant) {
        readingRecordDao.upsert(
            ReadingRecordEntity(volumeId = volumeId, status = status, note = note, recordedAt = recordedAt),
        )
    }

    override suspend fun listPlacements(storeId: Long, workId: Long): List<PlacementSnapshot> {
        val volumeNumbers = volumeDao.listByWork(workId).associate { it.id to it.volumeNumber }
        return shelfPlacementDao.listByStoreAndWork(storeId, workId).map { entity ->
            PlacementSnapshot(
                volumeId = entity.volumeId,
                volumeNumber = volumeNumbers[entity.volumeId],
                shelfNumber = entity.shelfNumber?.let(::ShelfNumber),
                updatedAt = entity.updatedAt,
            )
        }
    }

    override suspend fun upsertPlacement(storeId: Long, workId: Long, volumeId: Long, shelfNumber: ShelfNumber?, updatedAt: Instant) {
        shelfPlacementDao.upsert(
            ShelfPlacementEntity(
                storeId = storeId,
                workId = workId,
                volumeId = volumeId,
                shelfNumber = shelfNumber?.value,
                updatedAt = updatedAt,
            ),
        )
    }

    override suspend fun listWorkIdsInStore(storeId: Long): List<Long> = shelfPlacementDao.listWorkIdsInStore(storeId)

    override suspend fun listStoreWorkSnapshots(storeId: Long): List<StoreWorkSnapshot> {
        // 2回の問い合わせで済ませる。作品ごとに引くと件数に比例して往復が増えるため
        val placementRows = shelfPlacementDao.listStorePlacementRows(storeId)
        val readingRows = readingRecordDao.listReadingsForStoreWorks(storeId)

        val readingsByWork =
            readingRows.groupBy(
                keySelector = { it.workId },
                valueTransform = {
                    ReadingSnapshot(
                        volumeId = it.volumeId,
                        volumeNumber = it.volumeNumber,
                        status = it.status,
                        note = it.note,
                        recordedAt = it.recordedAt,
                    )
                },
            )

        return placementRows
            .groupBy { it.workId }
            .map { (workId, rows) ->
                StoreWorkSnapshot(
                    workId = workId,
                    workTitle = rows.first().workTitle,
                    placements =
                    rows.map { row ->
                        PlacementSnapshot(
                            volumeId = row.volumeId,
                            volumeNumber = row.volumeNumber,
                            shelfNumber = row.shelfNumber?.let(::ShelfNumber),
                            updatedAt = row.updatedAt,
                        )
                    },
                    readings = readingsByWork[workId].orEmpty(),
                )
            }
    }
}

private fun WorkEntity.toDomain() = Work(
    id = id,
    title = title,
    matchKey = matchKey,
    author = author,
    publisher = publisher,
    isProvisional = isProvisional,
)

private fun VolumeEntity.toDomain() = Volume(
    id = id,
    workId = workId,
    volumeNumber = volumeNumber,
    // 保存済みの値は登録時に検証済みのため、復元に失敗した場合は null として扱う
    isbn = isbn13?.let { Isbn.parse(it).getOrNull() },
    displayTitle = displayTitle,
    publishedDate = publishedDate,
)

private fun ReadingRecordEntity.toDomain(volumeNumber: Int?) = ReadingSnapshot(
    volumeId = volumeId,
    volumeNumber = volumeNumber,
    status = status,
    note = note,
    recordedAt = recordedAt,
)
