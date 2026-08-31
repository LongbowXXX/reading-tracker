package io.github.longbowxxx.readingtracker.ui.visit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.longbowxxx.readingtracker.domain.reading.NextVolume
import io.github.longbowxxx.readingtracker.domain.usecase.VisitListItem

/**
 * 来店時に「この店で読める続き」を一覧する（User Story 2 / B-1, B-2, B-3）。
 *
 * 各行に**棚番号**と**次に読むべき巻**を出す。棚番号は作品の代表値ではなく、
 * その次に読むべき巻を探すための番号である（長期連載では巻によって棚が分かれるため）。
 */
@Composable
fun VisitScreen(
    onOpenRecord: (volumeId: Long, storeId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VisitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 記録して戻ってきたときに古い一覧を見せない。画面へ戻るたびに引き直す
    LaunchedEffect(Unit) { viewModel.refresh() }

    if (state.selectedStoreId == null) {
        StoreSelectScreen(stores = state.stores, onSelectStore = viewModel::selectStore, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.selectedStore?.name ?: "店舗",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = viewModel::clearSelection) { Text("店舗を変える") }
        }

        when {
            state.isLoading -> CircularProgressIndicator()

            state.items.isEmpty() -> EmptyState(storeName = state.selectedStore?.name)

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items, key = { it.workId }) { item ->
                        VisitListRow(
                            item = item,
                            onOpen = {
                                val volumeId = item.editableVolumeId
                                val storeId = state.selectedStoreId
                                if (volumeId != null && storeId != null) onOpenRecord(volumeId, storeId)
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun VisitListRow(item: VisitListItem, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = item.workTitle, style = MaterialTheme.typography.titleMedium)
            Text(text = nextVolumeLabel(item.nextVolume), style = MaterialTheme.typography.bodyMedium)
            Text(
                // 未入力であることが分かる表示にする（FR-022）
                text = item.shelfNumber?.let { "棚番号: ${it.value}" } ?: "棚番号: 未入力",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 次に読むべき巻の表示文言（B-3）。
 *
 * `Next` はシステムが実在を確かめていない巻番号なので、断定的に書かない。
 */
private fun nextVolumeLabel(nextVolume: NextVolume): String = when (nextVolume) {
    is NextVolume.Paused ->
        nextVolume.volumeNumber?.let { "次: ${it}巻（読みかけ）" } ?: "次: 読みかけの巻"

    is NextVolume.Next -> "次: ${nextVolume.volumeNumber}巻"

    NextVolume.Unknown -> "次に読む巻は不明"
}
