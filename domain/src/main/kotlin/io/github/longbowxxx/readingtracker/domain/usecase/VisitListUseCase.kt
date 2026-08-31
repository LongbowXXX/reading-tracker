package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.port.StoreWorkSnapshot
import io.github.longbowxxx.readingtracker.domain.reading.NextVolume
import io.github.longbowxxx.readingtracker.domain.reading.resolveNextVolume
import io.github.longbowxxx.readingtracker.domain.shelf.resolveInheritedShelfNumber

/**
 * 来店時の一覧の1行（B-1, B-2, B-3）。
 *
 * @property shelfNumber **[nextVolume] を探すための棚番号**。null は未入力（FR-022）
 */
data class VisitListItem(val workId: Long, val workTitle: String, val shelfNumber: ShelfNumber?, val nextVolume: NextVolume)

/**
 * 選んだ店舗で読める読みかけ作品を一覧する（User Story 2）。
 *
 * 「何巻まで読んだかを思い出す」機能ではなく、「この店で今日読める続きを提示する」機能。
 *
 * 併記する棚番号は、作品の代表値ではなく**次に読むべき巻を探すための番号**とする。
 * 長期連載では巻によって棚が分かれるため（要求定義書 3.2）、作品に1つの番号を割り当てると
 * 目的の巻にたどり着けないことがある。
 *
 * 「離脱」による除外（E-1）は本スコープ外のため行わない。
 */
class VisitListUseCase(private val repository: ReadingRepository) {
    suspend fun execute(storeId: Long): List<VisitListItem> = repository.listStoreWorkSnapshots(storeId).map { snapshot ->
        val nextVolume = resolveNextVolume(snapshot.readings)
        VisitListItem(
            workId = snapshot.workId,
            workTitle = snapshot.workTitle,
            shelfNumber = resolveShelfNumberFor(nextVolume, snapshot),
            nextVolume = nextVolume,
        )
    }

    /**
     * 次に読むべき巻を探すための棚番号を決める。
     *
     * - 中断中の巻は当該店舗に記録があるはずなので、その巻の棚番号をそのまま使う
     * - 次の巻が未記録の場合は、直前の巻から継承した番号を使う（FR-015 と同じ規則）
     */
    private fun resolveShelfNumberFor(nextVolume: NextVolume, snapshot: StoreWorkSnapshot): ShelfNumber? = when (nextVolume) {
        is NextVolume.Paused ->
            snapshot.placements
                .firstOrNull { it.volumeId == nextVolume.volumeId }
                ?.shelfNumber
                ?: inherited(nextVolume.volumeNumber, snapshot.placements)

        is NextVolume.Next -> inherited(nextVolume.volumeNumber, snapshot.placements)

        NextVolume.Unknown -> inherited(null, snapshot.placements)
    }

    private fun inherited(volumeNumber: Int?, placements: List<PlacementSnapshot>): ShelfNumber? =
        resolveInheritedShelfNumber(volumeNumber, placements)
}
