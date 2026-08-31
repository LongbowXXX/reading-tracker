package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.fake.FakeReadingRepository
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 記録の作成（User Story 1）。
 *
 * 棚番号の継承、未入力での保存、既存記録の扱い、店舗をまたいだ記録を検証する。
 */
class RecordVolumeUseCaseTest {
    private lateinit var repository: FakeReadingRepository
    private lateinit var useCase: RecordVolumeUseCase

    private var now: Instant = Instant.parse("2026-08-31T10:00:00Z")

    @BeforeEach
    fun setUp() {
        repository = FakeReadingRepository()
        useCase = RecordVolumeUseCase(repository, UpdateRecordUseCase(repository) { now }) { now }
    }

    private fun command(
        storeId: Long,
        title: String,
        isbn: String? = null,
        status: ReadingStatus = ReadingStatus.READ,
        shelfNumber: String? = null,
    ) = RecordCommand(
        storeId = storeId,
        isbn = isbn?.let { Isbn.parse(it).getOrThrow() },
        rawTitle = title,
        status = status,
        shelfNumber = shelfNumber?.let { ShelfNumber(it) },
    )

    @Test
    fun `作品と巻と読書記録と配架レコードを作る`() = runTest {
        val store = repository.createStore("A店")

        val result = useCase.execute(command(store.id, "ONE PIECE 12", shelfNumber = "A-12"))

        assertEquals(12, result.volumeNumber)
        assertFalse(result.updatedExistingReading)
        assertEquals(1, repository.readingCount())
        assertEquals(1, repository.placementCount(store.id))
    }

    @Test
    @DisplayName("棚番号が直前の巻から初期値として提示される")
    fun `棚番号の初期値を提示する`() = runTest {
        val store = repository.createStore("A店")
        useCase.execute(command(store.id, "ONE PIECE 12", shelfNumber = "A-12"))

        val suggested = useCase.suggestShelfNumber(store.id, "ONE PIECE 13")

        assertEquals(ShelfNumber("A-12"), suggested)
    }

    @Test
    fun `記録の無い作品では棚番号の初期値を提示しない`() = runTest {
        val store = repository.createStore("A店")

        assertNull(useCase.suggestShelfNumber(store.id, "よつばと！ 1"))
    }

    @Test
    fun `別店舗の棚番号を初期値として提示しない`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")
        useCase.execute(command(storeA.id, "ONE PIECE 12", shelfNumber = "A-12"))

        assertNull(useCase.suggestShelfNumber(storeB.id, "ONE PIECE 13"))
    }

    @Test
    fun `棚番号が未入力でも保存でき配架レコードは作られる`() = runTest {
        val store = repository.createStore("A店")

        useCase.execute(command(store.id, "ONE PIECE 12", shelfNumber = null))

        // 行の存在が「その店でその巻を記録した」事実を表す（FR-017 と FR-024 の両立）
        assertEquals(1, repository.placementCount(store.id))
        assertEquals(listOf(1L), repository.listWorkIdsInStore(store.id))
    }

    @Test
    fun `既存の記録がある場合は新規作成せず更新する`() = runTest {
        val store = repository.createStore("A店")
        useCase.execute(command(store.id, "ONE PIECE 12", status = ReadingStatus.PAUSED, shelfNumber = "A-12"))

        val result = useCase.execute(command(store.id, "ONE PIECE 12", status = ReadingStatus.READ, shelfNumber = "A-12"))

        assertTrue(result.updatedExistingReading)
        assertEquals(1, repository.readingCount())
        assertEquals(ReadingStatus.READ, repository.findReading(result.volumeId)?.status)
    }

    @Test
    @DisplayName("同一の巻を別の店舗で記録しても読書記録は1件のまま")
    fun `別店舗での記録は配架レコードだけを増やす`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")

        useCase.execute(command(storeA.id, "ONE PIECE 12", status = ReadingStatus.PAUSED, shelfNumber = "A-12"))
        val result = useCase.execute(command(storeB.id, "ONE PIECE 12", status = ReadingStatus.READ, shelfNumber = "C-07"))

        assertEquals(1, repository.readingCount())
        assertEquals(1, repository.placementCount(storeA.id))
        assertEquals(1, repository.placementCount(storeB.id))
        assertEquals(ReadingStatus.READ, repository.findReading(result.volumeId)?.status)
    }

    @Test
    fun `同一シリーズの別の巻は同じ作品にまとめられる`() = runTest {
        val store = repository.createStore("A店")

        val first = useCase.execute(command(store.id, "ONE PIECE 12"))
        val second = useCase.execute(command(store.id, "ONE PIECE 13"))

        assertEquals(first.workId, second.workId)
    }

    @Test
    fun `ISBN があれば同じ巻として同定する`() = runTest {
        val store = repository.createStore("A店")

        val first = useCase.execute(command(store.id, "ONE PIECE 12", isbn = "9784088807232"))
        // 表記が揺れていても ISBN が一致すれば同じ巻
        val second = useCase.execute(command(store.id, "ＯＮＥ　ＰＩＥＣＥ 12", isbn = "9784088807232"))

        assertEquals(first.volumeId, second.volumeId)
        assertEquals(1, repository.readingCount())
    }

    @Test
    fun `暫定名でも記録できる`() = runTest {
        val store = repository.createStore("A店")

        val result =
            useCase.execute(
                RecordCommand(
                    storeId = store.id,
                    isbn = null,
                    rawTitle = "表紙が青いやつ",
                    status = ReadingStatus.PAUSED,
                    isProvisional = true,
                ),
            )

        assertNull(result.volumeNumber)
        assertTrue(repository.findWork(result.workId)?.isProvisional == true)
    }
}
