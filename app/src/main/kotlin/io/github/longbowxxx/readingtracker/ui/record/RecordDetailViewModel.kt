package io.github.longbowxxx.readingtracker.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.usecase.PlacementUpdate
import io.github.longbowxxx.readingtracker.domain.usecase.UpdateRecordCommand
import io.github.longbowxxx.readingtracker.domain.usecase.UpdateRecordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 記録詳細の編集内容（FR-019, FR-020）。 */
data class RecordDetailDraft(
    val workTitle: String,
    val displayTitle: String,
    val volumeNumberText: String,
    val status: ReadingStatus,
    val shelfNumberText: String,
    val note: String,
    val isProvisional: Boolean,
)

data class RecordDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val draft: RecordDetailDraft? = null,
    val savedMessage: String? = null,
)

/**
 * 保存した記録の確認・修正（User Story 4 / FR-019, FR-020）。
 *
 * 「中断として記録した巻を、次の来店で読み切って読了に直す」がここを通る。
 * 巻の ID は変えずに内容だけを更新するため、棚番号と他店舗の記録は影響を受けない。
 */
@HiltViewModel
class RecordDetailViewModel
@Inject
constructor(
    private val repository: ReadingRepository,
    private val updateRecordUseCase: UpdateRecordUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val volumeId: Long = checkNotNull(savedStateHandle[ARG_VOLUME_ID])
    private val storeId: Long = checkNotNull(savedStateHandle[ARG_STORE_ID])

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    private var workId: Long? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val volume = repository.findVolume(volumeId)
            if (volume == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            workId = volume.workId

            val work = repository.findWork(volume.workId)
            val reading = repository.findReading(volumeId)
            val placement = repository.listPlacements(storeId, volume.workId).firstOrNull { it.volumeId == volumeId }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    draft =
                    RecordDetailDraft(
                        workTitle = work?.title.orEmpty(),
                        displayTitle = volume.displayTitle,
                        volumeNumberText = volume.volumeNumber?.toString().orEmpty(),
                        status = reading?.status ?: ReadingStatus.PAUSED,
                        shelfNumberText = placement?.shelfNumber?.value.orEmpty(),
                        note = reading?.note.orEmpty(),
                        isProvisional = work?.isProvisional ?: false,
                    ),
                )
            }
        }
    }

    fun updateDraft(transform: (RecordDetailDraft) -> RecordDetailDraft) {
        _uiState.update { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val currentWorkId = workId ?: return

        viewModelScope.launch {
            // 書誌情報の修正。巻の ID は変えないため、読書記録と配架レコードは失われない
            repository.updateVolumeDetails(
                volumeId = volumeId,
                volumeNumber = draft.volumeNumberText.toIntOrNull(),
                isbn = repository.findVolume(volumeId)?.isbn,
                displayTitle = draft.displayTitle,
                publishedDate = repository.findVolume(volumeId)?.publishedDate,
            )

            updateRecordUseCase.execute(
                UpdateRecordCommand(
                    volumeId = volumeId,
                    status = draft.status,
                    note = draft.note,
                    placement =
                    PlacementUpdate(
                        storeId = storeId,
                        workId = currentWorkId,
                        // 空欄は「棚番号は未入力」であって、空文字ではない（FR-017）
                        shelfNumber = draft.shelfNumberText.trim().takeIf { it.isNotBlank() }?.let(::ShelfNumber),
                    ),
                ),
            )

            _uiState.update { it.copy(savedMessage = "更新しました") }
        }
    }

    fun consumeSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    companion object {
        const val ARG_VOLUME_ID = "volumeId"
        const val ARG_STORE_ID = "storeId"
    }
}
