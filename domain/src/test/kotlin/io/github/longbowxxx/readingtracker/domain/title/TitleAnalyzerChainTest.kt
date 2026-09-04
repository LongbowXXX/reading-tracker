package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 解析経路の連鎖とキャッシュ（Issue #4）。
 *
 * AI 経路は対応端末が限られ、推論も失敗しうる。**どの経路が落ちても記録は完了する**
 * ことをここで固定する（憲法 原則VI）。
 */
class TitleAnalyzerChainTest {
    private val ruleBased = RuleBasedTitleAnalyzer()

    @Test
    fun `先頭の経路が答えればそれを採用する`() = runTest {
        val chain = ChainedTitleAnalyzer(listOf(FixedAnalyzer(TitleAnalysis("拳児", 2)), ruleBased))

        assertEquals(TitleAnalysis("拳児", 2), chain.analyze("拳児2"))
    }

    @Test
    @DisplayName("先頭の経路が判定できなければ次の経路へ進む")
    fun `nullを返す経路は飛ばす`() = runTest {
        val chain = ChainedTitleAnalyzer(listOf(FixedAnalyzer(null), ruleBased))

        assertEquals(TitleAnalysis("ワンピース", 12), chain.analyze("ワンピース 12"))
    }

    @Test
    fun `例外を投げる経路があっても次の経路で答える`() = runTest {
        val chain = ChainedTitleAnalyzer(listOf(ThrowingAnalyzer(), ruleBased))

        assertEquals(TitleAnalysis("ワンピース", 12), chain.analyze("ワンピース 12"))
    }

    @Test
    @DisplayName("時間切れの経路は待たずに次へ進む")
    fun `遅い経路は打ち切る`() = runTest {
        val chain =
            ChainedTitleAnalyzer(
                listOf(SlowAnalyzer(delayMillis = 10_000), ruleBased),
                perAnalyzerTimeoutMillis = 100,
            )

        assertEquals(TitleAnalysis("ワンピース", 12), chain.analyze("ワンピース 12"))
    }

    @Test
    fun `すべての経路が判定できなければnullを返す`() = runTest {
        val chain = ChainedTitleAnalyzer(listOf(FixedAnalyzer(null), FixedAnalyzer(null)))

        assertNull(chain.analyze("ワンピース 12"))
    }

    @Test
    @DisplayName("どの経路も判定できなければ規則ベースの結果へ落ちる")
    fun `フォールバックで必ず結果を得る`() = runTest {
        val analyzer = ChainedTitleAnalyzer(listOf(FixedAnalyzer(null)))

        val parsed = analyzer.analyzeOrFallback("ワンピース 12")

        assertEquals("ワンピース", parsed.workTitle)
        assertEquals(12, parsed.volumeNumber)
    }

    @Test
    @DisplayName("同じタイトルの解析は1度しか行わない")
    fun `結果をキャッシュする`() = runTest {
        val counting = CountingAnalyzer(TitleAnalysis("ワンピース", 12))
        val caching = CachingTitleAnalyzer(counting)

        caching.analyze("ワンピース 12")
        caching.analyze("ワンピース 12")

        assertEquals(1, counting.callCount)
    }

    @Test
    @DisplayName("判定できなかったことも覚える")
    fun `nullの結果もキャッシュする`() = runTest {
        val counting = CountingAnalyzer(null)
        val caching = CachingTitleAnalyzer(counting)

        caching.analyze("ワンピース 12")
        caching.analyze("ワンピース 12")

        assertEquals(1, counting.callCount)
    }

    @Test
    fun `古い項目はキャッシュから追い出す`() = runTest {
        val counting = CountingAnalyzer(TitleAnalysis("作品", 1))
        val caching = CachingTitleAnalyzer(counting, maxEntries = 2)

        caching.analyze("A 1")
        caching.analyze("B 1")
        caching.analyze("C 1")
        caching.analyze("A 1")

        assertEquals(4, counting.callCount)
    }

    private class FixedAnalyzer(private val result: TitleAnalysis?) : TitleAnalyzer {
        override suspend fun analyze(rawTitle: String): TitleAnalysis? = result
    }

    private class ThrowingAnalyzer : TitleAnalyzer {
        override suspend fun analyze(rawTitle: String): TitleAnalysis = error("経路の障害")
    }

    private class SlowAnalyzer(private val delayMillis: Long) : TitleAnalyzer {
        override suspend fun analyze(rawTitle: String): TitleAnalysis {
            delay(delayMillis)
            return TitleAnalysis(rawTitle, null)
        }
    }

    private class CountingAnalyzer(private val result: TitleAnalysis?) : TitleAnalyzer {
        var callCount: Int = 0
            private set

        override suspend fun analyze(rawTitle: String): TitleAnalysis? {
            callCount++
            return result
        }
    }
}
