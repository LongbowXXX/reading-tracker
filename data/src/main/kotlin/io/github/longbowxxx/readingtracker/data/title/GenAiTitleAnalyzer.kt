package io.github.longbowxxx.readingtracker.data.title

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.buildTitleAnalysisPrompt
import io.github.longbowxxx.readingtracker.domain.title.parseTitleAnalysisResponse
import kotlinx.coroutines.CancellationException

/**
 * オンデバイス AI（ML Kit GenAI Prompt API / Gemini Nano）でタイトルを解析する（Issue #4）。
 *
 * 正規表現では原理的に解けない表記——たとえば区切りの無い数字が巻数なのか作品名の一部なのか
 * （`拳児2` は2巻、`ゴルゴ13` は作品名）——を、世界知識で判断させることが狙い。
 *
 * **判定できない場合は必ず null を返す。** 呼び出し側は規則ベースの経路へ落ちる。
 * ここに残るのは「`AVAILABLE` なら推論し、それ以外・失敗時は null を返す」ことだけであり、
 * **モデルのダウンロードを促す責務は持たない**。起動ゲート（contracts/ai-availability.md、
 * Issue #9）が `AVAILABLE` でなければ本体へ入れないため、記録の最中に未取得を検知する経路は
 * 実際には通らない。それでも判定を残すのは、起動後に端末側の状態が変わりうるためである。
 *
 * [model] を外から受け取るのは、起動ゲートの判定（`GenAiAvailability`）と同じクライアントを
 * 共有するため（`AiModule`）。判定と推論で別の接続を張らない。
 *
 * 推論は端末内で完結し、入出力はネットワークへ出ない（憲法 原則V）。ただし ML Kit は
 * API の利用状況メトリクスを Google へ送る点に注意（ML Kit 利用規約 Privacy）。
 *
 * **実機確認が必要**（憲法 原則IV）。JVM のユニットテストからは AICore へ接続できないため、
 * ここで検証できるのはプロンプトの組み立てと応答の解釈だけであり、それらは `:domain` 側に
 * 純粋関数として置いてテストしてある。
 */
class GenAiTitleAnalyzer(private val model: GenerativeModel) : TitleAnalyzer {
    override suspend fun analyze(rawTitle: String): TitleAnalysis? {
        if (rawTitle.isBlank()) return null

        return try {
            when (val status = model.checkStatus()) {
                FeatureStatus.AVAILABLE -> infer(rawTitle)

                else -> {
                    // 起動ゲートを通ったあとに状態が変わった場合。記録は止めず規則ベースへ落とす
                    Log.d(TAG, "オンデバイス AI を利用できません: status=$status")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 推論の失敗で記録が止まってはならない（憲法 原則VI）
            Log.w(TAG, "タイトルの解析に失敗しました。規則ベースへ落とします", e)
            null
        }
    }

    private suspend fun infer(rawTitle: String): TitleAnalysis? {
        val request =
            generateContentRequest(TextPart(buildTitleAnalysisPrompt(rawTitle))) {
                // 同じタイトルから毎回同じ照合キーを得るため、揺れを最小にする。
                // 揺れると同一作品が巻ごとに分裂する（Issue #4 そのもの）
                temperature = 0f
                topK = 1
                candidateCount = 1
                seed = FIXED_SEED
                maxOutputTokens = MAX_OUTPUT_TOKENS
            }

        val response = model.generateContent(request)
        return parseTitleAnalysisResponse(response.candidates.firstOrNull()?.text, rawTitle)
    }

    private companion object {
        const val TAG = "GenAiTitleAnalyzer"

        /** 応答は1行の JSON。これを超える出力は誤りとみなしてよい。 */
        const val MAX_OUTPUT_TOKENS = 128

        /** 結果を決定的にするための固定値。0 は「毎回異なるシード」を意味するため使わない。 */
        const val FIXED_SEED = 1
    }
}
