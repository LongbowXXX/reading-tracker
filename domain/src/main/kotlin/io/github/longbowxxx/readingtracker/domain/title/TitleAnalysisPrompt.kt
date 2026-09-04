package io.github.longbowxxx.readingtracker.domain.title

/**
 * オンデバイス AI（ML Kit GenAI Prompt API）へ渡すプロンプトを組み立てる。
 *
 * ドメイン層に置くのは、プロンプトが**この機能の仕様そのもの**だからである。
 * ML Kit への依存を持たないため、ユニットテストで内容を固定できる（憲法 原則III）。
 *
 * **指示文を英語で書いている理由**: Prompt API の公式ドキュメントには対応言語の記載が無い。
 * 要約 API・校正 API には「日本語対応」の明記があるのに Prompt API には無いため、
 * 日本語の指示が通る保証が取れていない。作品名そのものは日本語のまま扱う必要があるので、
 * **指示は英語、例と入出力は日本語**という構成にしてある。
 * 日本語の指示でも十分に動くことが実機で確認できれば、日本語へ寄せてよい。
 */
fun buildTitleAnalysisPrompt(rawTitle: String): String = "$PROMPT_TEMPLATE $rawTitle\nOutput:"

private val PROMPT_TEMPLATE =
    """
    Extract the series name and the volume number from a book title.

    Reply with exactly one JSON object and nothing else:
    {"work": "<series name>", "volume": <integer or null>}

    Rules:
    - "work": copy the series name from the input verbatim, in its original language and script.
    - Remove the volume notation from "work" (for example "5", "(5)", "第5巻", "巻ノ5", "vol.5", "その5", "#5").
    - Remove a parallel title that follows "=".
    - Keep a subtitle that follows ":".
    - Never translate, romanize, abbreviate or correct the series name.
    - "volume": the volume number as an integer, or null when the title has no volume number.
    - A number that belongs to the series name is not a volume number.

    Examples:
    Input: 進撃の巨人 34
    Output: {"work": "進撃の巨人", "volume": 34}
    Input: ONE PIECE. 巻94
    Output: {"work": "ONE PIECE", "volume": 94}
    Input: 乾と巽 = INUI and TATSUMI : ザバイカル戦記. 5
    Output: {"work": "乾と巽 : ザバイカル戦記", "volume": 5}
    Input: 鬼滅の刃公式ファンブック鬼殺隊見聞録
    Output: {"work": "鬼滅の刃公式ファンブック鬼殺隊見聞録", "volume": null}
    Input: ゴルゴ13
    Output: {"work": "ゴルゴ13", "volume": null}
    Input: 拳児2
    Output: {"work": "拳児", "volume": 2}

    Input:
    """.trimIndent()
