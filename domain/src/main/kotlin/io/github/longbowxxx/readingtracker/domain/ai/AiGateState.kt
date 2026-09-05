package io.github.longbowxxx.readingtracker.domain.ai

import io.github.longbowxxx.readingtracker.domain.port.AiAvailability
import io.github.longbowxxx.readingtracker.domain.port.AiAvailabilityStatus
import io.github.longbowxxx.readingtracker.domain.port.AiPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull

/** 非対応として扱う理由。取れる行動は同じであり、**分けるのは文言だけ**（FR-033）。 */
enum class AiUnsupportedReason {
    /** 端末がオンデバイス AI に対応していない。 */
    UNSUPPORTED,

    /** 可否そのものを判定できなかった。「確認できませんでした」と伝える。 */
    UNDETERMINED,
}

/**
 * 起動ゲートの画面状態（Issue #9、contracts/ai-availability.md）。
 *
 * 「準備待ち」と「取得中」を分けているのは、**モデルの取得をアプリが勝手に始めないため**である。
 * 起動は店舗外でも起こり、従量課金のモバイル回線で大容量の取得が黙って始まりうる（FR-034）。
 */
sealed interface AiGateState {
    /** 確認中。判定の完了を待つ。 */
    data object Checking : AiGateState

    /** 利用可。ゲートを外して本体を表示する。 */
    data object Available : AiGateState

    /**
     * 準備待ち。モデルが未取得であることを示すだけの状態であり、**ここで取得を開始してはならない**。
     *
     * @property lastFailure 直前の取得に失敗していればその原因。失敗した旨と再試行を出すために持つ（FR-034）
     */
    data class PreparingIdle(val lastFailure: Throwable? = null) : AiGateState

    /** 取得中。進捗率は持たない。取得量が不定であり、画面は不定のインジケータを出す。 */
    data object Downloading : AiGateState

    /** 非対応。先へ進めない（SC-008）。 */
    data class Unsupported(val reason: AiUnsupportedReason) : AiGateState
}

/**
 * 判定結果・準備結果から [AiGateState] を導く純粋なロジック（FR-032〜FR-034）。
 *
 * ML Kit GenAI は Android に依存するため、判定の実体は [AiAvailability] の向こう側にある。
 * 状態の写像と遷移だけをここに置くことで、**エミュレータなしに全分岐をユニットテストで固定できる**
 * （憲法 原則III）。`:app` の ViewModel は本クラスを呼び、状態を画面へ流すだけの薄い層に留める。
 */
class AiGateStateMachine(private val availability: AiAvailability) {
    private val _state = MutableStateFlow<AiGateState>(AiGateState.Checking)
    val state: StateFlow<AiGateState> = _state.asStateFlow()

    /**
     * 可用性を判定する。起動時と再試行の双方から呼ぶ（FR-032、A-8）。
     *
     * **判定結果を保持しない。** 端末側の更新で可否が変わりうるため、呼ばれるたびに判定し直す。
     * [AiAvailabilityStatus.PREPARING] は「準備待ち」へ写すだけで、
     * ここでモデルの取得を始めない（FR-034）。
     */
    suspend fun check() {
        _state.value = AiGateState.Checking
        _state.value =
            try {
                when (availability.status()) {
                    AiAvailabilityStatus.AVAILABLE -> AiGateState.Available
                    AiAvailabilityStatus.PREPARING -> AiGateState.PreparingIdle()
                    AiAvailabilityStatus.UNSUPPORTED -> AiGateState.Unsupported(AiUnsupportedReason.UNSUPPORTED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 契約上 status() は例外を投げないが、beta の実装が投げてもゲートが壊れてはならない。
                // 利用者に取れる行動は非対応と同じであり、文言だけを分ける（FR-033）
                AiGateState.Unsupported(AiUnsupportedReason.UNDETERMINED)
            }
    }

    /**
     * モデルの取得を開始する。**利用者が「ダウンロードを開始」を選んだときだけ呼ぶ**（FR-034、A-3）。
     *
     * 準備待ち以外の状態では何もしない。表示しただけで購読が起きる経路を残さないための番人であり、
     * 二重の購読も防ぐ。完了すれば追加の操作なく利用可へ進み（SC-009）、
     * 失敗すれば準備待ちへ戻して再試行できるようにする。
     */
    suspend fun startPreparation() {
        if (_state.value !is AiGateState.PreparingIdle) return
        _state.value = AiGateState.Downloading

        _state.value =
            try {
                availability
                    .prepare()
                    .mapNotNull { event ->
                        when (event) {
                            // 開始の通知では状態を変えない。既に取得中を表示している
                            AiPreparation.Started -> null

                            AiPreparation.Completed -> AiGateState.Available

                            is AiPreparation.Failed -> AiGateState.PreparingIdle(event.cause)
                        }
                    }
                    // 終端の事象を得た時点で購読を打ち切る
                    .firstOrNull()
                    // 終端の事象なく流れが終わった場合も、取得できたとは見なさない
                    ?: AiGateState.PreparingIdle()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 契約上 prepare() は例外を投げず Failed を流すが、投げられても再試行できる状態へ戻す
                AiGateState.PreparingIdle(e)
            }
    }

    /**
     * 非対応のままゲートを外す（FR-035）。
     *
     * **開発ビルドでのみ使う回避手段**である。ビルド種別の判断は本クラスの責務ではなく、
     * `:app` の画面が `BuildConfig.DEBUG` で導線ごと出し分ける。配布ビルドから呼んではならない。
     */
    fun continueWithoutAi() {
        _state.value = AiGateState.Available
    }
}
