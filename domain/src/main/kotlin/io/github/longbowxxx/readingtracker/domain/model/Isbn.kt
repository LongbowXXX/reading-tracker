package io.github.longbowxxx.readingtracker.domain.model

/**
 * ISBN。常に13桁・ハイフンなしに正規化して保持する。
 *
 * 10桁 ISBN（2007年以前の書籍）は13桁へ変換して受け付ける（FR-005）。
 * 生成は [parse] のみを通す。妥当性を検証していない文字列がこの型になることはない。
 */
@JvmInline
value class Isbn private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        /**
         * 生の入力文字列から ISBN を作る。ハイフンと空白は除去してから判定する（FR-004）。
         *
         * 失敗時の例外は [IsbnFormatException] のいずれか。呼び出し側は例外の種別で
         * 利用者への案内を出し分けられる。
         */
        fun parse(raw: String): Result<Isbn> {
            val normalized = raw.filterNot { it.isWhitespace() || it == '-' || it == '‐' || it == '−' }.uppercase()

            return when (normalized.length) {
                13 -> parseIsbn13(normalized)
                10 -> parseIsbn10(normalized)
                else -> Result.failure(IsbnFormatException.InvalidLength(normalized.length))
            }
        }

        private fun parseIsbn13(normalized: String): Result<Isbn> {
            if (!normalized.all { it.isDigit() }) {
                return Result.failure(IsbnFormatException.InvalidCharacter(normalized))
            }
            val digits = normalized.map { it - '0' }
            if (digits[12] != checkDigit13(digits.subList(0, 12))) {
                return Result.failure(IsbnFormatException.InvalidCheckDigit(normalized))
            }
            return Result.success(Isbn(normalized))
        }

        private fun parseIsbn10(normalized: String): Result<Isbn> {
            val body = normalized.substring(0, 9)
            val last = normalized[9]
            if (!body.all { it.isDigit() } || !(last.isDigit() || last == 'X')) {
                return Result.failure(IsbnFormatException.InvalidCharacter(normalized))
            }

            val digits = body.map { it - '0' }
            val check = if (last == 'X') 10 else last - '0'
            if (check != checkDigit10(digits)) {
                return Result.failure(IsbnFormatException.InvalidCheckDigit(normalized))
            }

            // 978 プレフィックスを付け、13桁のチェックディジットを付け直す
            val converted = "978$body"
            val convertedDigits = converted.map { it - '0' }
            return Result.success(Isbn(converted + checkDigit13(convertedDigits)))
        }

        /** 13桁 ISBN のチェックディジット。奇数位に1、偶数位に3の重みを掛ける。 */
        private fun checkDigit13(first12: List<Int>): Int {
            val sum = first12.withIndex().sumOf { (index, digit) -> if (index % 2 == 0) digit else digit * 3 }
            return (10 - sum % 10) % 10
        }

        /** 10桁 ISBN のチェックディジット。10 は 'X' を意味する。 */
        private fun checkDigit10(first9: List<Int>): Int {
            val sum = first9.withIndex().sumOf { (index, digit) -> digit * (10 - index) }
            return (11 - sum % 11) % 11
        }
    }
}

/** ISBN の形式エラー。利用者への案内を出し分けるため種別を分けている。 */
sealed class IsbnFormatException(message: String) : IllegalArgumentException(message) {
    /** 桁数が10でも13でもない。 */
    class InvalidLength(val length: Int) : IsbnFormatException("ISBN の桁数が不正です: $length 桁")

    /** 数字（および10桁の末尾 X）以外の文字を含む。 */
    class InvalidCharacter(val input: String) : IsbnFormatException("ISBN に使用できない文字が含まれています: $input")

    /** チェックディジットが一致しない。入力の打ち間違いを検出する。 */
    class InvalidCheckDigit(val input: String) : IsbnFormatException("ISBN のチェックディジットが一致しません: $input")
}
