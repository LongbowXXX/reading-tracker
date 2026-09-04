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
 *
 * 表記の例は openBD 1,535 件・NDL 230 件の実データから採った（Issue #4）。
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

    // --- 以下 Issue #4 で実データから追加した表記 ---

    @Test
    @DisplayName("末尾のピリオドの有無で照合キーが割れない")
    fun `末尾の区切り記号を照合キーから落とす`() {
        // openBD は「チェンソーマン 5」、NDL は「チェンソーマン. 5」を返す。同じ本である
        assertEquals(
            parseVolumeTitle("チェンソーマン 5").matchKey,
            parseVolumeTitle("チェンソーマン. 6").matchKey,
        )
    }

    @Test
    fun `巻Nの表記から巻数を抽出する`() {
        val parsed = parseVolumeTitle("ONE PIECE. 巻94")
        assertEquals(94, parsed.volumeNumber)
        assertEquals("onepiece", parsed.matchKey)
    }

    @Test
    fun `巻ノNと巻之Nの表記から巻数を抽出する`() {
        assertEquals(9, parseVolumeTitle("BORUTO : NARUTO NEXT GENERATIONS 巻ノ9").volumeNumber)
        assertEquals(3, parseVolumeTitle("るろうに剣心-明治剣客浪漫譚・北海道編- 巻之3").volumeNumber)
    }

    @Test
    fun `volN表記から巻数を抽出する`() {
        assertEquals(8, parseVolumeTitle("アクタージュ = act-age vol.8").volumeNumber)
        assertEquals(25, parseVolumeTitle("僕のヒーローアカデミア = MY HERO ACADEMIA. Vol.25").volumeNumber)
        assertEquals(1, parseVolumeTitle("激辛課長 : New Edition VOLUME.1").volumeNumber)
    }

    @Test
    fun `そのN表記から巻数を抽出する`() {
        assertEquals(1, parseVolumeTitle("月曜日のたわわ. その1").volumeNumber)
    }

    @Test
    fun `シャープN表記から巻数を抽出する`() {
        assertEquals(2, parseVolumeTitle("ブーツレグ = BooTsLeG. #2").volumeNumber)
    }

    @Test
    @DisplayName("並列書名の有無で照合キーが割れない")
    fun `照合キーは並列書名を落とす`() {
        // AI 経路は「バイオメガ」を返し、規則ベースは「バイオメガ = BIOMEGA」を返す。
        // 照合キー側で吸収しないと、経路の違いだけで別作品になる
        assertEquals(
            buildMatchKey("バイオメガ"),
            parseVolumeTitle("バイオメガ = BIOMEGA. 1").matchKey,
        )
    }

    @Test
    fun `照合キーは大文字小文字を揃える`() {
        assertEquals(
            parseVolumeTitle("act-age 1").matchKey,
            parseVolumeTitle("Act-Age 2").matchKey,
        )
    }

    @Test
    @DisplayName("先頭の等号は並列書名の区切りとみなさない")
    fun `先頭の等号は落とさない`() {
        assertEquals("=love", buildMatchKey("=LOVE"))
    }

    @Test
    fun `感嘆符と括弧は作品名の一部として残す`() {
        assertEquals("よつばと!", buildMatchKey("よつばと！"))
        assertEquals("怪物(けもの)事変", buildMatchKey("怪物(けもの)事変"))
    }
}
