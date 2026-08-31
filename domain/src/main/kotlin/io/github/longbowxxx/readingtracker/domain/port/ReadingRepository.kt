package io.github.longbowxxx.readingtracker.domain.port

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.NewVolume
import io.github.longbowxxx.readingtracker.domain.model.NewWork
import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.model.Store
import io.github.longbowxxx.readingtracker.domain.model.Volume
import io.github.longbowxxx.readingtracker.domain.model.Work
import java.time.Instant

/**
 * 記録の永続化。実装は `:data`（Room）に置く。
 *
 * ドメイン層はこのインターフェースだけを知り、Room にも Android にも依存しない
 * （憲法 原則III）。データはすべて端末内に保持する（憲法 原則V）。
 */
interface ReadingRepository {
    // ---- 店舗（FR-030, FR-031: 新規登録と選択のみ。編集・削除は本スコープ外） ----

    suspend fun listStores(): List<Store>

    suspend fun createStore(name: String): Store

    // ---- 作品・巻 ----

    /** 照合キーで既存の作品を探す（FR-027）。 */
    suspend fun findWorkByMatchKey(matchKey: String): Work?

    suspend fun findWork(workId: Long): Work?

    suspend fun createWork(work: NewWork): Work

    suspend fun findVolumeByIsbn(isbn: Isbn): Volume?

    suspend fun findVolumeByNumber(workId: Long, volumeNumber: Int): Volume?

    suspend fun findVolume(volumeId: Long): Volume?

    suspend fun createVolume(volume: NewVolume): Volume

    /**
     * 暫定記録を正式な作品へ付け替える（FR-008）。
     * `Volume.workId` と、その巻を参照する全ての配架レコードの作品 ID を同時に更新すること。
     */
    suspend fun relinkVolumeToWork(volumeId: Long, newWorkId: Long)

    /**
     * 巻の書誌情報を更新する（FR-008 の紐づけ時、および FR-019 の修正時）。
     * **巻の ID は変えない。** 読書記録と配架レコードは巻 ID で結ばれており、
     * 更新によってそれらが失われてはならない。
     */
    suspend fun updateVolumeDetails(volumeId: Long, volumeNumber: Int?, isbn: Isbn?, displayTitle: String, publishedDate: String?)

    /** 暫定名のまま残っている巻の一覧（FR-008 の紐づけ導線）。 */
    suspend fun listProvisionalVolumes(): List<ProvisionalVolume>

    // ---- 読書記録（作品 × 巻に対して1つ。店舗をまたいで共有される） ----

    suspend fun findReading(volumeId: Long): ReadingSnapshot?

    suspend fun listReadingsByWork(workId: Long): List<ReadingSnapshot>

    /**
     * 読書記録を保存する。既に記録があれば**新規作成せず更新する**（FR-029）。
     */
    suspend fun upsertReading(volumeId: Long, status: ReadingStatus, note: String?, recordedAt: Instant)

    // ---- 配架（店舗 × 作品 × 巻で一意。FR-013, FR-014） ----

    /** 単一店舗・単一作品の配架レコードを返す。棚番号の継承判定にそのまま渡せる。 */
    suspend fun listPlacements(storeId: Long, workId: Long): List<PlacementSnapshot>

    /**
     * 配架レコードを保存する。
     * **[shelfNumber] が null（未入力）でも行を作ること**（FR-017, FR-024）。
     * 行の存在が「その店舗でその巻を記録した」事実を表す。
     */
    suspend fun upsertPlacement(storeId: Long, workId: Long, volumeId: Long, shelfNumber: ShelfNumber?, updatedAt: Instant)

    /** 指定店舗で記録のある作品の ID を返す（B-1 の絞り込み）。 */
    suspend fun listWorkIdsInStore(storeId: Long): List<Long>

    /**
     * 来店時の一覧に必要な情報を作品単位でまとめて返す（B-1, B-2, B-3）。
     *
     * 含めるのは**指定店舗で記録のある作品だけ**（FR-024）。配架レコードは指定店舗のものに
     * 限られるが、**読書記録はその作品の全ての巻を含む**。読書状態は店舗をまたいで共有される
     * ため、他店舗で読んだ巻も「次に読むべき巻」の判定に効かせる必要がある。
     */
    suspend fun listStoreWorkSnapshots(storeId: Long): List<StoreWorkSnapshot>
}

/**
 * ある店舗における1作品分のスナップショット。
 *
 * @property placements 当該店舗・当該作品の配架レコードのみ
 * @property readings 当該作品の読書記録（店舗によらない）
 */
data class StoreWorkSnapshot(
    val workId: Long,
    val workTitle: String,
    val placements: List<PlacementSnapshot>,
    val readings: List<ReadingSnapshot>,
)

/** 暫定名のまま残っている巻。正式な作品へ紐づけ直す対象（FR-008）。 */
data class ProvisionalVolume(val volumeId: Long, val workId: Long, val workTitle: String, val displayTitle: String, val volumeNumber: Int?)
