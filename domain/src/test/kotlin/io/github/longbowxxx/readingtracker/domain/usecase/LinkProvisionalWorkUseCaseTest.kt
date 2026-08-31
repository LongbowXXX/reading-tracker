package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.fake.FakeReadingRepository
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 暫定記録の正式な作品への紐づけ（User Story 3 / FR-008）。
 *
 * **紐づけによって読書状態・棚番号・メモを失わないこと**が要求の核。
 * 巻の ID を変えずに作品だけを付け替えることで満たす。
 */
class LinkProvisionalWorkUseCaseTest {
    private lateinit var repository: FakeReadingRepository
    private lateinit var recordUseCase: RecordVolumeUseCase
    private lateinit var useCase: LinkProvisionalWorkUseCase

    private val now: Instant = Instant.parse("2026-08-31T10:00:00Z")

    @BeforeEach
    fun setUp() {
        repository = FakeReadingRepository()
        recordUseCase = RecordVolumeUseCase(repository, UpdateRecordUseCase(repository) { now }) { now }
        useCase = LinkProvisionalWorkUseCase(repository)
    }

    private suspend fun recordProvisional(storeId: Long, title: String, shelfNumber: String? = "D-01", note: String? = "3話の途中") =
        recordUseCase.execute(
            RecordCommand(
                storeId = storeId,
                isbn = null,
                rawTitle = title,
                status = ReadingStatus.PAUSED,
                shelfNumber = shelfNumber?.let { ShelfNumber(it) },
                note = note,
                isProvisional = true,
            ),
        )

    @Test
    @DisplayName("紐づけても読書状態・棚番号・メモが失われない")
    fun `記録内容が引き継がれる`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")

        val result =
            useCase.execute(
                LinkProvisionalWorkCommand(
                    volumeId = recorded.volumeId,
                    isbn = Isbn.parse("9784088807232").getOrThrow(),
                    rawTitle = "ONE PIECE 12",
                ),
            )

        val linked = checkNotNull(result)
        // 巻の ID は変わらない。これが記録を失わないことの担保
        assertEquals(recorded.volumeId, linked.volumeId)
        assertEquals(ReadingStatus.PAUSED, repository.findReading(linked.volumeId)?.status)
        assertEquals("3話の途中", repository.findReading(linked.volumeId)?.note)
        assertEquals(
            ShelfNumber("D-01"),
            repository.listPlacements(store.id, linked.workId).single().shelfNumber,
        )
    }

    @Test
    fun `作品が付け替わる`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")

        val linked = checkNotNull(useCase.execute(LinkProvisionalWorkCommand(recorded.volumeId, null, "ONE PIECE 12")))

        assertNotEquals(recorded.workId, linked.workId)
        assertEquals("ONE PIECE", repository.findWork(linked.workId)?.title)
    }

    @Test
    fun `紐づけ先は暫定ではない作品になる`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")

        val linked = checkNotNull(useCase.execute(LinkProvisionalWorkCommand(recorded.volumeId, null, "ONE PIECE 12")))

        assertFalse(repository.findWork(linked.workId)?.isProvisional ?: true)
    }

    @Test
    fun `既に同じ作品があればそこへ吸収される`() = runTest {
        val store = repository.createStore("A店")
        val existing =
            recordUseCase.execute(
                RecordCommand(
                    storeId = store.id,
                    isbn = null,
                    rawTitle = "ONE PIECE 11",
                    status = ReadingStatus.READ,
                    shelfNumber = ShelfNumber("A-12"),
                ),
            )
        val provisional = recordProvisional(store.id, "表紙が青いやつ")

        val linked = checkNotNull(useCase.execute(LinkProvisionalWorkCommand(provisional.volumeId, null, "ONE PIECE 12")))

        assertEquals(existing.workId, linked.workId)
    }

    @Test
    fun `巻数と ISBN が更新される`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")
        assertNull(repository.findVolume(recorded.volumeId)?.volumeNumber)

        useCase.execute(
            LinkProvisionalWorkCommand(
                volumeId = recorded.volumeId,
                isbn = Isbn.parse("9784088807232").getOrThrow(),
                rawTitle = "ONE PIECE 12",
            ),
        )

        val volume = checkNotNull(repository.findVolume(recorded.volumeId))
        assertEquals(12, volume.volumeNumber)
        assertEquals("9784088807232", volume.isbn?.value)
    }

    @Test
    fun `紐づけ後は配架レコードから正式な作品として引ける`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")

        val linked = checkNotNull(useCase.execute(LinkProvisionalWorkCommand(recorded.volumeId, null, "ONE PIECE 12")))

        assertEquals(listOf(linked.workId), repository.listWorkIdsInStore(store.id))
        assertEquals(1, repository.placementCount(store.id))
    }

    @Test
    @DisplayName("同一の巻を複数店舗に配架していれば、その全てが付け替わる")
    fun `複数店舗の配架レコードがすべて付け替わる`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")
        val recorded = recordProvisional(storeA.id, "表紙が青いやつ")
        // 同じ巻を B店 でも配架として記録した状態を作る。
        // 暫定記録は ISBN も巻番号も持たないため、記録フローからは同じ巻として
        // 同定できない。ここではリポジトリを直接使って前提を作る
        repository.upsertPlacement(
            storeId = storeB.id,
            workId = recorded.workId,
            volumeId = recorded.volumeId,
            shelfNumber = ShelfNumber("Z-99"),
            updatedAt = now,
        )

        val linked = checkNotNull(useCase.execute(LinkProvisionalWorkCommand(recorded.volumeId, null, "ONE PIECE 12")))

        assertEquals(listOf(linked.workId), repository.listWorkIdsInStore(storeA.id))
        assertEquals(listOf(linked.workId), repository.listWorkIdsInStore(storeB.id))
        // 店舗ごとの棚番号は付け替えの影響を受けない
        assertEquals(ShelfNumber("D-01"), repository.listPlacements(storeA.id, linked.workId).single().shelfNumber)
        assertEquals(ShelfNumber("Z-99"), repository.listPlacements(storeB.id, linked.workId).single().shelfNumber)
    }

    @Test
    fun `存在しない巻を指定しても何も起きない`() = runTest {
        assertNull(useCase.execute(LinkProvisionalWorkCommand(volumeId = 999L, isbn = null, rawTitle = "ONE PIECE 12")))
    }

    @Test
    fun `暫定記録の一覧から紐づけ済みの巻が消える`() = runTest {
        val store = repository.createStore("A店")
        val recorded = recordProvisional(store.id, "表紙が青いやつ")
        assertTrue(repository.listProvisionalVolumes().any { it.volumeId == recorded.volumeId })

        useCase.execute(LinkProvisionalWorkCommand(recorded.volumeId, null, "ONE PIECE 12"))

        assertTrue(repository.listProvisionalVolumes().none { it.volumeId == recorded.volumeId })
    }
}
