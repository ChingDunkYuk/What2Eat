package com.what2eat.feature.preference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 偏好设置页 ViewModel。
 *
 * 按一级分类分组显示具体分类，支持搜索和筛选。
 * 每个分类可设置五档偏好等级和独立的长期硬排除开关。
 */
@HiltViewModel
class PreferenceViewModel @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val foodCategoryRepository: FoodCategoryRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val personId: String = savedStateHandle.get<String>("personId") ?: "person_primary"

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(PreferenceFilter.ALL)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PreferenceUiState> = combine(
        personProfileRepository.observeAll().map { profiles ->
            profiles.firstOrNull { it.id == personId }
        },
        foodCategoryRepository.observeAll(),
        preferenceRepository.observeByPerson(personId),
        combine(_searchQuery, _filter) { query, filter -> query to filter }
    ) { person, allCategories, preferences, (searchQuery, filter) ->
        val prefMap = preferences.associateBy { it.categoryId }

        // 构建分组：按一级分类分组
        val rootCategories = allCategories.filter { it.parentId == null }
        val groups = rootCategories.map { root ->
            val children = allCategories.filter { it.parentId == root.id }
            val items = children.map { child ->
                CategoryPreferenceItem(
                    category = child,
                    preference = prefMap[child.id]
                )
            }
            CategoryPreferenceGroup(rootCategory = root, items = items)
        }

        // 应用搜索和筛选
        val filteredGroups = groups
            .map { group ->
                val filteredItems = group.items.filter { item ->
                    passesSearch(item, group.rootCategory, searchQuery) &&
                    passesFilter(item, filter)
                }
                group.copy(items = filteredItems)
            }
            .filter { it.items.isNotEmpty() }

        PreferenceUiState(
            person = person,
            categoryGroups = filteredGroups,
            searchQuery = searchQuery,
            filter = filter,
            isLoading = false,
            message = _message.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PreferenceUiState()
    )

    /** 搜索输入变化 */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** 设置筛选模式 */
    fun setFilter(filter: PreferenceFilter) {
        _filter.value = filter
    }

    /** 设置偏好等级 */
    fun setPreferenceLevel(categoryId: String, level: Int) {
        viewModelScope.launch {
            preferenceRepository.setPreferenceLevel(personId, categoryId, level)
        }
    }

    /** 设置硬排除 */
    fun setHardExcluded(categoryId: String, excluded: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setHardExcluded(personId, categoryId, excluded)
        }
    }

    // ── 筛选逻辑 ──

    private fun passesSearch(
        item: CategoryPreferenceItem,
        rootCategory: com.what2eat.domain.model.FoodCategory,
        query: String
    ): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return item.category.name.lowercase().contains(q) ||
               rootCategory.name.lowercase().contains(q)
    }

    private fun passesFilter(item: CategoryPreferenceItem, filter: PreferenceFilter): Boolean {
        return when (filter) {
            PreferenceFilter.ALL -> true
            PreferenceFilter.SET_ONLY -> item.isSet
            PreferenceFilter.EXCLUDED_ONLY -> item.isHardExcluded
        }
    }
}
