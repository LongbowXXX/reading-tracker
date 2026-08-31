package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.fake.FakeReadingRepository
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.reading.NextVolume
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 来店時の一覧（User Story 2 / B-1, B-2, B-3）。
 *
 * 「この店で今日読める続きを提示する」機能であり、課題1・4 に対する実質的な解にあたる。
 */
class VisitListUseCaseTest {
    private lateinit var repository: FakeReadingRepository
    private lateinit var recordUseCase: RecordVolumeUseCase
    private lateinit var useCase: VisitListUseCase

    private val now: Instant = Instant.parse("2026-08-31T10:00:00Z")

    @BeforeEach
    fun setUp() {
        repository = FakeReadingRepository()
        recordUseCase = RecordVolumeUseCase(repository, UpdateRecordUseCase(repository) { now }) { now }
        useCase = VisitListUseCase(repository)
    }

    private suspend fun record(storeId: Long, title: String, status: ReadingStatus = ReadingStatus.READ, shelfNumber: String? = null) =
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
    fun `記録が無ければ空の一覧になる`() = runTest {
        val store = repository.createStore("A店")

        assertTrue(useCase.execute(store.id).isEmpty())
    }

    @Test
    @DisplayName("選択した店舗で記録した作品だけが並ぶ")
    fun `他店舗のみで記録した作品は含めない`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")
        record(storeA.id, "ONE PIECE 12", shelfNumber = "A-12")
        record(storeB.id, "よつばと！ 3", shelfNumber = "C-07")

        val items = useCase.execute(storeA.id)

        assertEquals(1, items.size)
        assertEquals("ONE PIECE", items.single().workTitle)
    }

    @Test
    fun `棚番号を併記する`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", shelfNumber = "A-12")

        assertEquals(ShelfNumber("A-12"), useCase.execute(store.id).single().shelfNumber)
    }

    @Test
    fun `店舗ごとに異なる棚番号を示す`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")
        record(storeA.id, "ONE PIECE 12", shelfNumber = "A-12")
        record(storeB.id, "ONE PIECE 12", shelfNumber = "C-07")

        assertEquals(ShelfNumber("A-12"), useCase.execute(storeA.id).single().shelfNumber)
        assertEquals(ShelfNumber("C-07"), useCase.execute(storeB.id).single().shelfNumber)
    }

    @Test
    fun `棚番号が未入力の作品も一覧に現れる`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", shelfNumber = null)

        val item = useCase.execute(store.id).single()

        assertEquals("ONE PIECE", item.workTitle)
        assertNull(item.shelfNumber)
    }

    @Test
    fun `中断中の巻があればそれを次に読むべき巻として示す`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", ReadingStatus.READ, "A-12")
        record(store.id, "ONE PIECE 13", ReadingStatus.PAUSED, "A-12")

        val item = useCase.execute(store.id).single()

        assertTrue(item.nextVolume is NextVolume.Paused)
        assertEquals(13, (item.nextVolume as NextVolume.Paused).volumeNumber)
    }

    @Test
    fun `中断中の巻が無ければ読了済み最大巻の次を示す`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", ReadingStatus.READ, "A-12")

        assertEquals(NextVolume.Next(volumeNumber = 13), useCase.execute(store.id).single().nextVolume)
    }

    @Test
    @DisplayName("次に読むべき巻が未記録なら、直前の巻から継承した棚番号を示す")
    fun `未記録の次巻には継承した棚番号を示す`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 30", ReadingStatus.READ, "A-12")
        record(store.id, "ONE PIECE 31", ReadingStatus.READ, "B-03")

        val item = useCase.execute(store.id).single()

        // 32巻は未記録。31巻で棚が変わっているので B-03 を引き継ぐ
        assertEquals(NextVolume.Next(volumeNumber = 32), item.nextVolume)
        assertEquals(ShelfNumber("B-03"), item.shelfNumber)
    }

    @Test
    fun `中断中の巻の棚番号をそのまま示す`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 30", ReadingStatus.READ, "A-12")
        record(store.id, "ONE PIECE 31", ReadingStatus.PAUSED, "B-03")

        val item = useCase.execute(store.id).single()

        assertEquals(ShelfNumber("B-03"), item.shelfNumber)
    }

    @Test
    @DisplayName("巻番号不明の暫定記録だけの作品は、次に読むべき巻を示さない")
    fun `暫定記録のみの作品は次巻を示さない`() = runTest {
        val store = repository.createStore("A店")
        recordUseCase.execute(
            RecordCommand(
                storeId = store.id,
                isbn = null,
                rawTitle = "表紙が青いやつ",
                status = ReadingStatus.PAUSED,
                shelfNumber = ShelfNumber("D-01"),
                isProvisional = true,
            ),
        )

        val item = useCase.execute(store.id).single()

        // 中断中ではあるが巻番号が分からない
        assertTrue(item.nextVolume is NextVolume.Paused)
        assertNull((item.nextVolume as NextVolume.Paused).volumeNumber)
        assertEquals(ShelfNumber("D-01"), item.shelfNumber)
    }

    @Test
    @DisplayName("一覧から開く記録は、中断中の巻を対象にする")
    fun `中断中の巻を編集対象にする`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", ReadingStatus.READ, "A-12")
        val paused = record(store.id, "ONE PIECE 13", ReadingStatus.PAUSED, "A-12")

        assertEquals(paused.volumeId, useCase.execute(store.id).single().editableVolumeId)
    }

    @Test
    fun `中断中の巻が無ければ巻番号が最大の巻を編集対象にする`() = runTest {
        val store = repository.createStore("A店")
        val latest = record(store.id, "ONE PIECE 13", ReadingStatus.READ, "A-12")
        // 後から前の巻を記録しても、対象は巻番号が最大の13巻のまま
        record(store.id, "ONE PIECE 12", ReadingStatus.READ, "A-12")

        assertEquals(latest.volumeId, useCase.execute(store.id).single().editableVolumeId)
    }

    @Test
    fun `他店舗で中断した巻は編集対象にしない`() = runTest {
        val storeA = repository.createStore("A店")
        val storeB = repository.createStore("B店")
        val atA = record(storeA.id, "ONE PIECE 12", ReadingStatus.READ, "A-12")
        record(storeB.id, "ONE PIECE 13", ReadingStatus.PAUSED, "C-07")

        // A店の一覧から開けるのは、A店に配架記録のある巻だけ
        assertEquals(atA.volumeId, useCase.execute(storeA.id).single().editableVolumeId)
    }

    @Test
    fun `複数の作品が並ぶ`() = runTest {
        val store = repository.createStore("A店")
        record(store.id, "ONE PIECE 12", shelfNumber = "A-12")
        record(store.id, "よつばと！ 3", shelfNumber = "C-07")

        assertEquals(2, useCase.execute(store.id).size)
    }
}
