package io.github.longbowxxx.readingtracker.domain.usecase

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.NewVolume
import io.github.longbowxxx.readingtracker.domain.model.NewWork
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.model.Volume
import io.github.longbowxxx.readingtracker.domain.model.Work
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.shelf.resolveInheritedShelfNumber
import io.github.longbowxxx.readingtracker.domain.title.RuleBasedTitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.analyzeOrFallback
import java.time.Instant

/**
 * 1冊分の記録指示。
 *
 * @property volumeNumberOverride 利用者が確認画面で巻数を修正した場合に指定する。
 *   指定が無ければタイトルから抽出した値を使う（FR-006, FR-027）
 * @property shelfNumber null は「棚番号は未入力」を意味する（FR-017）
 */
data class RecordCommand(
    val storeId: Long,
    val isbn: Isbn?,
    val rawTitle: String,
    val volumeNumberOverride: Int? = null,
    val author: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val status: ReadingStatus,
    val shelfNumber: ShelfNumber? = null,
    val note: String? = null,
    val isProvisional: Boolean = false,
)

/** 記録の結果。 */
data class RecordResult(val workId: Long, val volumeId: Long, val volumeNumber: Int?, val updatedExistingReading: Boolean)

/**
 * 1冊分の記録を保存する（User Story 1）。
 *
 * 作品の自動照合 → 巻の作成/取得 → 読書記録 → 配架レコードの順に処理する。
 * **棚番号が未入力でも配架レコードは必ず作る。** 行の存在が「その店舗でその巻を記録した」
 * 事実を表すためで、これが無いと来店時の一覧に現れない（FR-017 と FR-024 の両立）。
 *
 * 既に読書記録がある巻を再度記録した場合は、新規作成せず [UpdateRecordUseCase] へ委ねる（FR-029）。
 */
class RecordVolumeUseCase(
    private val repository: ReadingRepository,
    private val updateRecordUseCase: UpdateRecordUseCase,
    private val titleAnalyzer: TitleAnalyzer = RuleBasedTitleAnalyzer(),
    private val clock: () -> Instant = Instant::now,
) {
    /**
     * 棚番号の初期値を求める（FR-015）。該当する作品の記録が当該店舗に無ければ null。
     *
     * 渡す配架レコードは当該店舗・当該作品に限定されるため、他店舗の棚番号が
     * 混ざることはない（FR-014）。
     */
    suspend fun suggestShelfNumber(storeId: Long, rawTitle: String, volumeNumberOverride: Int? = null): ShelfNumber? {
        val parsed = titleAnalyzer.analyzeOrFallback(rawTitle)
        val work = repository.findWorkByMatchKey(parsed.matchKey) ?: return null
        val targetVolumeNumber = volumeNumberOverride ?: parsed.volumeNumber
        return resolveInheritedShelfNumber(targetVolumeNumber, repository.listPlacements(storeId, work.id))
    }

    suspend fun execute(command: RecordCommand): RecordResult {
        val parsed = titleAnalyzer.analyzeOrFallback(command.rawTitle)
        val volumeNumber = command.volumeNumberOverride ?: parsed.volumeNumber

        val existingVolume = command.isbn?.let { repository.findVolumeByIsbn(it) }
        val work = resolveWork(command, parsed.workTitle, parsed.matchKey, existingVolume)
        val volume = existingVolume ?: resolveVolume(command, work, volumeNumber)

        val alreadyRecorded = repository.findReading(volume.id) != null
        if (alreadyRecorded) {
            // 新規作成せず既存記録の更新に委ねる（FR-029）
            updateRecordUseCase.execute(
                UpdateRecordCommand(
                    volumeId = volume.id,
                    status = command.status,
                    note = command.note,
                    placement =
                    PlacementUpdate(
                        storeId = command.storeId,
                        workId = work.id,
                        shelfNumber = command.shelfNumber,
                    ),
                ),
            )
        } else {
            repository.upsertReading(
                volumeId = volume.id,
                status = command.status,
                note = command.note,
                recordedAt = clock(),
            )
            repository.upsertPlacement(
                storeId = command.storeId,
                workId = work.id,
                volumeId = volume.id,
                shelfNumber = command.shelfNumber,
                updatedAt = clock(),
            )
        }

        return RecordResult(
            workId = work.id,
            volumeId = volume.id,
            volumeNumber = volume.volumeNumber,
            updatedExistingReading = alreadyRecorded,
        )
    }

    /**
     * 作品を同定する。ISBN で既知の巻が見つかればその作品を、無ければ照合キーで探し、
     * それも無ければ新規に作る（FR-027）。
     */
    private suspend fun resolveWork(command: RecordCommand, workTitle: String, matchKey: String, existingVolume: Volume?): Work {
        existingVolume?.let { volume ->
            repository.findWork(volume.workId)?.let { return it }
        }
        repository.findWorkByMatchKey(matchKey)?.let { return it }

        return repository.createWork(
            NewWork(
                title = workTitle,
                matchKey = matchKey,
                author = command.author,
                publisher = command.publisher,
                isProvisional = command.isProvisional,
            ),
        )
    }

    private suspend fun resolveVolume(command: RecordCommand, work: Work, volumeNumber: Int?): Volume {
        if (volumeNumber != null) {
            repository.findVolumeByNumber(work.id, volumeNumber)?.let { return it }
        }

        return repository.createVolume(
            NewVolume(
                workId = work.id,
                volumeNumber = volumeNumber,
                isbn = command.isbn,
                displayTitle = command.rawTitle,
                publishedDate = command.publishedDate,
            ),
        )
    }
}
