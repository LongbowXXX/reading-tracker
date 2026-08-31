package io.github.longbowxxx.readingtracker.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.longbowxxx.readingtracker.domain.model.Store
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.usecase.VisitListItem
import io.github.longbowxxx.readingtracker.domain.usecase.VisitListUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisitUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,
    val items: List<VisitListItem> = emptyList(),
    val isLoading: Boolean = false,
) {
    val selectedStore: Store? get() = stores.firstOrNull { it.id == selectedStoreId }
}

/**
 * 来店時の参照（User Story 2）。
 *
 * 店舗を選んでから一覧が出るまでを短く保つ（SC-003: 3操作以内・5秒以内）。
 */
@HiltViewModel
class VisitViewModel
@Inject
constructor(private val repository: ReadingRepository, private val visitListUseCase: VisitListUseCase) :
    ViewModel() {
    private val _uiState = MutableStateFlow(VisitUiState())
    val uiState: StateFlow<VisitUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stores = repository.listStores()
            _uiState.update { it.copy(stores = stores) }
            // 店舗が1件だけなら選ばせない。操作を1つ減らす（憲法 原則VI）
            stores.singleOrNull()?.let { selectStore(it.id) }
        }
    }

    fun selectStore(storeId: Long) {
        _uiState.update { it.copy(selectedStoreId = storeId, isLoading = true) }
        viewModelScope.launch {
            val items = visitListUseCase.execute(storeId)
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStoreId = null, items = emptyList()) }
    }

    /** 記録して戻ってきた場合に一覧を最新にする。 */
    fun refresh() {
        _uiState.value.selectedStoreId?.let(::selectStore)
    }
}
