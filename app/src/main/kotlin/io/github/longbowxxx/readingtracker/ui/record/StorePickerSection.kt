package io.github.longbowxxx.readingtracker.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.longbowxxx.readingtracker.domain.model.Store

/**
 * 店舗の選択と新規登録（FR-030）。
 *
 * 登録は**記録フローの中だけ**で行う。専用の管理画面・編集・削除（F-1）は本スコープ外（FR-031）。
 * 店舗が1件も無い状態から記録を始められるようにするための最小限の入口である。
 */
@Composable
fun StorePickerSection(
    stores: List<Store>,
    selectedStoreId: Long?,
    onSelectStore: (Long) -> Unit,
    onAddStore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newStoreName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "店舗")

        if (stores.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stores.forEach { store ->
                    FilterChip(
                        selected = store.id == selectedStoreId,
                        onClick = { onSelectStore(store.id) },
                        label = { Text(store.name) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newStoreName,
                onValueChange = { newStoreName = it },
                label = { Text(if (stores.isEmpty()) "店舗名を入力して登録" else "店舗を追加") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAddStore(newStoreName)
                    newStoreName = ""
                },
                enabled = newStoreName.isNotBlank(),
            ) {
                Text("追加")
            }
        }
    }
}
