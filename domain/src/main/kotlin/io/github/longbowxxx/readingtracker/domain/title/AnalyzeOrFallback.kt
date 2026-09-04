package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer

/**
 * タイトルを解析し、どの経路も判定できなければ規則ベースの結果へ落とす。
 *
 * AI 経路（ML Kit GenAI Prompt API）は対応端末が限られるうえ、推論そのものが
 * 失敗・時間切れになりうる。**記録が止まることのほうが害が大きい**ため、
 * ここでは必ず結果を返す（憲法 原則VI）。
 */
suspend fun TitleAnalyzer.analyzeOrFallback(rawTitle: String): ParsedTitle =
    toParsedTitle(analyze(rawTitle) ?: RULE_BASED_ANALYZER.analyze(rawTitle))

/** 状態を持たないため、使い回してよい。 */
private val RULE_BASED_ANALYZER = RuleBasedTitleAnalyzer()
