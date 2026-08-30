package io.github.longbowxxx.readingtracker.domain.shelf

import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 作品単位の棚番号一括適用の店舗独立性（憲法 原則III 必須項目）。
 *
 * A-9（一括更新）は今回スコープ外であり、UI からの導線は作らない。
 * この関数は原則III が要求するテストを成立させるためだけに存在する
 * （plan.md の Complexity Tracking を参照）。
 */
class ShelfNumberBulkUpdateTest {
    private val now: Instant = Instant.parse("2026-08-30T12:00:00Z")

    private fun placement(volumeId: Long, volumeNumber: Int?, shelfNumber: String?) = PlacementSnapshot(
        volumeId = volumeId,
        volumeNumber = volumeNumber,
        shelfNumber = shelfNumber?.let { ShelfNumber(it) },
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    @Test
    @DisplayName("憲法 原則III: 一括更新が他店舗の記録に影響しない")
    fun `入力に含まれるレコードだけを更新して返す`() {
        // A店の配架レコードのみを渡す。B店のレコードは引数に含めない
        val storeAPlacements =
            listOf(
                placement(volumeId = 1L, volumeNumber = 1, shelfNumber = "A-12"),
                placement(volumeId = 2L, volumeNumber = 2, shelfNumber = "A-12"),
                placement(volumeId = 3L, volumeNumber = 3, shelfNumber = null),
            )

        val updated = applyShelfNumberToWork(ShelfNumber("D-01"), storeAPlacements, now)

        assertEquals(storeAPlacements.map { it.volumeId }.toSet(), updated.map { it.volumeId }.toSet())
        assertTrue(updated.all { it.shelfNumber == ShelfNumber("D-01") })
    }

    @Test
    fun `未入力の巻にも新しい棚番号が適用される`() {
        val placements = listOf(placement(volumeId = 9L, volumeNumber = 9, shelfNumber = null))

        val updated = applyShelfNumberToWork(ShelfNumber("D-01"), placements, now)

        assertEquals(ShelfNumber("D-01"), updated.single().shelfNumber)
    }

    @Test
    fun `更新日時が渡した時刻で置き換わる`() {
        val placements = listOf(placement(volumeId = 1L, volumeNumber = 1, shelfNumber = "A-12"))

        val updated = applyShelfNumberToWork(ShelfNumber("D-01"), placements, now)

        assertEquals(now, updated.single().updatedAt)
    }

    @Test
    fun `空の入力には空を返す`() {
        assertTrue(applyShelfNumberToWork(ShelfNumber("D-01"), emptyList(), now).isEmpty())
    }

    @Test
    fun `入力のレコードを破壊的に変更しない`() {
        val original = placement(volumeId = 1L, volumeNumber = 1, shelfNumber = "A-12")
        val placements = listOf(original)

        applyShelfNumberToWork(ShelfNumber("D-01"), placements, now)

        assertEquals(ShelfNumber("A-12"), original.shelfNumber)
    }
}
