package io.github.longbowxxx.readingtracker.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * オンデバイス AI の利用可否（FR-032〜FR-034）。
 *
 * @property AVAILABLE 推論できる。起動ゲートを外す
 * @property PREPARING 対応端末だがモデルが未取得、または取得中。**非対応ではない**。
 *   これを非対応と混同すると、対応端末を非対応と誤って伝えることになる（FR-034）
 * @property UNSUPPORTED サポート対象外。判定できなかった場合もここへ畳み込む（FR-033）
 */
enum class AiAvailabilityStatus { AVAILABLE, PREPARING, UNSUPPORTED }

/** モデルの取得の経過。進捗率は含めない。取得量が不定であり、画面は不定のインジケータを出すため。 */
sealed interface AiPreparation {
    /** 取得を開始した。 */
    data object Started : AiPreparation

    /** 取得が完了した。以降 [AiAvailability.status] は [AiAvailabilityStatus.AVAILABLE] を返す。 */
    data object Completed : AiPreparation

    /** 取得に失敗した。呼び出し側は再試行の手段を出す（FR-034）。 */
    data class Failed(val cause: Throwable?) : AiPreparation
}

/**
 * オンデバイス AI の可用性を判定し、モデルの取得を行う経路（FR-032〜FR-034）。
 *
 * インターフェースをドメイン層に置くのは、判定の実体である ML Kit GenAI
 * （`checkStatus()` / `download()`）が **Android に依存する**ためである。
 * `:domain` を純粋な Kotlin に保つ（憲法 原則III）にはここに境界が要る。
 * これにより「判定結果から画面状態を導く」部分
 * （[io.github.longbowxxx.readingtracker.domain.ai.AiGateStateMachine]）を Android 非依存の
 * 純粋なロジックとして置け、エミュレータなしで全分岐をユニットテストで固定できる。
 * beta である ML Kit の API 変更も、`:data` の実装1箇所に閉じる（research.md R-008）。
 */
interface AiAvailability {
    /**
     * 現在の可用性を返す。
     *
     * **例外を投げてはならない。** 端末側の AI 基盤へ到達できないなど判定できなかった場合も
     * [AiAvailabilityStatus.UNSUPPORTED] を返す（FR-033 が判定不能を非対応と同様に扱うため）。
     *
     * **結果を保持してはならない。** 端末側の更新で可否が変わりうるため、
     * 呼ばれるたびに実際の状態を返し、起動のたびに判定できるようにする（FR-032）。
     */
    suspend fun status(): AiAvailabilityStatus

    /**
     * モデルの取得を行い、経過を流す。
     *
     * [status] が [AiAvailabilityStatus.PREPARING] のときのみ意味を持つ。
     * それ以外での呼び出しは契約外であり、呼び出し側が呼ばない。
     *
     * **購読は利用者の明示的な操作（「ダウンロードを開始」の選択）を受けてから行う。**
     * 起動は店舗外でも起こり、従量課金のモバイル回線で大容量の取得が黙って始まりうるため、
     * ゲートが自動で購読してはならない（FR-034、research.md R-008）。
     *
     * 失敗しても例外を投げず、[AiPreparation.Failed] を流して終了する。
     */
    fun prepare(): Flow<AiPreparation>
}
