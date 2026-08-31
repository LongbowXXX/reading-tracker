package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyRecord
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * openBD の応答を解析する。
 *
 * 応答は要素数が問い合わせた ISBN 数と一致する配列で、該当が無い要素は `null` になる。
 * `summary` だけを見る。ONIX の詳細構造に依存しないほうが、仕様変更に強い。
 */
object OpenBdResponseParser {
    const val SOURCE_NAME: String = "openBD"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, isbn: Isbn): BibliographyResult = try {
        val items = json.decodeFromString<List<OpenBdItem?>>(body)
        val summary = items.firstOrNull()?.summary
        if (summary == null) {
            BibliographyResult.NotFound
        } else {
            BibliographyResult.Found(
                BibliographyRecord(
                    isbn = isbn,
                    rawTitle = summary.title.orEmpty(),
                    author = summary.author?.takeIf { it.isNotBlank() },
                    publisher = summary.publisher?.takeIf { it.isNotBlank() },
                    publishedDate = summary.pubdate?.takeIf { it.isNotBlank() },
                    // 巻数が独立項目で返る場合のみ採用する。返らなければ上位がタイトルから抽出する
                    volumeNumber = summary.volume?.trim()?.toIntOrNull(),
                    sourceName = SOURCE_NAME,
                ),
            )
        }
    } catch (e: Exception) {
        // 解析できない応答は障害として扱い、手入力へ落とす（FR-007）
        BibliographyResult.Unavailable(e)
    }

    @Serializable
    private data class OpenBdItem(val summary: OpenBdSummary? = null)

    @Serializable
    private data class OpenBdSummary(
        val isbn: String? = null,
        val title: String? = null,
        val volume: String? = null,
        val series: String? = null,
        val publisher: String? = null,
        val pubdate: String? = null,
        val author: String? = null,
    )
}
