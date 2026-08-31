package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.fake.FakeReadingRepository
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.reading.NextVolume
import io.github.longbowxxx.readingtracker.domain.reading.resolveNextVolume
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 記録の更新（FR-029、憲法 原則III「読書状態の遷移」）。
 *
 * 「個室で中断し、次の来店で読み切る」は本アプリの中核的な体験であり、
 * この更新経路が無いと MVP が成立しない。
 */
class UpdateRecordUseCaseTest {
    private lateinit var repository: FakeReadingRepository
    private lateinit var recordUseCase: RecordVolumeUseCase
    private lateinit var updateUseCase: UpdateRecordUseCase

    private val now: Instant = Instant.parse("2026-08-31T10:00:00Z")

    @BeforeEach
    fun setUp() {
        repository = FakeReadingRepository()
        updateUseCase = UpdateRecordUseCase(repository) { now }
        recordUseCase = RecordVolumeUseCase(repository, updateUseCase) { now }
    }

    private suspend fun recordVolume(storeId: Long, title: String, status: ReadingStatus, shelfNumber: String? = null) =
        recordUseCase.execute(
            RecordCommand(
                storeId = storeId,
                isbn = null,
                rawTitle = title,
                status = status,
                shelfNumber = shelfNumber?.let { ShelfNumber(it) },
            ),
        )

    @Test
    @DisplayName("同一店舗で同じ巻を再度記録しても新規作成されない")
    fun `再記録は既存記録の編集になる`() = runTest {
        val store = repository.createStore("A店")
        val first = recordVolume(store.id, "ONE PIECE 13", ReadingStatus.PAUSED, "A-12")

        val second = recordVolume(store.id, "ONE PIECE 13", ReadingStatus.READ, "A-12")

        assertEquals(first.volumeId, second.volumeId)
        assertTrue(second.updatedExistingReading)
        assertEquals(1, repository.readingCount())
    }

    @Test
    fun `中断から読了への更新が次巻判定に反映される`() = runTest {
        val store = repository.createStore("A店")
        recordVolume(store.id, "ONE PIECE 12", ReadingStatus.READ)
        val paused = recordVolume(store.id, "ONE PIECE 13", ReadingStatus.PAUSED)

        // 中断中の巻があるので、それが次に読むべき巻
        assertTrue(resolveNextVolume(repository.listReadingsByWork(paused.workId)) is NextVolume.Paused)

        updateUseCase.execute(UpdateRecordCommand(volumeId = paused.volumeId, status = ReadingStatus.READ))

        // 読み切ったので次は14巻
        assertEquals(
            NextVolume.Next(volumeNumber = 14),
            resolveNextVolume(repository.listReadingsByWork(paused.workId)),
        )
    }

    @Test
    fun `読了から中断への差し戻しもできる`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordVolume(store.id, "ONE PIECE 12", ReadingStatus.READ)

        updateUseCase.execute(UpdateRecordCommand(volumeId = recorded.volumeId, status = ReadingStatus.PAUSED))

        assertEquals(ReadingStatus.PAUSED, repository.findReading(recorded.volumeId)?.status)
    }

    @Test
    fun `メモを更新できる`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordVolume(store.id, "ONE PIECE 13", ReadingStatus.PAUSED)

        updateUseCase.execute(
            UpdateRecordCommand(volumeId = recorded.volumeId, status = ReadingStatus.PAUSED, note = "空島の途中まで"),
        )

        assertEquals("空島の途中まで", repository.findReading(recorded.volumeId)?.note)
    }

    @Test
    fun `棚番号だけを訂正しても読書記録は増えない`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordVolume(store.id, "ONE PIECE 13", ReadingStatus.PAUSED, "A-12")

        updateUseCase.execute(
            UpdateRecordCommand(
                volumeId = recorded.volumeId,
                status = ReadingStatus.PAUSED,
                placement =
                PlacementUpdate(
                    storeId = store.id,
                    workId = recorded.workId,
                    shelfNumber = ShelfNumber("B-03"),
                ),
            ),
        )

        assertEquals(1, repository.readingCount())
        assertEquals(1, repository.placementCount(store.id))
        assertEquals(
            ShelfNumber("B-03"),
            repository.listPlacements(store.id, recorded.workId).single().shelfNumber,
        )
    }

    @Test
    fun `記録が存在しない巻の更新は何もしない`() = runTest {
        updateUseCase.execute(UpdateRecordCommand(volumeId = 999L, status = ReadingStatus.READ))

        assertEquals(0, repository.readingCount())
    }
}
