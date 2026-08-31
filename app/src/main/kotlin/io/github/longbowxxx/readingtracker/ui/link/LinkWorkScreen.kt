package io.github.longbowxxx.readingtracker.ui.link

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.longbowxxx.readingtracker.domain.port.ProvisionalVolume

/**
 * 暫定記録を正式な作品へ紐づける（User Story 3 / FR-008）。
 *
 * 紐づけても読書状態・棚番号・メモは引き継がれる。巻の ID を変えずに
 * 作品だけを付け替えるため。
 */
@Composable
fun LinkWorkScreen(modifier: Modifier = Modifier, viewModel: LinkWorkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    val draft = state.draft
    if (draft != null) {
        LinkForm(
            draft = draft,
            onDraftChange = viewModel::updateDraft,
            onLookup = viewModel::lookup,
            onLink = viewModel::link,
            onCancel = viewModel::cancel,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "暫定の記録", style = MaterialTheme.typography.headlineSmall)

        state.completedMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = viewModel::consumeCompletedMessage) { Text("閉じる") }
        }

        if (state.provisionalVolumes.isEmpty()) {
            Text(
                text = "暫定の名前で記録した本はありません。バーコードが付いていない本を記録すると、ここに並びます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Text(
            text = "正式な作品へ紐づけると、読書状態・棚番号・メモはそのまま引き継がれます。",
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.provisionalVolumes, key = { it.volumeId }) { volume ->
                ProvisionalRow(volume = volume, onClick = { viewModel.selectTarget(volume) })
            }
        }
    }
}

@Composable
private fun ProvisionalRow(volume: ProvisionalVolume, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = volume.displayTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = volume.volumeNumber?.let { "${it}巻" } ?: "巻数は未入力",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LinkForm(
    draft: LinkDraft,
    onDraftChange: ((LinkDraft) -> LinkDraft) -> Unit,
    onLookup: () -> Unit,
    onLink: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "「${draft.target.displayTitle}」を紐づける", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = draft.isbnInput,
            onValueChange = { value -> onDraftChange { it.copy(isbnInput = value) } },
            label = { Text("ISBN（分かる場合）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onLookup,
            enabled = draft.isbnInput.isNotBlank() && !draft.isLookingUp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("書誌情報を取得して埋める")
        }

        if (draft.isLookingUp) {
            CircularProgressIndicator()
        }

        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onDraftChange { it.copy(title = value) } },
            label = { Text("正式なタイトル") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.volumeNumberText,
            onValueChange = { value -> onDraftChange { it.copy(volumeNumberText = value.filter(Char::isDigit)) } },
            label = { Text("巻数（不明なら空欄）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        draft.message?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = onLink, modifier = Modifier.fillMaxWidth()) {
            Text("この作品に紐づける")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("やめる")
        }
    }
}
