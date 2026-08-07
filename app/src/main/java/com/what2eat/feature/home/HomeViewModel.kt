package com.what2eat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 观察主要用户档案（isPrimary=true）和活动决策会话。
 * 当存在 READY 会话时，计算候选数量用于"继续本次决定"卡片文案。
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val repository: PersonProfileRepository,
    private val sessionRepository: DecisionSessionRepository
) : ViewModel() {

    private val sessionWithCount: Flow<Pair<DecisionSession?, Int>> =
        sessionRepository.observeActiveSession().flatMapLatest { session ->
            if (session?.status == SessionStatus.READY) {
                flow<Pair<DecisionSession?, Int>> {
                    val count = try {
                        sessionRepository.generateCandidates(session.id).size
                    } catch (e: Exception) {
                        0
                    }
                    emit(Pair(session, count))
                }
            } else {
                flowOf(Pair(session, 0))
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observePrimaryProfile(),
        sessionWithCount
    ) { profile, sessionAndCount ->
        HomeUiState(
            primaryUserName = profile?.name,
            isLoading = false,
            hasActiveSession = sessionAndCount.first != null,
            candidateCount = sessionAndCount.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState()
    )
}