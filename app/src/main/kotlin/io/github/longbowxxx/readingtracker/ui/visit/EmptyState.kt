package io.github.longbowxxx.readingtracker.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 記録が1件も無いときの表示。
 *
 * 空であること自体は異常ではない。使い始めは必ずここを通るため、
 * 次に何をすればよいかだけを示す。
 */
@Composable
fun EmptyState(storeName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (storeName != null) "$storeName の記録はまだありません" else "記録はまだありません",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "個室で1冊読み終えたら記録してください。次の来店から、この画面に続きが並びます。",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
