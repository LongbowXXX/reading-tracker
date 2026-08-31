package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyRecord
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/**
 * 国立国会図書館サーチ（SRU）の応答を解析する。
 *
 * 応答スキーマの細部は実装時に一次情報で確認する必要がある（research.md の未解決事項）。
 * そのため、**名前空間接頭辞に依存せず要素のローカル名で拾う**寛容な実装にしてある。
 * `dcterms:title` でも `dc:title` でも同じように扱える。
 */
object NdlResponseParser {
    const val SOURCE_NAME: String = "NDL"

    private const val TAG_NUMBER_OF_RECORDS = "numberOfRecords"
    private const val TAG_TITLE = "title"
    private const val TAG_VOLUME = "volume"
    private const val TAG_CREATOR = "creator"
    private const val TAG_PUBLISHER = "publisher"
    private const val TAG_DATE = "date"

    fun parse(body: String, isbn: Isbn): BibliographyResult = try {
        val values = readFirstValues(body)

        val numberOfRecords = values[TAG_NUMBER_OF_RECORDS]?.trim()?.toIntOrNull()
        val title = values[TAG_TITLE]?.trim()

        when {
            numberOfRecords == 0 -> BibliographyResult.NotFound

            // 件数もタイトルも読み取れない応答は「該当なし」ではなく「解釈できない」。
            // NotFound にすると、経路の障害を蔵書なしと誤って伝えてしまう
            numberOfRecords == null && title.isNullOrBlank() ->
                BibliographyResult.Unavailable(IOException("SRU 応答を解釈できません"))

            title.isNullOrBlank() -> BibliographyResult.NotFound

            else ->
                BibliographyResult.Found(
                    BibliographyRecord(
                        isbn = isbn,
                        rawTitle = title,
                        author = values[TAG_CREATOR]?.trim()?.takeIf { it.isNotBlank() },
                        publisher = values[TAG_PUBLISHER]?.trim()?.takeIf { it.isNotBlank() },
                        publishedDate = values[TAG_DATE]?.trim()?.takeIf { it.isNotBlank() },
                        volumeNumber = values[TAG_VOLUME]?.trim()?.toIntOrNull(),
                        sourceName = SOURCE_NAME,
                    ),
                )
        }
    } catch (e: Exception) {
        // 解析できない応答は障害として扱い、手入力へ落とす（FR-007）
        BibliographyResult.Unavailable(e)
    }

    /** 対象タグのうち、最初に現れた値だけを拾う。2件目以降のレコードは見ない。 */
    private fun readFirstValues(body: String): Map<String, String> {
        val targets = setOf(TAG_NUMBER_OF_RECORDS, TAG_TITLE, TAG_VOLUME, TAG_CREATOR, TAG_PUBLISHER, TAG_DATE)
        val values = mutableMapOf<String, String>()

        val parser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser().apply {
                setInput(StringReader(body))
            }

        var event = parser.eventType
        var currentTag: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val localName = parser.name.substringAfterLast(':')
                    currentTag = localName.takeIf { it in targets && it !in values }
                }

                XmlPullParser.TEXT -> {
                    val tag = currentTag
                    val text = parser.text
                    if (tag != null && !text.isNullOrBlank()) {
                        values[tag] = text
                    }
                }

                XmlPullParser.END_TAG -> currentTag = null
            }
            event = parser.next()
        }

        return values
    }
}
