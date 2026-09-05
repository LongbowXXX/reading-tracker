package io.github.longbowxxx.readingtracker.domain.ai

import io.github.longbowxxx.readingtracker.domain.port.AiAvailability
import io.github.longbowxxx.readingtracker.domain.port.AiAvailabilityStatus
import io.github.longbowxxx.readingtracker.domain.port.AiPreparation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 起動ゲートの5状態と遷移（Issue #9、contracts/ai-availability.md の受け入れ基準 A-1〜A-8）。
 *
 * 端末が実際に対応しているかは AICore への接続が要るため JVM では確かめられない。
 * ここで固定するのは**判定結果・準備結果から画面状態への写像**であり、とりわけ
 * **準備待ちの間に prepare() を購読しないこと**（FR-034）を購読回数で押さえる。
 * 自動で取得が始まると、店舗外の従量課金回線で大容量の通信が黙って発生する。
 */
class AiGateStateTest {
    @Test
    @DisplayName("A-1 利用可能ならゲートを外す")
    fun `利用可の判定で本体へ進める`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.AVAILABLE))
        val machine = AiGateStateMachine(availability)

        machine.check()

        assertEquals(AiGateState.Available, machine.state.value)
        assertEquals(0, availability.prepareSubscriptionCount)
    }

    @Test
    @DisplayName("A-2 モデル未取得は非対応ではなく準備待ちにする")
    fun `準備中の判定は準備待ちへ写す`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)

        machine.check()

        assertEquals(AiGateState.PreparingIdle(), machine.state.value)
    }

    @Test
    @DisplayName("A-2 準備待ちの間は取得を自動で開始しない")
    fun `準備待ちでは取得の経路を購読しない`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)

        machine.check()
        runCurrent()

        assertEquals(0, availability.prepareSubscriptionCount)
    }

    @Test
    @DisplayName("A-3 利用者が開始を選んで初めて取得中へ遷移する")
    fun `取得開始の操作で取得の経路を購読する`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)
        machine.check()

        val preparing = launch { machine.startPreparation() }
        runCurrent()

        assertEquals(AiGateState.Downloading, machine.state.value)
        assertEquals(1, availability.prepareSubscriptionCount)

        availability.emitPreparation(AiPreparation.Completed)
        preparing.join()
    }

    @Test
    @DisplayName("A-4 取得が完了すれば追加の操作なく利用可へ進む")
    fun `完了で利用可へ遷移する`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)
        machine.check()

        val preparing = launch { machine.startPreparation() }
        runCurrent()
        availability.emitPreparation(AiPreparation.Started)
        availability.emitPreparation(AiPreparation.Completed)
        preparing.join()

        assertEquals(AiGateState.Available, machine.state.value)
    }

    @Test
    @DisplayName("A-5 取得に失敗したら準備待ちへ戻し、失敗した旨を残す")
    fun `失敗で準備待ちへ戻す`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)
        machine.check()
        val cause = IllegalStateException("取得に失敗しました")

        val preparing = launch { machine.startPreparation() }
        runCurrent()
        availability.emitPreparation(AiPreparation.Failed(cause))
        preparing.join()

        val state = machine.state.value
        assertEquals(AiGateState.PreparingIdle(cause), state)
        assertSame(cause, (state as AiGateState.PreparingIdle).lastFailure)
    }

    @Test
    @DisplayName("A-5 失敗のあとに再度開始できる")
    fun `失敗後の再試行で取得をやり直す`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.PREPARING))
        val machine = AiGateStateMachine(availability)
        machine.check()

        val first = launch { machine.startPreparation() }
        runCurrent()
        availability.emitPreparation(AiPreparation.Failed(null))
        first.join()

        val second = launch { machine.startPreparation() }
        runCurrent()
        availability.emitPreparation(AiPreparation.Completed)
        second.join()

        assertEquals(AiGateState.Available, machine.state.value)
        assertEquals(2, availability.prepareSubscriptionCount)
    }

    @Test
    @DisplayName("A-6 非対応の端末は記録・参照の画面へ到達させない")
    fun `非対応の判定は非対応の状態へ写す`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.UNSUPPORTED))
        val machine = AiGateStateMachine(availability)

        machine.check()

        assertEquals(AiGateState.Unsupported(AiUnsupportedReason.UNSUPPORTED), machine.state.value)
        assertEquals(0, availability.prepareSubscriptionCount)
    }

    @Test
    @DisplayName("A-7 判定が例外で失敗しても非対応と同じ状態にし、理由だけを分ける")
    fun `判定できなかった場合も先へ進めない`() = runTest {
        val availability = FakeAiAvailability(statusFailure = IllegalStateException("AICore へ接続できません"))
        val machine = AiGateStateMachine(availability)

        machine.check()

        assertEquals(AiGateState.Unsupported(AiUnsupportedReason.UNDETERMINED), machine.state.value)
    }

    @Test
    @DisplayName("A-8 再試行すると判定をやり直す")
    fun `再試行で判定を呼び直す`() = runTest {
        val availability =
            FakeAiAvailability(listOf(AiAvailabilityStatus.UNSUPPORTED, AiAvailabilityStatus.AVAILABLE))
        val machine = AiGateStateMachine(availability)

        machine.check()
        assertEquals(AiGateState.Unsupported(AiUnsupportedReason.UNSUPPORTED), machine.state.value)
        machine.check()

        assertEquals(AiGateState.Available, machine.state.value)
        assertEquals(2, availability.statusCallCount)
    }

    @Test
    @DisplayName("判定が終わるまでは確認中で待たせる")
    fun `初期状態は確認中`() = runTest {
        val machine = AiGateStateMachine(FakeAiAvailability(listOf(AiAvailabilityStatus.AVAILABLE)))

        assertEquals(AiGateState.Checking, machine.state.value)
    }

    @Test
    @DisplayName("準備待ち以外の状態から取得を開始しようとしても購読しない")
    fun `非対応の状態からは取得を開始しない`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.UNSUPPORTED))
        val machine = AiGateStateMachine(availability)
        machine.check()

        machine.startPreparation()

        assertEquals(AiGateState.Unsupported(AiUnsupportedReason.UNSUPPORTED), machine.state.value)
        assertEquals(0, availability.prepareSubscriptionCount)
    }

    @Test
    @DisplayName("A-9 開発ビルドの続行は非対応のままゲートを外す")
    fun `非対応のまま続行できる`() = runTest {
        val availability = FakeAiAvailability(listOf(AiAvailabilityStatus.UNSUPPORTED))
        val machine = AiGateStateMachine(availability)
        machine.check()

        machine.continueWithoutAi()

        assertEquals(AiGateState.Available, machine.state.value)
    }

    /**
     * 判定結果を並べ、準備の経過を手で進めるフェイク。
     *
     * 取得中の状態を観測するため、[prepare] は事象を流したときだけ進む。
     * 購読回数を数えるのは、準備待ちの間に購読されないことを固定するため（FR-034）。
     */
    private class FakeAiAvailability(
        private val statuses: List<AiAvailabilityStatus> = listOf(AiAvailabilityStatus.PREPARING),
        private val statusFailure: Throwable? = null,
    ) : AiAvailability {
        var statusCallCount: Int = 0
            private set

        var prepareSubscriptionCount: Int = 0
            private set

        private val events = Channel<AiPreparation>(Channel.UNLIMITED)

        override suspend fun status(): AiAvailabilityStatus {
            statusCallCount++
            statusFailure?.let { throw it }
            return statuses[(statusCallCount - 1).coerceAtMost(statuses.lastIndex)]
        }

        override fun prepare(): Flow<AiPreparation> = flow {
            prepareSubscriptionCount++
            for (event in events) {
                emit(event)
            }
        }

        suspend fun emitPreparation(event: AiPreparation) {
            events.send(event)
        }
    }
}
