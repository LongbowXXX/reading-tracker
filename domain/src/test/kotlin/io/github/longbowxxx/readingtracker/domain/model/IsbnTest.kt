package io.github.longbowxxx.readingtracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ISBN の検証と正規化（FR-004, FR-005）。
 * 契約は contracts/domain-api.md の表に対応する。
 */
class IsbnTest {
    @Nested
    @DisplayName("13桁 ISBN")
    inner class Isbn13 {
        @Test
        fun `チェックディジットが正しければ受け付ける`() {
            val result = Isbn.parse("9784088807232")
            assertTrue(result.isSuccess)
            assertEquals("9784088807232", result.getOrThrow().value)
        }

        @Test
        fun `チェックディジットが誤っていれば拒否する`() {
            val result = Isbn.parse("9784088807233")
            assertTrue(result.isFailure)
            assertInstanceOf(IsbnFormatException.InvalidCheckDigit::class.java, result.exceptionOrNull())
        }
    }

    @Nested
    @DisplayName("10桁 ISBN")
    inner class Isbn10 {
        @Test
        fun `13桁へ変換して受け付ける`() {
            val result = Isbn.parse("4088807235")
            assertTrue(result.isSuccess)
            assertEquals("9784088807232", result.getOrThrow().value)
        }

        @Test
        fun `末尾が X でも受け付ける`() {
            val result = Isbn.parse("486152069X")
            assertTrue(result.isSuccess)
            assertEquals("9784861520693", result.getOrThrow().value)
        }

        @Test
        fun `末尾の小文字 x も受け付ける`() {
            assertTrue(Isbn.parse("486152069x").isSuccess)
        }

        @Test
        fun `チェックディジットが誤っていれば拒否する`() {
            val result = Isbn.parse("4088807236")
            assertTrue(result.isFailure)
            assertInstanceOf(IsbnFormatException.InvalidCheckDigit::class.java, result.exceptionOrNull())
        }
    }

    @Nested
    @DisplayName("入力の正規化")
    inner class Normalization {
        @Test
        fun `ハイフンを除去してから判定する`() {
            assertEquals("9784088807232", Isbn.parse("978-4-08-880723-2").getOrThrow().value)
        }

        @Test
        fun `前後および途中の空白を除去してから判定する`() {
            assertEquals("9784088807232", Isbn.parse("  978 4088 807232 ").getOrThrow().value)
        }
    }

    @Nested
    @DisplayName("不正な入力")
    inner class Invalid {
        @Test
        fun `桁数が10でも13でもなければ拒否する`() {
            val result = Isbn.parse("12345")
            assertTrue(result.isFailure)
            assertInstanceOf(IsbnFormatException.InvalidLength::class.java, result.exceptionOrNull())
        }

        @Test
        fun `空文字を拒否する`() {
            assertTrue(Isbn.parse("").isFailure)
        }

        @Test
        fun `数字以外を含む場合は拒否する`() {
            val result = Isbn.parse("97840888072AB")
            assertTrue(result.isFailure)
            assertInstanceOf(IsbnFormatException.InvalidCharacter::class.java, result.exceptionOrNull())
        }

        @Test
        fun `X が末尾以外にあれば拒否する`() {
            assertTrue(Isbn.parse("48X152069X").isFailure)
        }
    }
}
