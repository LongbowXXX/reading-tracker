package io.github.longbowxxx.readingtracker.domain.reading

import io.github.longbowxxx.readingtracker.domain.model.ReadingSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 次に読むべき巻の判定（FR-023）。
 *
 * システムは次巻が実在するかを判定しない。新刊把握（D群）は本スコープ外のため。
 */
class NextVolumeResolverTest {
    private fun reading(volumeNumber: Int?, status: ReadingStatus) = ReadingSnapshot(
        volumeId = (volumeNumber ?: 0).toLong(),
        volumeNumber = volumeNumber,
        status = status,
    )

    @Test
    fun `中断中の巻があればその巻を示す`() {
        val readings =
            listOf(
                reading(11, ReadingStatus.READ),
                reading(12, ReadingStatus.READ),
                reading(13, ReadingStatus.PAUSED),
            )

        assertEquals(NextVolume.Paused(volumeNumber = 13, volumeId = 13L), resolveNextVolume(readings))
    }

    @Test
    fun `中断中の巻が複数あれば巻番号が最小のものを示す`() {
        val readings =
            listOf(
                reading(20, ReadingStatus.PAUSED),
                reading(5, ReadingStatus.PAUSED),
            )

        assertEquals(NextVolume.Paused(volumeNumber = 5, volumeId = 5L), resolveNextVolume(readings))
    }

    @Test
    fun `中断中の巻が無ければ読了済み最大巻の次の巻番号を示す`() {
        val readings = (1..12).map { reading(it, ReadingStatus.READ) }

        assertEquals(NextVolume.Next(volumeNumber = 13), resolveNextVolume(readings))
    }

    @Test
    fun `記録が1件も無ければ不明`() {
        assertEquals(NextVolume.Unknown, resolveNextVolume(emptyList()))
    }

    @Test
    fun `巻番号を持つ記録が無ければ不明`() {
        val readings = listOf(reading(null, ReadingStatus.READ))

        assertEquals(NextVolume.Unknown, resolveNextVolume(readings))
    }

    @Test
    fun `巻番号が不明な中断中の巻は巻番号を持つ中断中の巻より後に評価する`() {
        val readings =
            listOf(
                reading(null, ReadingStatus.PAUSED),
                reading(7, ReadingStatus.PAUSED),
            )

        assertEquals(NextVolume.Paused(volumeNumber = 7, volumeId = 7L), resolveNextVolume(readings))
    }

    @Test
    fun `巻番号が不明な中断中の巻しか無ければそれを示す`() {
        val readings = listOf(reading(null, ReadingStatus.PAUSED))

        assertEquals(NextVolume.Paused(volumeNumber = null, volumeId = 0L), resolveNextVolume(readings))
    }

    @Test
    fun `読了済みに巻番号不明が混ざっても巻番号を持つ最大巻から判定する`() {
        val readings =
            listOf(
                reading(3, ReadingStatus.READ),
                reading(null, ReadingStatus.READ),
            )

        assertEquals(NextVolume.Next(volumeNumber = 4), resolveNextVolume(readings))
    }
}
