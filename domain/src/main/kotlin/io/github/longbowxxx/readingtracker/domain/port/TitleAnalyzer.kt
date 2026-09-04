package io.github.longbowxxx.readingtracker.domain.port

/**
 * タイトル文字列から読み取った作品名と巻数。
 *
 * @property workTitle 巻数表記・レーベル名・並列書名を除いた作品名。
 *   表示にも照合キーの生成にも使う
 * @property volumeNumber 読み取れた巻数。読み取れなければ null
 */
data class TitleAnalysis(val workTitle: String, val volumeNumber: Int?)

/**
 * 書誌タイトルから作品名と巻数を読み取る経路（FR-027, FR-028）。
 *
 * 経路をインターフェースで抽象化するのは、オンデバイス AI（ML Kit GenAI Prompt API）が
 * 一部の端末でしか使えないためである。非対応端末では規則ベースの経路へ落ちる。
 * また ML Kit は Android に依存するため、`:domain` を純粋な Kotlin に保つには
 * ここに境界を置く必要がある（憲法 原則III）。
 *
 * **実装は例外を投げてはならない。** 判定できなかった場合は null を返し、
 * 呼び出し側が次の経路へ進む（[io.github.longbowxxx.readingtracker.domain.title.ChainedTitleAnalyzer]）。
 */
interface TitleAnalyzer {
    suspend fun analyze(rawTitle: String): TitleAnalysis?
}
