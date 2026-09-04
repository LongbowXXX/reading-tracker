package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis

/**
 * オンデバイス AI の応答を [TitleAnalysis] に変換する（[buildTitleAnalysisPrompt] の対）。
 *
 * ドメイン層の純粋関数として置くことで、**推論そのものは実機でしか試せなくても、
 * 応答の解釈はユニットテストで固定できる**（憲法 原則III）。
 *
 * 生成モデルの出力は整った JSON とは限らないため、寛容に読む。
 * 一方で**内容は疑ってかかる**。翻訳・要約・作り話が混じった作品名をそのまま採用すると、
 * 照合キーが壊れて記録が分裂する。[isDerivedFrom] を通らない応答は捨て、
 * 呼び出し側が規則ベースの経路へ落ちるよう null を返す。
 *
 * @param rawTitle 推論に渡した元のタイトル。応答の妥当性検証に使う
 * @return 解釈できた解析結果。解釈できない・信用できない場合は null
 */
fun parseTitleAnalysisResponse(response: String?, rawTitle: String): TitleAnalysis? {
    if (response.isNullOrBlank()) return null

    val workTitle = WORK_PATTERN.find(response)?.groupValues?.get(1)?.let(::unescapeJsonString)?.trim()
    if (workTitle.isNullOrBlank()) return null
    if (!isDerivedFrom(workTitle, rawTitle)) return null

    val volumeNumber = VOLUME_PATTERN.find(response)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in VALID_VOLUME_RANGE }

    return TitleAnalysis(workTitle = workTitle, volumeNumber = volumeNumber)
}

/**
 * 応答の作品名が、元のタイトルから**文字を削っただけ**のものかを確かめる。
 *
 * 空白と記号を無視したうえで、作品名の各文字が元のタイトルに同じ順序で現れることを見る。
 * 巻数表記や並列書名の除去（`乾と巽 = INUI and TATSUMI : ザバイカル戦記. 5` → `乾と巽 : ザバイカル戦記`）は
 * 通り、翻訳・ローマ字化・創作は通らない。
 */
internal fun isDerivedFrom(workTitle: String, rawTitle: String): Boolean {
    val needle = workTitle.filter { it.isLetterOrDigit() }.lowercase()
    val haystack = rawTitle.filter { it.isLetterOrDigit() }.lowercase()
    if (needle.isEmpty()) return false

    var index = 0
    for (c in haystack) {
        if (index < needle.length && needle[index] == c) index++
    }
    return index == needle.length
}

/** JSON の文字列エスケープを戻す。生成モデルの出力に現れる範囲だけを扱う。 */
private fun unescapeJsonString(value: String): String {
    val builder = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c != '\\' || i == value.length - 1) {
            builder.append(c)
            i++
            continue
        }

        when (val escaped = value[i + 1]) {
            '"', '\\', '/' -> builder.append(escaped)

            'n' -> builder.append('\n')

            't' -> builder.append('\t')

            'r' -> builder.append('\r')

            'b' -> builder.append('\b')

            'f' -> builder.append('\u000C')

            'u' -> {
                val hex = value.drop(i + 2).take(4)
                val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                if (code == null) {
                    builder.append(escaped)
                } else {
                    builder.append(code.toChar())
                    i += 4
                }
            }

            else -> builder.append(escaped)
        }
        i += 2
    }
    return builder.toString()
}

/** `"work"` の値。エスケープされた引用符を含みうる。 */
private val WORK_PATTERN = Regex(""""work"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/** `"volume"` の値。数値が文字列で返ることもあるため引用符を任意とする。 */
private val VOLUME_PATTERN = Regex(""""volume"\s*:\s*"?(\d{1,4})"?""")

/** 巻数として受け入れる範囲。0 巻や桁あふれは誤読とみなす。 */
private val VALID_VOLUME_RANGE = 1..9999
