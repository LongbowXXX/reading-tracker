package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * openBD 応答のパース（research.md R-001）。
 *
 * 実ネットワークへはアクセスしない。応答サンプルをリソースに固定して解析だけを検証する。
 */
class OpenBdParserTest {
    private val isbn = Isbn.parse("9784088807232").getOrThrow()

    private fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).bufferedReader().use { it.readText() }

    @Test
    fun `書誌情報を取り出す`() {
        val result = OpenBdResponseParser.parse(load("openbd_found.json"), isbn)

        assertTrue(result is BibliographyResult.Found)
        val record = (result as BibliographyResult.Found).record
        assertEquals("ONE PIECE 87", record.rawTitle)
        assertEquals("尾田栄一郎／著", record.author)
        assertEquals("集英社", record.publisher)
        assertEquals("20171204", record.publishedDate)
        assertEquals("openBD", record.sourceName)
    }

    @Test
    fun `巻数が独立項目にあれば取り込む`() {
        val result = OpenBdResponseParser.parse(load("openbd_found.json"), isbn)

        assertEquals(87, (result as BibliographyResult.Found).record.volumeNumber)
    }

    @Test
    fun `該当なしの null 要素は NotFound に写像する`() {
        val result = OpenBdResponseParser.parse(load("openbd_not_found.json"), isbn)

        assertEquals(BibliographyResult.NotFound, result)
    }

    @Test
    fun `空配列は NotFound に写像する`() {
        assertEquals(BibliographyResult.NotFound, OpenBdResponseParser.parse("[]", isbn))
    }

    @Test
    fun `壊れた JSON は例外にせず Unavailable を返す`() {
        val result = OpenBdResponseParser.parse("{壊れている", isbn)

        assertTrue(result is BibliographyResult.Unavailable)
    }
}
