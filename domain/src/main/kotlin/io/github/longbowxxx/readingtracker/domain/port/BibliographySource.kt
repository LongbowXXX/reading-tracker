package io.github.longbowxxx.readingtracker.domain.port

import io.github.longbowxxx.readingtracker.domain.model.Isbn

/**
 * ISBN から書誌情報を取得する経路。
 *
 * インターフェースをドメイン層に置くのは、取得経路の差し替え（openBD 単独／NDL 単独／
 * 将来の別経路）が UI とドメインへ波及しないようにするため（research.md R-001）。
 *
 * 実装は例外を投げてはならない。通信失敗は [BibliographyResult.Unavailable] として返す。
 */
interface BibliographySource {
    suspend fun lookup(isbn: Isbn): BibliographyResult
}

/** 書誌情報の取得結果。 */
sealed interface BibliographyResult {
    /** 取得できた。 */
    data class Found(val record: BibliographyRecord) : BibliographyResult

    /** 経路は生きているが該当が無い。 */
    data object NotFound : BibliographyResult

    /** 圏外・タイムアウト・障害。**エラー画面ではなく手入力へ遷移すること**（FR-007）。 */
    data class Unavailable(val cause: Throwable) : BibliographyResult
}

/**
 * 取得した書誌情報。
 *
 * @property rawTitle 巻数表記を含みうる、取得したままのタイトル
 * @property volumeNumber 経路が独立項目として返した場合のみ設定される。通常は null で、
 *   上位が `parseVolumeTitle()` で [rawTitle] から抽出する
 * @property sourceName 表示用ではなく診断用（"openBD" / "NDL"）
 */
data class BibliographyRecord(
    val isbn: Isbn,
    val rawTitle: String,
    val author: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val volumeNumber: Int? = null,
    val sourceName: String,
)
