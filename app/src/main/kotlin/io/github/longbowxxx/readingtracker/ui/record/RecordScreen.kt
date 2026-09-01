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
import androidx.compose.material3.HorizontalDivider
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
import io.github.longbowxxx.readingtracker.scanner.rememberBarcodeScanner

/**
 * 記録の入口（User Story 1）。
 *
 * **バーコード読み取りを主導線とする**（T001 の判断）。ただし棚番号シールがバーコードを
 * 覆っている場合に備え、**手入力へ1操作で切り替えられる**（FR-003, SC-002）。
 * 手入力はエラー経路ではなく同格の入力手段であり、切り替えに警告や確認を挟まない。
 */
@Composable
fun RecordScreen(modifier: Modifier = Modifier, viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // CameraX + ML Kit の自前実装。下段（192 始まり）を読んでも読み取りを止めない（Issue #1）
    val scanner = rememberBarcodeScanner()

    val requestScan =
        rememberCameraPermissionRequest(
            onGranted = { viewModel.scan(scanner) },
            // 権限が無くても記録は続けられる。手入力へ落とす
            onDenied = { viewModel.switchInputMode() },
        )

    val draft = state.draft
    if (draft != null) {
        ConfirmScreen(
            draft = draft,
            onDraftChange = viewModel::updateDraft,
            onSave = viewModel::save,
            onCancel = viewModel::cancelDraft,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "読んだ巻を記録する", style = MaterialTheme.typography.headlineSmall)

        StorePickerSection(
            stores = state.stores,
            selectedStoreId = state.selectedStoreId,
            onSelectStore = viewModel::selectStore,
            onAddStore = viewModel::addStore,
        )

        HorizontalDivider()

        if (!state.canInput) {
            Text(
                text = "先に店舗を登録してください。棚番号は店舗ごとに記録します。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        if (state.isLookingUp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("書誌情報を取得しています…")
            }
        }

        when (state.inputMode) {
            InputMode.SCAN -> {
                Button(
                    onClick = requestScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLookingUp,
                ) {
                    Text("バーコードを読み取る")
                }
                // 1操作で手入力へ切り替えられること（FR-003, SC-002）
                TextButton(onClick = viewModel::switchInputMode, modifier = Modifier.fillMaxWidth()) {
                    Text("シールで隠れている場合は ISBN を手入力")
                }
            }

            InputMode.MANUAL -> {
                OutlinedTextField(
                    value = state.isbnInput,
                    onValueChange = viewModel::updateIsbnInput,
                    label = { Text("ISBN（10桁または13桁）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::submitManualIsbn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isbnInput.isNotBlank() && !state.isLookingUp,
                ) {
                    Text("この ISBN で記録する")
                }
                TextButton(onClick = viewModel::switchInputMode, modifier = Modifier.fillMaxWidth()) {
                    Text("バーコード読み取りに戻る")
                }
            }
        }

        HorizontalDivider()

        ProvisionalInputSection(onStartProvisional = viewModel::startProvisionalDraft)

        state.inputError?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        state.savedMessage?.let { message ->
            SavedMessage(message = message, onConsumed = viewModel::consumeSavedMessage)
        }
    }
}

@Composable
private fun SavedMessage(message: String, onConsumed: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onConsumed) { Text("続けて記録する") }
    }
}
