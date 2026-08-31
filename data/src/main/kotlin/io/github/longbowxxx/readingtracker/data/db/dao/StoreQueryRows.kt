package io.github.longbowxxx.readingtracker.data.db.dao

import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import java.time.Instant

/** 来店時の一覧のための配架レコード1行（`ShelfPlacementDao.listStorePlacementRows`）。 */
data class StorePlacementRow(
    val workId: Long,
    val workTitle: String,
    val volumeId: Long,
    val volumeNumber: Int?,
    val shelfNumber: String?,
    val updatedAt: Instant,
)

/** 暫定名のまま残っている巻1行（`VolumeDao.listProvisionalVolumes`）。 */
data class ProvisionalVolumeRow(
    val volumeId: Long,
    val workId: Long,
    val workTitle: String,
    val displayTitle: String,
    val volumeNumber: Int?,
)

/** 来店時の一覧のための読書記録1行（`ReadingRecordDao.listReadingsForStoreWorks`）。 */
data class StoreReadingRow(
    val volumeId: Long,
    val workId: Long,
    val volumeNumber: Int?,
    val status: ReadingStatus,
    val note: String?,
    val recordedAt: Instant,
)
