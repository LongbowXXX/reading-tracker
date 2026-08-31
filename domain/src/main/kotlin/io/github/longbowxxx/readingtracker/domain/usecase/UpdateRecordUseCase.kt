package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import java.time.Instant

/** 配架レコードの更新指示。棚番号だけを訂正する場合にも用いる。 */
data class PlacementUpdate(val storeId: Long, val workId: Long, val shelfNumber: ShelfNumber?)

/**
 * 既存記録の更新指示。
 *
 * @property placement 併せて配架レコードを更新する場合に指定する。null なら棚番号は触らない
 */
data class UpdateRecordCommand(
    val volumeId: Long,
    val status: ReadingStatus,
    val note: String? = null,
    val placement: PlacementUpdate? = null,
)

/**
 * 保存済みの記録を更新する（FR-019, FR-029）。
 *
 * 「個室で中断し、次の来店で読み切る」という中核の体験がこの経路を通る。
 * 読書状態の遷移に制限は設けない（READ ⇄ PAUSED のいずれの向きも許す）。
 * 巻内の読了位置は扱わない（FR-011）。
 */
class UpdateRecordUseCase(private val repository: ReadingRepository, private val clock: () -> Instant = Instant::now) {
    suspend fun execute(command: UpdateRecordCommand) {
        val existing = repository.findReading(command.volumeId) ?: return

        repository.upsertReading(
            volumeId = command.volumeId,
            status = command.status,
            note = command.note ?: existing.note,
            recordedAt = clock(),
        )

        command.placement?.let { placement ->
            repository.upsertPlacement(
                storeId = placement.storeId,
                workId = placement.workId,
                volumeId = command.volumeId,
                shelfNumber = placement.shelfNumber,
                updatedAt = clock(),
            )
        }
    }
}
