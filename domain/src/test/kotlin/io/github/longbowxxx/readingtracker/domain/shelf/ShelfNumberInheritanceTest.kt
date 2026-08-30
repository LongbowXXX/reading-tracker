package io.github.longbowxxx.readingtracker.domain.shelf

import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 棚番号の継承（FR-015, FR-016）。
 *
 * 憲法 原則III が必須とするテスト項目を含む。このテストは仕様書として機能させる。
 * 引数に店舗 ID を含まないことが、他店舗へ到達しえない構造の担保である（FR-014）。
 */
class ShelfNumberInheritanceTest {
    private val base: Instant = Instant.parse("2026-08-01T00:00:00Z")

    private fun placement(volumeNumber: Int?, shelfNumber: String?, minutesAfterBase: Long) = PlacementSnapshot(
        volumeId = (volumeNumber ?: 0).toLong() * 100 + minutesAfterBase,
        volumeNumber = volumeNumber,
        shelfNumber = shelfNumber?.let { ShelfNumber(it) },
        updatedAt = base.plusSeconds(minutesAfterBase * 60),
    )

    @Test
    @DisplayName("憲法 原則III: 棚番号が直前の巻から継承される")
    fun `直前の巻の棚番号を初期値とする`() {
        val placements =
            listOf(
                placement(volumeNumber = 29, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = 30, shelfNumber = "A-12", minutesAfterBase = 2),
            )

        assertEquals(ShelfNumber("A-12"), resolveInheritedShelfNumber(31, placements))
    }

    @Test
    @DisplayName("憲法 原則III: 31巻で変更した番号が32巻以降へ継承される")
    fun `巻単位の変更が以降の巻へ継承される`() {
        val placements =
            listOf(
                placement(volumeNumber = 30, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = 31, shelfNumber = "B-03", minutesAfterBase = 2),
            )

        assertEquals(ShelfNumber("B-03"), resolveInheritedShelfNumber(32, placements))
    }

    @Test
    fun `変更した巻より前の巻には古い番号が残る`() {
        val placements =
            listOf(
                placement(volumeNumber = 30, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = 31, shelfNumber = "B-03", minutesAfterBase = 2),
            )

        assertEquals(ShelfNumber("A-12"), resolveInheritedShelfNumber(31, placements))
    }

    @Test
    fun `巻番号順の継承元が無ければ記録日時が最新の巻から継承する`() {
        // 5巻だけ記録済みの状態で3巻を記録する場合
        val placements = listOf(placement(volumeNumber = 5, shelfNumber = "A-12", minutesAfterBase = 1))

        assertEquals(ShelfNumber("A-12"), resolveInheritedShelfNumber(3, placements))
    }

    @Test
    fun `フォールバックでは記録日時が最も新しいレコードを選ぶ`() {
        val placements =
            listOf(
                placement(volumeNumber = 10, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = 20, shelfNumber = "C-07", minutesAfterBase = 5),
                placement(volumeNumber = 15, shelfNumber = "B-03", minutesAfterBase = 3),
            )

        assertEquals(ShelfNumber("C-07"), resolveInheritedShelfNumber(1, placements))
    }

    @Test
    fun `記録が1件も無ければ初期値なし`() {
        assertNull(resolveInheritedShelfNumber(1, emptyList()))
    }

    @Test
    fun `継承元の棚番号が未入力なら結果も未入力`() {
        val placements = listOf(placement(volumeNumber = 30, shelfNumber = null, minutesAfterBase = 1))

        assertNull(resolveInheritedShelfNumber(31, placements))
    }

    @Test
    fun `対象の巻番号が不明なら巻番号順を評価せずフォールバックする`() {
        val placements =
            listOf(
                placement(volumeNumber = 10, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = 20, shelfNumber = "C-07", minutesAfterBase = 5),
            )

        assertEquals(ShelfNumber("C-07"), resolveInheritedShelfNumber(null, placements))
    }

    @Test
    fun `巻番号が不明なレコードは巻番号順の継承元にならない`() {
        val placements =
            listOf(
                placement(volumeNumber = 10, shelfNumber = "A-12", minutesAfterBase = 1),
                placement(volumeNumber = null, shelfNumber = "Z-99", minutesAfterBase = 9),
            )

        // 巻番号順では 10巻が選ばれ、記録日時が新しい巻番号不明のレコードは無視される
        assertEquals(ShelfNumber("A-12"), resolveInheritedShelfNumber(11, placements))
    }

    @Test
    fun `巻番号が不明なレコードもフォールバックでは継承元になりうる`() {
        val placements = listOf(placement(volumeNumber = null, shelfNumber = "Z-99", minutesAfterBase = 9))

        assertEquals(ShelfNumber("Z-99"), resolveInheritedShelfNumber(3, placements))
    }
}
