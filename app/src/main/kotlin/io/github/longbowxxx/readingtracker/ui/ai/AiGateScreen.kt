package io.github.longbowxxx.readingtracker.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.longbowxxx.readingtracker.BuildConfig
import io.github.longbowxxx.readingtracker.domain.ai.AiGateState
import io.github.longbowxxx.readingtracker.domain.ai.AiUnsupportedReason

/**
 * 起動ゲート（Issue #9、FR-032〜FR-035）。
 *
 * 作品の自動照合はオンデバイス AI を前提とするため、AI が使えないまま本体へ入れると
 * **AI が効いていないことが利用者に見えないまま結果だけが劣化する**（Issue #9 が問題とした状態）。
 * そのためゲートはアプリ全体に掛け、AI を使わない画面も含めて通さない（SC-008）。
 *
 * 利用可のときは何も挟まずに [content] を出す。記録の主導線のタップ数を増やさない
 * （憲法 原則VI、SC-009）。
 */
@Composable
fun AiGateScreen(modifier: Modifier = Modifier, viewModel: AiGateViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        AiGateState.Available -> content()

        AiGateState.Checking -> CheckingContent(modifier)

        is AiGateState.PreparingIdle ->
            PreparingIdleContent(
                lastFailure = current.lastFailure,
                onStart = viewModel::startPreparation,
                modifier = modifier,
            )

        AiGateState.Downloading -> DownloadingContent(modifier)

        is AiGateState.Unsupported ->
            UnsupportedContent(
                reason = current.reason,
                onRetry = viewModel::check,
                onContinueWithoutAi = viewModel::continueWithoutAi,
                modifier = modifier,
            )
    }
}

/** 確認中。判定は端末側の AI 基盤へ接続するため、一瞬で終わるとは限らない。 */
@Composable
private fun CheckingContent(modifier: Modifier = Modifier) {
    GateLayout(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text("AI の対応状況を確認しています…")
        }
    }
}

/**
 * 準備待ち。**表示しただけで取得を始めてはならない**（FR-034）。
 *
 * 起動は店舗外でも起こる。従量課金のモバイル回線で大容量の取得が黙って始まらないよう、
 * 開始の判断は利用者に委ねる（research.md R-008）。回線種別は判定しないため、
 * 判断の材料として通信量が大きいことを文面で伝える。
 */
@Composable
private fun PreparingIdleContent(lastFailure: Throwable?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    GateLayout(modifier) {
        Text(text = "AI の準備が必要です", style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
            "この端末は AI に対応していますが、モデルがまだ取得されていません。" +
                "取得には時間と通信量がかかるため、Wi-Fi に接続した状態で開始することをおすすめします。",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (lastFailure != null) {
            // 失敗した旨を残す。何も出さずに戻すと、押したのに何も起きなかったように見える（FR-034）
            Text(
                text = "前回のダウンロードに失敗しました。もう一度お試しください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(if (lastFailure == null) "ダウンロードを開始" else "ダウンロードを再試行")
        }
    }
}

/** 取得中。取得量が不定であるため、進捗率ではなく不定のインジケータを出す。 */
@Composable
private fun DownloadingContent(modifier: Modifier = Modifier) {
    GateLayout(modifier) {
        Text(text = "AI を準備しています", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "モデルをダウンロードしています。完了すると、そのまま記録の画面へ進みます。",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 非対応・判定不能。ここから先へは進めない（SC-008）。
 *
 * 利用者に取れる行動は両者で変わらないため、**分けるのは文言だけ**にする（FR-033）。
 */
@Composable
private fun UnsupportedContent(
    reason: AiUnsupportedReason,
    onRetry: () -> Unit,
    onContinueWithoutAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GateLayout(modifier) {
        val title =
            when (reason) {
                AiUnsupportedReason.UNSUPPORTED -> "この端末は非対応です"
                AiUnsupportedReason.UNDETERMINED -> "AI の対応状況を確認できませんでした"
            }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
            "このアプリは作品の判別にオンデバイス AI を使います。" +
                "AI を利用できないため、記録と参照の機能は使えません。" +
                "端末のシステム更新後などに、再試行で状況が変わることがあります。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("再試行")
        }

        // 配布ビルドにこの導線が存在してはならない（FR-035）。
        // 実機に非対応端末しか無い状況でも画面を確認できるようにするための、開発用の抜け道
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onContinueWithoutAi, modifier = Modifier.fillMaxWidth()) {
                Text("非対応のまま続行（開発用）")
            }
        }
    }
}

/** ゲートの各画面で共通の余白と並び。 */
@Composable
private fun GateLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}
