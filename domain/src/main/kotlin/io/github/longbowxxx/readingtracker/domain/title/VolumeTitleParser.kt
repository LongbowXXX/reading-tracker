package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis

/**
 * タイトルの解析結果。
 *
 * @property matchKey 既存の作品との自動照合に用いる正規化文字列
 * @property workTitle 表示用の作品名。巻数表記を除いたもの。**元の表記を保つ**
 * @property volumeNumber 抽出できた巻数。できなければ null
 */
data class ParsedTitle(val matchKey: String, val workTitle: String, val volumeNumber: Int?)

/**
 * [TitleAnalysis] から照合キーを補って [ParsedTitle] にする。
 *
 * 照合キーの生成をここに集約するのは、**経路によって照合キーが割れないようにする**ため。
 * AI 経路は並列書名（`= BIOMEGA`）を落とした作品名を返すが、規則ベースの経路は残す。
 * 照合キー側で吸収しておかないと、同じ本が経路の違いだけで別作品になる（Issue #4）。
 */
fun toParsedTitle(analysis: TitleAnalysis): ParsedTitle = ParsedTitle(
    matchKey = buildMatchKey(analysis.workTitle),
    workTitle = analysis.workTitle,
    volumeNumber = analysis.volumeNumber,
)

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

    return ParsedTitle(matchKey = buildMatchKey(workTitle), workTitle = workTitle, volumeNumber = volumeNumber)
}

/**
 * 作品名から照合キーを作る。
 *
 * 実データ（openBD 1,535件 / NDL 230件）で確認した、同一シリーズが割れる要因を潰す。
 *
 * 1. 全角英数字・記号を半角へ寄せる
 * 2. 並列書名（`ブーツレグ = BooTsLeG`）を落とす。経路によって付いたり付かなかったりする
 * 3. 空白を除去する
 * 4. 末尾の区切り記号を落とす。openBD の `チェンソーマン 5` と NDL の `チェンソーマン. 5` は
 *    同じ本であり、ここを揃えないと1巻ごとに別作品になる
 * 5. 大文字小文字を揃える（`act-age` と `Act-Age`）
 *
 * `!` `?` `)` は作品名の一部として現れる（`よつばと！` `怪物(けもの)事変`）ため落とさない。
 */
fun buildMatchKey(workTitle: String): String {
    val halfWidth = workTitle.map(::toHalfWidthChar).joinToString("")

    // 先頭が `=` の場合は並列書名ではなく作品名の一部とみなす
    val withoutParallelTitle = halfWidth.indexOf('=').let { if (it > 0) halfWidth.substring(0, it) else halfWidth }

    return withoutParallelTitle
        .filterNot { it.isWhitespace() }
        .trimEnd { it in TRAILING_SEPARATORS }
        .lowercase()
}

/** 照合キーの末尾から落とす区切り記号。全角は半角へ寄せた後の文字で判定する。 */
private const val TRAILING_SEPARATORS = ".。,、:;=/-ー―‐・"

/**
 * 末尾の巻数表記のパターン。順序に意味がある。
 * 「第12巻」を「12巻」より先に評価しないと、作品名に「第」が残ってしまう。
 *
 * 語彙は実データ（openBD のコミック 1,129 件）で実際に現れたものに限る。
 * 網羅は目標としない（research.md R-002）。
 */
private val VOLUME_PATTERNS =
    listOf(
        Regex("""第\s*(\d{1,4})\s*巻$"""),
        Regex("""(\d{1,4})\s*巻$"""),
        // 巻94 / 巻ノ9 / 巻之3
        Regex("""巻\s*[ノの之]?\s*(\d{1,4})$"""),
        // vol.8 / VOLUME.1 / volume21
        Regex("""(?:vol|volume)\s*\.?\s*(\d{1,4})$""", RegexOption.IGNORE_CASE),
        // 月曜日のたわわ. その1
        Regex("""その\s*(\d{1,4})$"""),
        // ブーツレグ = BooTsLeG. #2
        Regex("""#\s*(\d{1,4})$"""),
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
