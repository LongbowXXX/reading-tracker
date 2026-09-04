package io.github.longbowxxx.readingtracker.domain.title

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * オンデバイス AI へ渡すプロンプトの組み立て（Issue #4）。
 *
 * プロンプトはこの機能の仕様そのものであり、推論を実機で試す前に形を固定しておく。
 */
class TitleAnalysisPromptTest {
    @Test
    fun `解析対象のタイトルが末尾の入力欄に入る`() {
        val prompt = buildTitleAnalysisPrompt("進撃の巨人 34")

        assertTrue(prompt.endsWith("Input: 進撃の巨人 34\nOutput:"), "プロンプトの末尾が想定と違う: $prompt")
    }

    @Test
    @DisplayName("巻数と紛らわしい作品名の例を含む")
    fun `区切りの無い数字の例を含む`() {
        val prompt = buildTitleAnalysisPrompt("拳児2")

        // ゴルゴ13 は作品名、拳児2 は2巻。この区別が AI を使う理由そのもの
        assertTrue(prompt.contains("ゴルゴ13"), "作品名に数字を含む例が抜けている")
    }

    @Test
    fun `応答の形式を指定している`() {
        val prompt = buildTitleAnalysisPrompt("進撃の巨人 34")

        assertTrue(prompt.contains(""""work""""), "work の指定が抜けている")
        assertTrue(prompt.contains(""""volume""""), "volume の指定が抜けている")
    }
}
