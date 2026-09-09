package com.what2eat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.SavedOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 观察主要用户档案（isPrimary=true）和活动决策会话。
 * 当存在 READY 会话时，计算候选数量用于"继续本次决定"卡片文案。
 *
 * v0.9.0：观察「待整理」数量——待整理条目不参与池决策推荐，
 * 堆积等于白收集；首页给直达提醒（吃饭池 Tab 角标之外的主动引导）。
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val repository: PersonProfileRepository,
    private val sessionRepository: DecisionSessionRepository,
    savedOptionRepository: SavedOptionRepository
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

    private val needsReviewCount: Flow<Int> =
        savedOptionRepository.observeAll().map { options ->
            options.count { it.importStatus == ImportStatus.NEEDS_REVIEW }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observePrimaryProfile(),
        sessionWithCount,
        needsReviewCount
    ) { profile, sessionAndCount, reviewCount ->
        HomeUiState(
            primaryUserName = profile?.name,
            isLoading = false,
            hasActiveSession = sessionAndCount.first != null,
            candidateCount = sessionAndCount.second,
            needsReviewCount = reviewCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState()
    )
}
