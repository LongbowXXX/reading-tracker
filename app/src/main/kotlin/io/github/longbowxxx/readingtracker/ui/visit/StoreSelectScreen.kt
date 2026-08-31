package io.github.longbowxxx.readingtracker.ui.visit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.longbowxxx.readingtracker.domain.model.Store

/**
 * 来店した店舗を選ぶ（B-1）。
 *
 * 店舗を1タップ選ぶだけで一覧へ進む。確定ボタンを挟まないのは、
 * 店舗選択から一覧表示までを3操作以内に収めるため（SC-003）。
 *
 * 現在地からの自動選択（B-6）は本スコープ外。
 */
@Composable
fun StoreSelectScreen(stores: List<Store>, onSelectStore: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "どの店舗にいますか", style = MaterialTheme.typography.headlineSmall)

        if (stores.isEmpty()) {
            Text(
                text = "店舗がまだありません。記録画面で1冊記録すると、そのとき入力した店舗がここに並びます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stores, key = { it.id }) { store ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectStore(store.id) },
                ) {
                    Text(
                        text = store.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
