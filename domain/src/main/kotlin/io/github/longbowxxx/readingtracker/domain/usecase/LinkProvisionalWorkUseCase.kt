package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.NewWork
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.RuleBasedTitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.analyzeOrFallback

/**
 * 暫定記録を正式な作品へ紐づける指示（FR-008）。
 *
 * @property volumeId 暫定名で記録した巻。**この ID は変えない**
 * @property isbn 判明した ISBN。分からなければ null のままでよい
 */
data class LinkProvisionalWorkCommand(
    val volumeId: Long,
    val isbn: Isbn?,
    val rawTitle: String,
    val volumeNumberOverride: Int? = null,
    val author: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
)

data class LinkProvisionalWorkResult(val workId: Long, val volumeId: Long)

/**
 * 暫定名で記録した巻を、正式な作品へ紐づけ直す（User Story 3 / FR-008）。
 *
 * **巻の ID を変えずに作品だけを付け替える。** 読書記録は巻 ID に、配架レコードは
 * 店舗 ID と巻 ID に結ばれているため、これにより読書状態・棚番号・メモが自動的に保たれる。
 *
 * 付け替え元の暫定作品に巻が残らなくなっても、その作品は削除しない。
 * 記録の削除（F-3）は本スコープ外であり、配架レコードも一緒に付け替わるため
 * 来店時の一覧には現れない。
 */
class LinkProvisionalWorkUseCase(
    private val repository: ReadingRepository,
    private val titleAnalyzer: TitleAnalyzer = RuleBasedTitleAnalyzer(),
) {
    /** @return 紐づけ結果。指定の巻が存在しなければ null */
    suspend fun execute(command: LinkProvisionalWorkCommand): LinkProvisionalWorkResult? {
        val volume = repository.findVolume(command.volumeId) ?: return null

        val parsed = titleAnalyzer.analyzeOrFallback(command.rawTitle)
        val volumeNumber = command.volumeNumberOverride ?: parsed.volumeNumber

        val targetWork =
            repository.findWorkByMatchKey(parsed.matchKey)
                ?: repository.createWork(
                    NewWork(
                        title = parsed.workTitle,
                        matchKey = parsed.matchKey,
                        author = command.author,
                        publisher = command.publisher,
                        // 紐づけ先は正式な作品として扱う
                        isProvisional = false,
                    ),
                )

        if (volume.workId != targetWork.id) {
            // 巻と、その巻を参照する全店舗の配架レコードを同時に付け替える
            repository.relinkVolumeToWork(command.volumeId, targetWork.id)
        }

        repository.updateVolumeDetails(
            volumeId = command.volumeId,
            volumeNumber = volumeNumber,
            isbn = command.isbn ?: volume.isbn,
            displayTitle = command.rawTitle,
            publishedDate = command.publishedDate ?: volume.publishedDate,
        )

        return LinkProvisionalWorkResult(workId = targetWork.id, volumeId = command.volumeId)
    }
}
