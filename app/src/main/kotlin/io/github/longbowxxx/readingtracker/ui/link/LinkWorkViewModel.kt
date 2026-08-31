package io.github.longbowxxx.readingtracker.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import io.github.longbowxxx.readingtracker.domain.port.ProvisionalVolume
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.usecase.LinkProvisionalWorkCommand
import io.github.longbowxxx.readingtracker.domain.usecase.LinkProvisionalWorkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 紐づけ先の入力内容。 */
data class LinkDraft(
    val target: ProvisionalVolume,
    val isbnInput: String = "",
    val title: String = "",
    val volumeNumberText: String = "",
    val author: String = "",
    val publisher: String = "",
    val publishedDate: String? = null,
    val isLookingUp: Boolean = false,
    val message: String? = null,
)

data class LinkUiState(
    val provisionalVolumes: List<ProvisionalVolume> = emptyList(),
    val draft: LinkDraft? = null,
    val completedMessage: String? = null,
)

/**
 * 暫定記録を正式な作品へ紐づける（User Story 3 / FR-008）。
 *
 * ISBN が分かるなら書誌を引いて埋める。分からなければタイトルを直接入力してもよい。
 */
@HiltViewModel
class LinkWorkViewModel
@Inject
constructor(
    private val repository: ReadingRepository,
    private val bibliographySource: BibliographySource,
    private val linkProvisionalWorkUseCase: LinkProvisionalWorkUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LinkUiState())
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val volumes = repository.listProvisionalVolumes()
            _uiState.update { it.copy(provisionalVolumes = volumes) }
        }
    }

    fun selectTarget(target: ProvisionalVolume) {
        _uiState.update { it.copy(draft = LinkDraft(target = target), completedMessage = null) }
    }

    fun cancel() {
        _uiState.update { it.copy(draft = null) }
    }

    fun updateDraft(transform: (LinkDraft) -> LinkDraft) {
        _uiState.update { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    /** ISBN から書誌を引いて入力欄を埋める。失敗しても手入力で続けられる（FR-007）。 */
    fun lookup() {
        val draft = _uiState.value.draft ?: return
        val isbn =
            Isbn.parse(draft.isbnInput).getOrElse { error ->
                updateDraft { it.copy(message = error.message) }
                return
            }

        viewModelScope.launch {
            updateDraft { it.copy(isLookingUp = true, message = null) }
            when (val result = bibliographySource.lookup(isbn)) {
                is BibliographyResult.Found ->
                    updateDraft {
                        it.copy(
                            isLookingUp = false,
                            title = result.record.rawTitle,
                            volumeNumberText = result.record.volumeNumber?.toString().orEmpty(),
                            author = result.record.author.orEmpty(),
                            publisher = result.record.publisher.orEmpty(),
                            publishedDate = result.record.publishedDate,
                            message = null,
                        )
                    }

                else ->
                    updateDraft {
                        it.copy(
                            isLookingUp = false,
                            message = "書誌情報を取得できませんでした。タイトルを直接入力してください。",
                        )
                    }
            }
        }
    }

    fun link() {
        val draft = _uiState.value.draft ?: return
        if (draft.title.isBlank()) {
            updateDraft { it.copy(message = "タイトルを入力してください。") }
            return
        }

        viewModelScope.launch {
            val result =
                linkProvisionalWorkUseCase.execute(
                    LinkProvisionalWorkCommand(
                        volumeId = draft.target.volumeId,
                        isbn = Isbn.parse(draft.isbnInput).getOrNull(),
                        rawTitle = draft.title,
                        volumeNumberOverride = draft.volumeNumberText.toIntOrNull(),
                        author = draft.author.takeIf { it.isNotBlank() },
                        publisher = draft.publisher.takeIf { it.isNotBlank() },
                        publishedDate = draft.publishedDate,
                    ),
                )

            if (result == null) {
                updateDraft { it.copy(message = "対象の記録が見つかりませんでした。") }
                return@launch
            }

            _uiState.update { it.copy(draft = null, completedMessage = "「${draft.title}」に紐づけました") }
            reload()
        }
    }

    fun consumeCompletedMessage() {
        _uiState.update { it.copy(completedMessage = null) }
    }
}
