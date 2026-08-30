package io.github.longbowxxx.readingtracker.domain.title

/**
 * タイトルの解析結果。
 *
 * @property matchKey 既存の作品との自動照合に用いる正規化文字列
 * @property workTitle 表示用の作品名。巻数表記を除いたもの。**元の表記を保つ**
 * @property volumeNumber 抽出できた巻数。できなければ null
 */
data class ParsedTitle(val matchKey: String, val workTitle: String, val volumeNumber: Int?)

/**
 * 書誌情報のタイトルから巻数を抽出し、作品の照合キーを作る（FR-027）。
 *
 * 巻数は独立した項目として取得できるとは限らないため、タイトル文字列から拾う
 * （要求定義書 10.）。巻数表記は末尾にのみ現れるものとして扱う。
 *
 * **誤照合の完全な排除は目標としない。** 表記ゆれや同名の別作品による誤りは、
 * 確認画面での利用者の修正によって解決する（spec.md の Assumptions）。
 */
fun parseVolumeTitle(rawTitle: String): ParsedTitle {
    // 全角英数字・記号を半角へ寄せた文字列。元と1文字ずつ対応するため添字が一致する
    val normalized = rawTitle.map(::toHalfWidthChar).joinToString("")
    val scanTarget = normalized.trimEnd()

    val match = VOLUME_PATTERNS.firstNotNullOfOrNull { it.find(scanTarget) }
    val volumeNumber = match?.groupValues?.get(1)?.toIntOrNull()

    // 表示用の作品名は元の表記から切り出す（全角記号などを潰さない）
    val workTitle =
        if (match != null && volumeNumber != null) {
            rawTitle.substring(0, match.range.first).trim()
        } else {
            rawTitle.trim()
        }

    val matchKey = workTitle.map(::toHalfWidthChar).filterNot { it.isWhitespace() }.joinToString("")

    return ParsedTitle(matchKey = matchKey, workTitle = workTitle, volumeNumber = volumeNumber)
}

/**
 * 末尾の巻数表記のパターン。順序に意味がある。
 * 「第12巻」を「12巻」より先に評価しないと、作品名に「第」が残ってしまう。
 */
private val VOLUME_PATTERNS =
    listOf(
        Regex("""第\s*(\d{1,4})\s*巻$"""),
        Regex("""(\d{1,4})\s*巻$"""),
        Regex("""[(\[](\d{1,4})[)\]]$"""),
        Regex("""\s+(\d{1,4})$"""),
    )

/**
 * 全角の英数字・記号・空白を半角へ寄せる。1文字を1文字へ写すため、
 * 変換前後で文字列の添字が一致する。
 */
private fun toHalfWidthChar(c: Char): Char = when (c) {
    // 全角スペース
    '　' -> ' '

    // 全角の記号・英数字（！ から ～ まで）
    in '！'..'～' -> (c.code - 0xFEE0).toChar()

    else -> c
}
