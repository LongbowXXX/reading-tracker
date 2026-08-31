package io.github.longbowxxx.readingtracker.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.model.Store
import io.github.longbowxxx.readingtracker.domain.port.BarcodeScanner
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.port.ScanResult
import io.github.longbowxxx.readingtracker.domain.usecase.RecordCommand
import io.github.longbowxxx.readingtracker.domain.usecase.RecordVolumeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 入力手段。バーコードと手入力は同格であり、1操作で相互に切り替えられる（FR-003）。 */
enum class InputMode {
    /** バーコード読み取り。T001 の判断により、こちらを主導線とする。 */
    SCAN,

    /** ISBN 手入力。棚番号シールがバーコードを覆っている場合に用いる。例外処理ではない。 */
    MANUAL,
}

/** 確認・修正中の1冊分の入力内容（FR-006, FR-019）。 */
data class RecordDraft(
    val isbn: Isbn?,
    val title: String,
    val volumeNumberText: String,
    val author: String,
    val publisher: String,
    val publishedDate: String?,
    val status: ReadingStatus,
    val shelfNumberText: String,
    val note: String,
    val sourceName: String?,
    val bibliographyMissing: Boolean,
)

data class RecordUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,
    val inputMode: InputMode = InputMode.SCAN,
    val isbnInput: String = "",
    val inputError: String? = null,
    val isLookingUp: Boolean = false,
    val draft: RecordDraft? = null,
    val savedMessage: String? = null,
) {
    val canInput: Boolean get() = selectedStoreId != null
}

@HiltViewModel
class RecordViewModel
@Inject
constructor(
    private val repository: ReadingRepository,
    private val bibliographySource: BibliographySource,
    private val recordVolumeUseCase: RecordVolumeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    init {
        reloadStores()
    }

    private fun reloadStores() {
        viewModelScope.launch {
            val stores = repository.listStores()
            _uiState.update { state ->
                state.copy(stores = stores, selectedStoreId = state.selectedStoreId ?: stores.firstOrNull()?.id)
            }
        }
    }

    fun selectStore(storeId: Long) {
        _uiState.update { it.copy(selectedStoreId = storeId) }
    }

    /** 記録フローの中から店舗を新規登録する（FR-030）。編集・削除は本スコープ外（FR-031）。 */
    fun addStore(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val store = repository.createStore(trimmed)
            _uiState.update { it.copy(stores = it.stores + store, selectedStoreId = store.id) }
        }
    }

    /** バーコードと手入力を1操作で切り替える（FR-003）。 */
    fun switchInputMode() {
        _uiState.update {
            val next = if (it.inputMode == InputMode.SCAN) InputMode.MANUAL else InputMode.SCAN
            it.copy(inputMode = next, inputError = null)
        }
    }

    fun updateIsbnInput(value: String) {
        _uiState.update { it.copy(isbnInput = value, inputError = null) }
    }

    /**
     * バーコードを読み取る。読み取りを閉じられた場合は**エラーにせず手入力へ切り替える**
     * （FR-003、契約 contracts/barcode-scanner.md）。
     */
    fun scan(scanner: BarcodeScanner) {
        viewModelScope.launch {
            when (val result = scanner.scan()) {
                is ScanResult.Scanned -> acceptIsbnInput(result.rawValue)

                ScanResult.Cancelled -> _uiState.update { it.copy(inputMode = InputMode.MANUAL, inputError = null) }

                is ScanResult.Unavailable ->
                    _uiState.update {
                        it.copy(
                            inputMode = InputMode.MANUAL,
                            inputError = "カメラを使えませんでした。ISBN を手入力してください。",
                        )
                    }
            }
        }
    }

    fun submitManualIsbn() {
        acceptIsbnInput(_uiState.value.isbnInput)
    }

    private fun acceptIsbnInput(raw: String) {
        val parsed = Isbn.parse(raw)
        val isbn =
            parsed.getOrElse { error ->
                _uiState.update { it.copy(inputError = error.message, isbnInput = raw) }
                return
            }

        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, inputError = null, isbnInput = isbn.value) }
            val result = bibliographySource.lookup(isbn)
            _uiState.update { state -> state.copy(isLookingUp = false, draft = result.toDraft(isbn)) }
            suggestShelfNumber()
        }
    }

    /**
     * 取得できなかった場合も**エラー画面にせず手入力の下書きへ落とす**（FR-007）。
     * 圏外の個室では取得失敗が通常経路である。
     */
    private fun BibliographyResult.toDraft(isbn: Isbn): RecordDraft = when (this) {
        is BibliographyResult.Found ->
            RecordDraft(
                isbn = isbn,
                title = record.rawTitle,
                volumeNumberText = record.volumeNumber?.toString().orEmpty(),
                author = record.author.orEmpty(),
                publisher = record.publisher.orEmpty(),
                publishedDate = record.publishedDate,
                status = ReadingStatus.READ,
                shelfNumberText = "",
                note = "",
                sourceName = record.sourceName,
                bibliographyMissing = false,
            )

        else ->
            RecordDraft(
                isbn = isbn,
                title = "",
                volumeNumberText = "",
                author = "",
                publisher = "",
                publishedDate = null,
                status = ReadingStatus.READ,
                shelfNumberText = "",
                note = "",
                sourceName = null,
                bibliographyMissing = true,
            )
    }

    fun updateDraft(transform: (RecordDraft) -> RecordDraft) {
        _uiState.update { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    /** 直前の巻から棚番号を引き継いで初期値にする（FR-015, FR-016）。 */
    fun suggestShelfNumber() {
        val state = _uiState.value
        val storeId = state.selectedStoreId ?: return
        val draft = state.draft ?: return
        if (draft.title.isBlank() || draft.shelfNumberText.isNotBlank()) return

        viewModelScope.launch {
            val suggestion =
                recordVolumeUseCase.suggestShelfNumber(
                    storeId = storeId,
                    rawTitle = draft.title,
                    volumeNumberOverride = draft.volumeNumberText.toIntOrNull(),
                )
            if (suggestion != null) {
                updateDraft { it.copy(shelfNumberText = suggestion.value) }
            }
        }
    }

    fun cancelDraft() {
        _uiState.update { it.copy(draft = null, isbnInput = "", inputError = null) }
    }

    fun save() {
        val state = _uiState.value
        val storeId = state.selectedStoreId ?: return
        val draft = state.draft ?: return

        if (draft.title.isBlank()) {
            _uiState.update { it.copy(inputError = "タイトルを入力してください。") }
            return
        }

        viewModelScope.launch {
            val result =
                recordVolumeUseCase.execute(
                    RecordCommand(
                        storeId = storeId,
                        isbn = draft.isbn,
                        rawTitle = draft.title,
                        volumeNumberOverride = draft.volumeNumberText.toIntOrNull(),
                        author = draft.author.takeIf { it.isNotBlank() },
                        publisher = draft.publisher.takeIf { it.isNotBlank() },
                        publishedDate = draft.publishedDate,
                        status = draft.status,
                        // 空欄は「棚番号は未入力」であって、空文字ではない（FR-017）
                        shelfNumber = draft.shelfNumberText.trim().takeIf { it.isNotBlank() }?.let(::ShelfNumber),
                        note = draft.note.takeIf { it.isNotBlank() },
                    ),
                )

            val message =
                if (result.updatedExistingReading) {
                    "既に記録があったため更新しました"
                } else {
                    "記録しました"
                }
            _uiState.update { it.copy(draft = null, isbnInput = "", inputError = null, savedMessage = message) }
        }
    }

    fun consumeSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }
}
