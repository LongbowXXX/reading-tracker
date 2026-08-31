package io.github.longbowxxx.readingtracker.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * バーコードも ISBN も使えない本を暫定名で記録する入口（FR-008 / User Story 3）。
 *
 * バーコード非搭載の旧刊が対象。記録できない本があると記録の連続性が途切れ、
 * 来店時の一覧の信頼性が落ちるため、逃げ道として用意する。
 *
 * 主導線ではないので控えめに置く。押した先の入力欄は通常の確認画面を流用する。
 */
@Composable
fun ProvisionalInputSection(onStartProvisional: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "バーコードが付いていない本は、暫定の名前で記録できます。後から正式な作品へ紐づけられます。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onStartProvisional, modifier = Modifier.fillMaxWidth()) {
            Text("暫定の名前で記録する")
        }
    }
}
