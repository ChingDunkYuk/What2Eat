package com.what2eat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.core.datastore.AppUsageModeDataStore
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 启动门控状态。
 */
sealed interface AppLaunchState {
    /** 初始化中（显示可爱加载页） */
    data object Loading : AppLaunchState
    /** 首次打开，需要引导（欢迎 + 改名） */
    data object NeedOnboarding : AppLaunchState
    /** 进入主界面 */
    data object Ready : AppLaunchState
}

/**
 * 启动门控 ViewModel。
 *
 * 依据 DataStore 的 onboarding_completed 标记决定首屏：
 * 加载页 →（首次）引导页 → 主界面。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsDataStore: AppUsageModeDataStore,
    private val personProfileRepository: PersonProfileRepository
) : ViewModel() {

    val launchState: StateFlow<AppLaunchState> = settingsDataStore.onboardingCompletedFlow
        .map { completed ->
            if (completed) AppLaunchState.Ready else AppLaunchState.NeedOnboarding
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppLaunchState.Loading
        )

    /** 引导完成：写入用户名（保留主用户档案其余字段）并标记已完成 */
    private val completing = MutableStateFlow(false)

    fun completeOnboarding(rawName: String) {
        if (completing.value) return
        completing.value = true
        viewModelScope.launch {
            try {
                val name = rawName.trim().ifEmpty { DEFAULT_USER_NAME }
                personProfileRepository.getPrimaryProfile()?.let { profile ->
                    if (profile.name != name) {
                        personProfileRepository.upsert(
                            profile.copy(name = name, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
                settingsDataStore.setOnboardingCompleted()
            } finally {
                completing.value = false
            }
        }
    }

    companion object {
        /** 与 PersonProfileInitializer 种子名保持一致 */
        const val DEFAULT_USER_NAME = "Klaus"
    }
}
