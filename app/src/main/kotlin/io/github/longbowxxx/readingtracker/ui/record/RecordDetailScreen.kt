package io.github.longbowxxx.readingtracker.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus

/**
 * 保存済みの記録を開いて直す（User Story 4 / FR-019, FR-020）。
 *
 * 想定する主な用途は「前回中断した巻を読み切ったので読了に変える」。
 * 棚番号の訂正とメモの追記もここで行える。
 *
 * 巻の ID は変えないため、他店舗の棚番号や別の巻の記録には影響しない。
 */
@Composable
fun RecordDetailScreen(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: RecordDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator()
                return@Column
            }

            state.notFound -> {
                Text(text = "対象の記録が見つかりませんでした。", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onClose) { Text("戻る") }
                return@Column
            }
        }

        val draft = state.draft ?: return@Column

        Text(text = draft.workTitle, style = MaterialTheme.typography.headlineSmall)

        if (draft.isProvisional) {
            Text(
                text = "暫定の記録です。正式な作品への紐づけは「暫定記録」から行えます。",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = draft.displayTitle,
            onValueChange = { value -> viewModel.updateDraft { it.copy(displayTitle = value) } },
            label = { Text("この巻の表示名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.volumeNumberText,
            onValueChange = { value -> viewModel.updateDraft { it.copy(volumeNumberText = value.filter(Char::isDigit)) } },
            label = { Text("巻数（不明なら空欄）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "読書状態")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.status == ReadingStatus.READ,
                onClick = { viewModel.updateDraft { it.copy(status = ReadingStatus.READ) } },
                label = { Text("読了") },
            )
            FilterChip(
                selected = draft.status == ReadingStatus.PAUSED,
                onClick = { viewModel.updateDraft { it.copy(status = ReadingStatus.PAUSED) } },
                label = { Text("中断") },
            )
        }

        OutlinedTextField(
            value = draft.shelfNumberText,
            onValueChange = { value -> viewModel.updateDraft { it.copy(shelfNumberText = value) } },
            label = { Text("棚番号（この店舗のみ）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        NoteEditor(
            note = draft.note,
            onNoteChange = { value -> viewModel.updateDraft { it.copy(note = value) } },
        )

        state.savedMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        }

        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
            Text("更新する")
        }
        TextButton(
            onClick = {
                viewModel.consumeSavedMessage()
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("戻る")
        }
    }
}
