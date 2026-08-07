package com.what2eat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 通过 Repository 观察主要用户档案，将名称暴露为 StateFlow。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PersonProfileRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository
        .observePrimary()
        .map { profile ->
            HomeUiState(
                primaryUserName = profile?.name,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeUiState()
        )
}
