package io.github.longbowxxx.readingtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.longbowxxx.readingtracker.domain.ai.AiGateState
import io.github.longbowxxx.readingtracker.domain.ai.AiGateStateMachine
import io.github.longbowxxx.readingtracker.domain.port.AiAvailability
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 起動ゲートの状態を画面へ流す（Issue #9、FR-032〜FR-035）。
 *
 * 判断そのものは `:domain` の [AiGateStateMachine] にあり、ここはポートの呼び出しと
 * 状態の保持だけを行う薄い層に留める。写像と遷移を `:domain` に置いておくことで、
 * エミュレータなしのユニットテストで全分岐を固定できる（憲法 原則III）。
 *
 * 起動時に行うのは判定（[AiGateStateMachine.check]）だけである。
 * **`prepare()` の購読は [startPreparation] を通じた利用者の操作を起点とする**（FR-034）。
 */
@HiltViewModel
class AiGateViewModel
@Inject
constructor(availability: AiAvailability) : ViewModel() {
    private val machine = AiGateStateMachine(availability)

    val state: StateFlow<AiGateState> = machine.state

    init {
        check()
    }

    /** 可用性を判定する。起動時と、非対応・判定不能の画面での再試行から呼ぶ（FR-032）。 */
    fun check() {
        viewModelScope.launch { machine.check() }
    }

    /** 利用者が「ダウンロードを開始」を選んだときだけ呼ぶ（FR-034）。 */
    fun startPreparation() {
        viewModelScope.launch { machine.startPreparation() }
    }

    /**
     * 非対応のまま本体へ進む（FR-035）。
     *
     * **開発ビルドの画面からしか呼ばれない。** 導線を出すかどうかは画面側が
     * `BuildConfig.DEBUG` で判断する。
     */
    fun continueWithoutAi() {
        machine.continueWithoutAi()
    }
}
