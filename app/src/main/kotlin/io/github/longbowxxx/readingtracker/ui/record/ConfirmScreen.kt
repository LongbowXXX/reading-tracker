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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus

/**
 * 取得した書誌情報の確認・修正と、読書状態・棚番号・メモの入力（FR-003, FR-006, FR-007, FR-010, FR-017, FR-020）。
 *
 * 書誌情報を取得できなかった場合も**エラー画面にせず、この画面へ来る**。圏外の個室では
 * 取得失敗が通常経路であり、そのまま手入力で記録を完了できる必要がある（FR-007）。
 *
 * 棚番号は直前の巻から継承済みの値が初期表示される。**空欄のまま保存できる**（FR-017）。
 */
@Composable
fun ConfirmScreen(
    draft: RecordDraft,
    onDraftChange: ((RecordDraft) -> RecordDraft) -> Unit,
    onSave: () -> Unit,
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
        Text(text = "内容を確認して記録", style = MaterialTheme.typography.headlineSmall)

        if (draft.isProvisional) {
            Text(
                text = "暫定の名前で記録します。覚えやすい呼び名で構いません。後から正式な作品へ紐づけられます。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (draft.bibliographyMissing) {
            Text(
                text = "書誌情報を取得できませんでした。手入力で記録できます。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            draft.sourceName?.let {
                Text(text = "取得元: $it", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onDraftChange { it.copy(title = value) } },
            label = { Text("タイトル") },
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

        OutlinedTextField(
            value = draft.author,
            onValueChange = { value -> onDraftChange { it.copy(author = value) } },
            label = { Text("著者") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.publisher,
            onValueChange = { value -> onDraftChange { it.copy(publisher = value) } },
            label = { Text("出版社") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "読書状態")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 「読了」「中断」の2値のみ。購入・所蔵に相当する状態は持たない（憲法 原則II）
            FilterChip(
                selected = draft.status == ReadingStatus.READ,
                onClick = { onDraftChange { it.copy(status = ReadingStatus.READ) } },
                label = { Text("読了") },
            )
            FilterChip(
                selected = draft.status == ReadingStatus.PAUSED,
                onClick = { onDraftChange { it.copy(status = ReadingStatus.PAUSED) } },
                label = { Text("中断") },
            )
        }

        OutlinedTextField(
            value = draft.shelfNumberText,
            onValueChange = { value -> onDraftChange { it.copy(shelfNumberText = value) } },
            label = { Text("棚番号（未入力のまま保存できます）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NoteEditor(
            note = draft.note,
            onNoteChange = { value -> onDraftChange { it.copy(note = value) } },
        )

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("記録する")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("やめる")
        }
    }
}
