package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 複数の解析経路を直列に試す（書誌情報の `ChainedBibliographySource` と同じ考え方）。
 *
 * 規則
 * 1. 先頭から順に試す。AI 経路を先頭に置く
 * 2. null 以外が返った時点で確定する
 * 3. null／例外／時間切れなら次の経路へ進む
 * 4. すべて尽きたら null を返す
 *
 * **1経路ごとに時間の上限を設ける。** オンデバイス推論は Pixel 9 の実測で 0.8〜2.1 秒
 * かかる。記録は本を手に持った状態で行うため、待たせるくらいなら規則ベースの結果で
 * 進めるほうがよい（憲法 原則VI）。
 */
class ChainedTitleAnalyzer(
    private val analyzers: List<TitleAnalyzer>,
    private val perAnalyzerTimeoutMillis: Long = DEFAULT_PER_ANALYZER_TIMEOUT_MILLIS,
) : TitleAnalyzer {
    override suspend fun analyze(rawTitle: String): TitleAnalysis? {
        for (analyzer in analyzers) {
            val result =
                try {
                    withTimeoutOrNull(perAnalyzerTimeoutMillis) { analyzer.analyze(rawTitle) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 契約上 analyze は例外を投げないが、実装の綻びで記録が止まらないよう受け止める
                    null
                }

            if (result != null) return result
        }

        return null
    }

    companion object {
        /** 1経路あたりの上限。AI 経路が遅い端末でも記録操作を止めないための値。 */
        const val DEFAULT_PER_ANALYZER_TIMEOUT_MILLIS: Long = 2_500
    }
}
