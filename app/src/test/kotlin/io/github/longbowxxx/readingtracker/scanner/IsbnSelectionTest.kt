package io.github.longbowxxx.readingtracker.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 読み取った値のうち何を採用するか（Issue #1）。
 *
 * 書籍バーコードは上段が ISBN、下段が日本図書コード（192 始まりの分類・価格コード）である。
 * 下段を読んでも**採用しない**ことが本 Issue の受け入れ条件であり、ここで固定する。
 */
class IsbnSelectionTest {
    @Test
    fun `上段の ISBN を採用する`() {
        assertEquals("9784088807232", selectIsbn(listOf("9784088807232"))?.value)
    }

    @Test
    fun `下段の日本図書コード（192 始まり）は採用しない`() {
        // 1920979018006 は書籍バーコード下段の分類・価格コード。EAN-13 としては成立する
        assertNull(selectIsbn(listOf("1920979018006")))
    }

    @Test
    fun `上段と下段が同時に読めた場合は上段を採用する`() {
        assertEquals("9784088807232", selectIsbn(listOf("1920979018006", "9784088807232"))?.value)
    }

    @Test
    fun `チェックディジットが一致しない誤読は採用しない`() {
        assertNull(selectIsbn(listOf("9784088807233")))
    }

    @Test
    fun `979 で始まる ISBN も採用する`() {
        assertEquals("9791234567896", selectIsbn(listOf("9791234567896"))?.value)
    }

    @Test
    fun `値が読めなかったフレームでは採用しない`() {
        assertNull(selectIsbn(emptyList()))
        assertNull(selectIsbn(listOf(null)))
    }
}
