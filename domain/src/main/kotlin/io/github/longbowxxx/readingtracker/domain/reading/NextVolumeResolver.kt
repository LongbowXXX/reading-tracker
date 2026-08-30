package io.github.longbowxxx.readingtracker.domain.reading

import io.github.longbowxxx.readingtracker.domain.model.ReadingSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus

/** 次に読むべき巻の判定結果（FR-023）。 */
sealed interface NextVolume {
    /** 中断中の巻がある。その巻の続きから読む。 */
    data class Paused(val volumeNumber: Int?, val volumeId: Long) : NextVolume

    /**
     * 読了済みの最大巻の次の巻。
     *
     * **その巻が実在するかは判定しない。** 新刊把握（D群）は本スコープ外であり、
     * システムは刊行状況を知らない。
     */
    data class Next(val volumeNumber: Int) : NextVolume

    /** 巻番号を持つ記録が無く、次の巻を示せない。 */
    data object Unknown : NextVolume
}

/**
 * 次に読むべき巻を判定する（FR-023）。
 *
 * 1. 中断中の巻があれば、そのうち巻番号が最小のものを返す（巻番号が不明なものは最後に評価する）
 * 2. 無ければ、読了済みの巻のうち巻番号が最大のものに 1 を加えた巻番号を返す
 * 3. 巻番号を持つ記録が1件も無ければ [NextVolume.Unknown]
 *
 * **事前条件**: [readings] は単一の作品に属する記録のみで構成されること。
 */
fun resolveNextVolume(readings: List<ReadingSnapshot>): NextVolume {
    val paused = readings.filter { it.status == ReadingStatus.PAUSED }
    if (paused.isNotEmpty()) {
        val target =
            paused.filter { it.volumeNumber != null }.minByOrNull { checkNotNull(it.volumeNumber) }
                ?: paused.first()
        return NextVolume.Paused(volumeNumber = target.volumeNumber, volumeId = target.volumeId)
    }

    val lastRead =
        readings
            .filter { it.status == ReadingStatus.READ && it.volumeNumber != null }
            .maxByOrNull { checkNotNull(it.volumeNumber) }
            ?: return NextVolume.Unknown

    return NextVolume.Next(volumeNumber = checkNotNull(lastRead.volumeNumber) + 1)
}
