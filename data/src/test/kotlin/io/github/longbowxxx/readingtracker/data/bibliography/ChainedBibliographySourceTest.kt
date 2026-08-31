package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyRecord
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 書誌取得の連鎖規則（contracts/bibliography-source.md）。
 *
 * フェイク実装を並べて検証し、**ネットワークへはアクセスしない**。
 */
class ChainedBibliographySourceTest {
    private val isbn = Isbn.parse("9784088807232").getOrThrow()

    private class FakeSource(private val name: String, private val result: BibliographyResult) : BibliographySource {
        var callCount: Int = 0
            private set

        override suspend fun lookup(isbn: Isbn): BibliographyResult {
            callCount++
            return result
        }
    }

    private fun found(name: String) = BibliographyResult.Found(BibliographyRecord(isbn = isbn, rawTitle = "タイトル", sourceName = name))

    @Test
    fun `最初の経路で見つかれば後続を呼ばない`() = runTest {
        val first = FakeSource("openBD", found("openBD"))
        val second = FakeSource("NDL", found("NDL"))

        val result = ChainedBibliographySource(listOf(first, second)).lookup(isbn)

        assertEquals("openBD", (result as BibliographyResult.Found).record.sourceName)
        assertEquals(1, first.callCount)
        assertEquals(0, second.callCount)
    }

    @Test
    fun `NotFound なら次の経路へ進む`() = runTest {
        val first = FakeSource("openBD", BibliographyResult.NotFound)
        val second = FakeSource("NDL", found("NDL"))

        val result = ChainedBibliographySource(listOf(first, second)).lookup(isbn)

        assertEquals("NDL", (result as BibliographyResult.Found).record.sourceName)
        assertEquals(1, second.callCount)
    }

    @Test
    fun `Unavailable でも次の経路へ進む`() = runTest {
        val first = FakeSource("openBD", BibliographyResult.Unavailable(IOException("圏外")))
        val second = FakeSource("NDL", found("NDL"))

        val result = ChainedBibliographySource(listOf(first, second)).lookup(isbn)

        assertEquals("NDL", (result as BibliographyResult.Found).record.sourceName)
    }

    @Test
    fun `すべて NotFound なら NotFound を返す`() = runTest {
        val sources =
            listOf(
                FakeSource("openBD", BibliographyResult.NotFound),
                FakeSource("NDL", BibliographyResult.NotFound),
            )

        assertEquals(BibliographyResult.NotFound, ChainedBibliographySource(sources).lookup(isbn))
    }

    @Test
    fun `1件でも Unavailable があれば Unavailable を返す`() = runTest {
        val sources =
            listOf(
                FakeSource("openBD", BibliographyResult.Unavailable(IOException("圏外"))),
                FakeSource("NDL", BibliographyResult.NotFound),
            )

        assertTrue(ChainedBibliographySource(sources).lookup(isbn) is BibliographyResult.Unavailable)
    }

    @Test
    fun `経路が空なら NotFound を返す`() = runTest {
        assertEquals(BibliographyResult.NotFound, ChainedBibliographySource(emptyList()).lookup(isbn))
    }

    @Test
    fun `経路が例外を投げても Unavailable として扱い落ちない`() = runTest {
        val throwing =
            object : BibliographySource {
                override suspend fun lookup(isbn: Isbn): BibliographyResult = throw IllegalStateException("想定外")
            }
        val second = FakeSource("NDL", found("NDL"))

        val result = ChainedBibliographySource(listOf(throwing, second)).lookup(isbn)

        assertEquals("NDL", (result as BibliographyResult.Found).record.sourceName)
    }
}
