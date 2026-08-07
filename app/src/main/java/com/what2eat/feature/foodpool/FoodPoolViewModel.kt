package com.what2eat.feature.foodpool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.foodpool.FoodPoolFilter
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.repository.SavedOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 池页筛选 Tab */
enum class PoolTab(val label: String) {
    ALL("全部"),
    FREQUENT("常吃"),
    VISITED("吃过"),
    WANT_TO_TRY("待尝试"),
    TAKEOUT("外卖"),
    HOME_COOK("在家做"),
    AVOIDED("踩雷"),
    NEEDS_REVIEW("待整理")
}

/** 池页排序方式 */
enum class PoolSort(val label: String) {
    RECENTLY_ADDED("最近添加"),
    NAME("名称"),
    RECENTLY_VISITED("最近吃过"),
    RECENTLY_CHOSEN("最近选择")
}

data class FoodPoolUiState(
    val isLoading: Boolean = true,
    val items: List<SavedOption> = emptyList(),
    val selectedTab: PoolTab = PoolTab.ALL,
    val sort: PoolSort = PoolSort.RECENTLY_ADDED,
    val query: String = ""
)

@HiltViewModel
class FoodPoolViewModel @Inject constructor(
    private val repository: SavedOptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodPoolUiState())
    val uiState: StateFlow<FoodPoolUiState> = _uiState.asStateFlow()

    private val selectedTab = MutableStateFlow(PoolTab.ALL)
    private val sortMode = MutableStateFlow(PoolSort.RECENTLY_ADDED)
    private val queryFlow = MutableStateFlow("")

    init {
        observePool()
    }

    private fun observePool() {
        viewModelScope.launch {
            val dataFlow = combine(
                repository.observeAll().distinctUntilChanged(),
                repository.observeAllCollections().distinctUntilChanged(),
                repository.observeAllTags().distinctUntilChanged()
            ) { options, collections, tagsByOption ->
                Triple(options, collections, tagsByOption)
            }
            val filterFlow = combine(
                selectedTab,
                sortMode,
                queryFlow
            ) { tab, sort, query ->
                Triple(tab, sort, query)
            }

            combine(dataFlow, filterFlow) { data, filter ->
                val (options, collections, tagsByOption) = data
                val (tab, sort, query) = filter

                val collectionsByOption = collections.groupBy { it.savedOptionId }
                    .mapValues { (_, list) -> list.map { it.collectionType }.toSet() }
                val tagsMap = tagsByOption

                val filtered = options
                    .filter { opt ->
                        FoodPoolFilter.matchesTab(
                            opt,
                            collectionsByOption[opt.id] ?: emptySet(),
                            tab.name
                        )
                    }
                    .filter { opt ->
                        FoodPoolFilter.matchesSearch(
                            opt,
                            tagsMap[opt.id]?.toSet() ?: emptySet(),
                            query
                        )
                    }
                    .let { list ->
                        when (sort) {
                            PoolSort.RECENTLY_ADDED ->
                                FoodPoolFilter.sort(list, FoodPoolFilter.SortMode.RECENTLY_ADDED)
                            PoolSort.NAME ->
                                FoodPoolFilter.sort(list, FoodPoolFilter.SortMode.NAME)
                            PoolSort.RECENTLY_VISITED ->
                                FoodPoolFilter.sort(list, FoodPoolFilter.SortMode.RECENTLY_VISITED)
                            PoolSort.RECENTLY_CHOSEN ->
                                FoodPoolFilter.sort(list, FoodPoolFilter.SortMode.RECENTLY_CHOSEN)
                        }
                    }

                FoodPoolUiState(
                    isLoading = false,
                    items = filtered,
                    selectedTab = tab,
                    sort = sort,
                    query = query
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectTab(tab: PoolTab) {
        selectedTab.value = tab
    }

    fun setSort(sort: PoolSort) {
        sortMode.value = sort
    }

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    fun clearQuery() {
        queryFlow.value = ""
    }
}