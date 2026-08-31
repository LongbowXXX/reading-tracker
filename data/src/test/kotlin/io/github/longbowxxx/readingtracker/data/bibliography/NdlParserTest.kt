package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 国立国会図書館サーチ（SRU）応答のパース（research.md R-001）。
 *
 * 実ネットワークへはアクセスしない。XmlPullParser が Android の実装であるため
 * Robolectric 上で動かす。
 *
 * 応答スキーマの細部は実装フェーズで一次情報の確認が必要（research.md の未解決事項）。
 * パーサは名前空間接頭辞に依存せず、要素のローカル名で拾う実装にしてある。
 */
@RunWith(RobolectricTestRunner::class)
class NdlParserTest {
    private val isbn = Isbn.parse("9784088807232").getOrThrow()

    private fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).bufferedReader().use { it.readText() }

    @Test
    fun `書誌情報を取り出す`() {
        val result = NdlResponseParser.parse(load("ndl_found.xml"), isbn)

        assertTrue(result is BibliographyResult.Found)
        val record = (result as BibliographyResult.Found).record
        assertEquals("ONE PIECE", record.rawTitle)
        assertEquals("尾田栄一郎 著", record.author)
        assertEquals("集英社", record.publisher)
        assertEquals("2017.12", record.publishedDate)
        assertEquals("NDL", record.sourceName)
    }

    @Test
    fun `巻次が独立項目にあれば取り込む`() {
        val result = NdlResponseParser.parse(load("ndl_found.xml"), isbn)

        assertEquals(87, (result as BibliographyResult.Found).record.volumeNumber)
    }

    @Test
    fun `ヒット0件は NotFound に写像する`() {
        assertEquals(BibliographyResult.NotFound, NdlResponseParser.parse(load("ndl_not_found.xml"), isbn))
    }

    @Test
    fun `壊れた XML は例外にせず Unavailable を返す`() {
        val result = NdlResponseParser.parse("<searchRetrieveResponse><records>", isbn)

        assertTrue(result is BibliographyResult.Unavailable)
    }
}
