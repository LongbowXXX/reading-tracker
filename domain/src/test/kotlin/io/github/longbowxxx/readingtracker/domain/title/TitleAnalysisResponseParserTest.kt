package io.github.longbowxxx.readingtracker.domain.title

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * オンデバイス AI の応答の解釈（Issue #4）。
 *
 * 推論そのものは実機でしか試せないが、**応答をどう解釈するかはここで仕様として固定する**
 * （憲法 原則III）。生成モデルの出力は整った JSON とは限らないため、寛容に読む一方で、
 * 元のタイトルから導けない作品名は捨てる。
 */
class TitleAnalysisResponseParserTest {
    @Test
    fun `作品名と巻数を読み取る`() {
        val analysis = parseTitleAnalysisResponse("""{"work": "進撃の巨人", "volume": 34}""", "進撃の巨人 34")

        assertEquals("進撃の巨人", analysis?.workTitle)
        assertEquals(34, analysis?.volumeNumber)
    }

    @Test
    @DisplayName("巻数が null なら巻数は不明とする")
    fun `巻数がnullなら不明とする`() {
        val analysis = parseTitleAnalysisResponse("""{"work": "ゴルゴ13", "volume": null}""", "ゴルゴ13")

        assertEquals("ゴルゴ13", analysis?.workTitle)
        assertNull(analysis?.volumeNumber)
    }

    @Test
    @DisplayName("コードブロックや前後の説明が付いていても読み取れる")
    fun `前後に余分な出力があっても読み取れる`() {
        val response =
            """
            Here is the result:
            ```json
            {"work": "拳児", "volume": 2}
            ```
            """.trimIndent()

        val analysis = parseTitleAnalysisResponse(response, "拳児2")

        assertEquals("拳児", analysis?.workTitle)
        assertEquals(2, analysis?.volumeNumber)
    }

    @Test
    fun `巻数が文字列で返っても読み取れる`() {
        assertEquals(5, parseTitleAnalysisResponse("""{"work":"化物語","volume":"5"}""", "化物語. 13")?.volumeNumber)
    }

    @Test
    fun `エスケープされた引用符を戻す`() {
        val analysis = parseTitleAnalysisResponse("""{"work": "\"ハーメルン\"", "volume": 1}""", "\"ハーメルン\" 1")

        assertEquals("\"ハーメルン\"", analysis?.workTitle)
    }

    @Test
    @DisplayName("並列書名を落とした作品名は受け入れる")
    fun `文字を削っただけの作品名は受け入れる`() {
        val analysis =
            parseTitleAnalysisResponse(
                """{"work": "乾と巽 : ザバイカル戦記", "volume": 5}""",
                "乾と巽 = INUI and TATSUMI : ザバイカル戦記. 5",
            )

        assertEquals("乾と巽 : ザバイカル戦記", analysis?.workTitle)
        assertEquals(5, analysis?.volumeNumber)
    }

    @Test
    @DisplayName("翻訳された作品名は信用しない")
    fun `元のタイトルから導けない作品名は捨てる`() {
        // そのまま採用すると照合キーが壊れ、同じ作品が分裂する
        assertNull(parseTitleAnalysisResponse("""{"work": "Attack on Titan", "volume": 34}""", "進撃の巨人 34"))
    }

    @Test
    fun `作り話の作品名は捨てる`() {
        assertNull(parseTitleAnalysisResponse("""{"work": "ドラえもん", "volume": 1}""", "進撃の巨人 34"))
    }

    @Test
    fun `巻数だけが読めない場合は作品名を活かす`() {
        val analysis = parseTitleAnalysisResponse("""{"work": "進撃の巨人", "volume": "不明"}""", "進撃の巨人 34")

        assertEquals("進撃の巨人", analysis?.workTitle)
        assertNull(analysis?.volumeNumber)
    }

    @Test
    fun `範囲外の巻数は誤読とみなす`() {
        assertNull(parseTitleAnalysisResponse("""{"work": "進撃の巨人", "volume": 0}""", "進撃の巨人 34")?.volumeNumber)
    }

    @Test
    fun `応答が空なら判定できないとする`() {
        assertNull(parseTitleAnalysisResponse(null, "進撃の巨人 34"))
        assertNull(parseTitleAnalysisResponse("", "進撃の巨人 34"))
        assertNull(parseTitleAnalysisResponse("すみません、わかりません", "進撃の巨人 34"))
    }

    @Test
    fun `作品名が空なら判定できないとする`() {
        assertNull(parseTitleAnalysisResponse("""{"work": "", "volume": 34}""", "進撃の巨人 34"))
    }
}
