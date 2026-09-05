package io.github.longbowxxx.readingtracker.data.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import io.github.longbowxxx.readingtracker.domain.port.AiAvailability
import io.github.longbowxxx.readingtracker.domain.port.AiAvailabilityStatus
import io.github.longbowxxx.readingtracker.domain.port.AiPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * ML Kit GenAI（Gemini Nano / AICore）で可用性を判定し、モデルを取得する（Issue #9）。
 *
 * `title/` ではなく `ai/` に置くのは、可用性の判定がタイトル解析とは別の責務だからである。
 * 起動ゲートは AI を使わない画面も含めてアプリ全体に掛かる（SC-008）。
 *
 * beta である ML Kit の API 変更をここ1箇所に閉じ込めるため、
 * `FeatureStatus` の値も `DownloadStatus` の形も**この実装の外へ漏らさない**（research.md R-008）。
 *
 * **実機確認が必要**（憲法 原則IV）。JVM のユニットテストからは AICore へ接続できないため、
 * ここで検証できることは無い。判定結果から画面状態への写像は `:domain` 側でテストしてある。
 */
class GenAiAvailability(private val model: GenerativeModel) : AiAvailability {
    /**
     * 端末側の更新で可否が変わりうるため、**結果を保持せず**呼ばれるたびに判定する（FR-032）。
     * 判定できなかった場合は非対応へ畳み込む。利用者に取れる行動が変わらないため（FR-033）。
     */
    override suspend fun status(): AiAvailabilityStatus = try {
        when (val status = model.checkStatus()) {
            FeatureStatus.AVAILABLE -> AiAvailabilityStatus.AVAILABLE

            // 対応端末だがモデルが未取得・取得中。**非対応と混同してはならない**（FR-034）
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> AiAvailabilityStatus.PREPARING

            else -> {
                Log.d(TAG, "オンデバイス AI を利用できません: status=$status")
                AiAvailabilityStatus.UNSUPPORTED
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 判定は端末側の AI 基盤へ接続するため失敗しうる。例外を投げずに畳み込む（FR-033）
        Log.w(TAG, "オンデバイス AI の可否を判定できませんでした", e)
        AiAvailabilityStatus.UNSUPPORTED
    }

    /**
     * モデルを取得する。**購読された時点で初めて通信が始まる**ため、
     * この関数を呼ぶ側（`:app` の画面）が利用者の操作を待つ（FR-034）。
     *
     * 進捗のバイト数は流さない。取得量が不定であり、画面は不定のインジケータを出すため。
     */
    override fun prepare(): Flow<AiPreparation> = flow {
        emit(AiPreparation.Started)
        model.download().collect { status ->
            when (status) {
                DownloadStatus.DownloadCompleted -> {
                    Log.i(TAG, "Gemini Nano のダウンロードが完了しました")
                    emit(AiPreparation.Completed)
                }

                is DownloadStatus.DownloadFailed -> {
                    Log.w(TAG, "Gemini Nano のダウンロードに失敗しました", status.e)
                    emit(AiPreparation.Failed(status.e))
                }

                // 開始・進捗は状態を変えない。取得中の表示は購読の開始時点から出ている
                else -> Unit
            }
        }
    }.catch { cause ->
        // 契約上、失敗は例外ではなく Failed として流す。呼び出し側は再試行の手段を出す（FR-034）
        Log.w(TAG, "Gemini Nano のダウンロードを開始できませんでした", cause)
        emit(AiPreparation.Failed(cause))
    }

    private companion object {
        const val TAG = "GenAiAvailability"
    }
}
