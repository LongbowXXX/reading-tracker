package io.github.longbowxxx.readingtracker.ui.record

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 巻ごとのメモ（FR-020）。
 *
 * 中断位置の覚書や感想を書く欄。**巻内の読了位置は記録しない**方針のため（FR-011）、
 * 「何ページまで読んだか」に相当する情報はここに自由記述として置かれる。
 * 記録画面と記録詳細画面で共有する。
 */
@Composable
fun NoteEditor(note: String, onNoteChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text("メモ（中断位置の覚書、感想など）") },
        modifier = modifier.fillMaxWidth(),
    )
}
