package com.what2eat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 观察主要用户档案和活动决策会话。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PersonProfileRepository,
    private val sessionRepository: DecisionSessionRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observePrimary(),
        sessionRepository.observeActiveSession()
    ) { profile, activeSession ->
        HomeUiState(
            primaryUserName = profile?.name,
            isLoading = false,
            hasActiveSession = activeSession != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState()
    )
}
