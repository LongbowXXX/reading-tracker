package io.github.longbowxxx.readingtracker.domain.title

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * タイトルからの巻数抽出と照合キーの生成（FR-027）。
 *
 * 誤照合の完全な排除は目標としない。確認画面で利用者が修正・分離できることを前提とする
 * （spec.md の Assumptions）。ここで固定するのは「同一シリーズの異なる巻から同一の
 * 照合キーが得られること」である。
 */
class VolumeTitleParserTest {
    @Test
    @DisplayName("同一シリーズの異なる巻から同一の照合キーが得られる")
    fun `同一シリーズは同じ照合キーになる`() {
        val keys =
            listOf(
                "ワンピース 12",
                "ワンピース 13",
                "ワンピース（14）",
                "ワンピース 第15巻",
                "ワンピース 16巻",
            ).map { parseVolumeTitle(it).matchKey }

        assertEquals(1, keys.toSet().size, "照合キーが揃っていない: $keys")
    }

    @Test
    fun `半角空白区切りの巻数を抽出する`() {
        val parsed = parseVolumeTitle("ワンピース 12")
        assertEquals(12, parsed.volumeNumber)
        assertEquals("ワンピース", parsed.workTitle)
    }

    @Test
    fun `全角括弧の巻数を抽出する`() {
        assertEquals(12, parseVolumeTitle("ワンピース（12）").volumeNumber)
    }

    @Test
    fun `半角括弧の巻数を抽出する`() {
        assertEquals(12, parseVolumeTitle("ワンピース(12)").volumeNumber)
    }

    @Test
    fun `第N巻の表記から巻数を抽出する`() {
        assertEquals(12, parseVolumeTitle("ワンピース 第12巻").volumeNumber)
    }

    @Test
    fun `N巻の表記から巻数を抽出する`() {
        assertEquals(12, parseVolumeTitle("ワンピース 12巻").volumeNumber)
    }

    @Test
    fun `全角数字の巻数を抽出する`() {
        assertEquals(12, parseVolumeTitle("ワンピース　１２").volumeNumber)
    }

    @Test
    fun `巻数表記が無ければ巻数は不明とする`() {
        val parsed = parseVolumeTitle("よつばと！")
        assertNull(parsed.volumeNumber)
        assertEquals("よつばと！", parsed.workTitle)
    }

    @Test
    fun `照合キーは空白を除去する`() {
        assertEquals(
            parseVolumeTitle("進撃 の 巨人 1").matchKey,
            parseVolumeTitle("進撃の巨人 2").matchKey,
        )
    }

    @Test
    fun `照合キーは全角英数字を半角に寄せる`() {
        assertEquals(
            parseVolumeTitle("ＡＫＩＲＡ 1").matchKey,
            parseVolumeTitle("AKIRA 2").matchKey,
        )
    }

    @Test
    fun `タイトル中の数字を巻数と誤認しない`() {
        // 巻数表記は末尾にのみ現れるものとして扱う
        val parsed = parseVolumeTitle("20世紀少年")
        assertNull(parsed.volumeNumber)
        assertEquals("20世紀少年", parsed.workTitle)
    }

    @Test
    fun `空文字を渡しても例外にしない`() {
        val parsed = parseVolumeTitle("")
        assertNull(parsed.volumeNumber)
        assertEquals("", parsed.matchKey)
    }
}
