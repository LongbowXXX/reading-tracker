package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer

/**
 * 正規表現でタイトルを解析する経路（[parseVolumeTitle]）。
 *
 * AI 経路（ML Kit GenAI Prompt API）が使えない端末・使えない状況のための最終手段であり、
 * **null を返さない**。対応端末は限られる（Pixel 9 以降、Galaxy S26 など）ため、
 * この経路が大半の端末での既定の挙動になる。
 */
class RuleBasedTitleAnalyzer : TitleAnalyzer {
    override suspend fun analyze(rawTitle: String): TitleAnalysis {
        val parsed = parseVolumeTitle(rawTitle)
        return TitleAnalysis(workTitle = parsed.workTitle, volumeNumber = parsed.volumeNumber)
    }
}
