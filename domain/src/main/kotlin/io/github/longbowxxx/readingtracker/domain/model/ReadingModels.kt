package io.github.longbowxxx.readingtracker.domain.model

import java.time.Instant

/**
 * 巻に対する読書状態。
 *
 * **この列挙に第3の値を追加してはならない**（憲法 原則II）。
 * 「離脱」は作品単位の状態であり、巻の状態ではない。今回のスコープでは保持しない。
 * 「購入済み」「所有」「蔵書」「未購入」に相当する値も導入しない。
 */
enum class ReadingStatus {
    /** 読了。その巻を最後まで読み終えた。 */
    READ,

    /** 中断。読み始めたが最後まで読んでいない。ページ数・話数は記録しない（FR-011）。 */
    PAUSED,
}

/**
 * 店舗が貼付しているシールの棚番号。
 *
 * 書式は店舗ごとに異なりうるため検証しない（FR-018）。
 * 未入力は `ShelfNumber?` の null で表す（FR-017）。
 */
@JvmInline
value class ShelfNumber(val value: String) {
    override fun toString(): String = value
}

/** 個々の漫画喫茶。棚番号の所属先となる。 */
data class Store(val id: Long, val name: String)

/** シリーズ単位の漫画作品。暫定名のみで成立する状態を取りうる（FR-008）。 */
data class Work(
    val id: Long,
    val title: String,
    val matchKey: String,
    val author: String? = null,
    val publisher: String? = null,
    val isProvisional: Boolean = false,
)

/** 作品を構成する個々の単行本。ISBN を持たない暫定記録も表現できる。 */
data class Volume(
    val id: Long,
    val workId: Long,
    val volumeNumber: Int?,
    val isbn: Isbn?,
    val displayTitle: String,
    val publishedDate: String? = null,
)

/** 作品の新規登録に必要な値。 */
data class NewWork(
    val title: String,
    val matchKey: String,
    val author: String? = null,
    val publisher: String? = null,
    val isProvisional: Boolean = false,
)

/** 巻の新規登録に必要な値。 */
data class NewVolume(
    val workId: Long,
    val volumeNumber: Int?,
    val isbn: Isbn?,
    val displayTitle: String,
    val publishedDate: String? = null,
)

/** 巻の参照。巻番号は不明でありうる。 */
data class VolumeRef(val volumeId: Long, val volumeNumber: Int?)

/**
 * 配架レコードの読み取り用スナップショット。
 *
 * **単一の店舗・単一の作品に属するものだけをまとめて扱うこと。** 店舗 ID を持たないのは、
 * ドメイン関数が他店舗のレコードへ到達しえないようにするため（FR-014）。
 * [shelfNumber] が null なら棚番号は未入力。行そのものは「その店でその巻を記録した」事実を表す。
 */
data class PlacementSnapshot(val volumeId: Long, val volumeNumber: Int?, val shelfNumber: ShelfNumber?, val updatedAt: Instant)

/**
 * 読書記録の読み取り用スナップショット。
 *
 * 読書状態は「作品 × 巻」に対して1つであり、店舗をまたいで共有される。
 */
data class ReadingSnapshot(
    val volumeId: Long,
    val volumeNumber: Int?,
    val status: ReadingStatus,
    val note: String? = null,
    val recordedAt: Instant? = null,
)
