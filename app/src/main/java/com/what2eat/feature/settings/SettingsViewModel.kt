package com.what2eat.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.MeituanCookieStore
import com.what2eat.MeituanLoginActivity
import com.what2eat.domain.repository.BackupFormatException
import com.what2eat.domain.repository.BackupManager
import com.what2eat.domain.repository.ValidatedBackup
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.PRIMARY_PROFILE_ID
import com.what2eat.domain.repository.SECONDARY_PROFILE_ID
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 备份/恢复 UI 状态（v0.9.3，独立于档案状态流） */
data class BackupUiState(
    /** 导出/导入执行中（按钮禁用防重复触发） */
    val isWorking: Boolean = false,
    /** 待确认的导入预览（null = 无导入流程） */
    val pendingImport: ValidatedBackup? = null,
    val message: String? = null,
    /** message 是否为失败提示（着色用） */
    val isError: Boolean = false
)

/**
 * 设置页 ViewModel。
 *
 * 管理使用模式、双人档案、偏好摘要。
 * 主用户身份以稳定 id = "person_primary" 为准，不依赖名称或列表顺序。
 * 支持加载失败态、重试与"修复人物档案"动作。
 *
 * v0.9.3：备份/恢复——导出全量 JSON 到 SAF 文件；导入先解析校验弹预览，确认后全量替换。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val backupManager: BackupManager,
    private val personProfileRepository: PersonProfileRepository,
    private val usageModeRepository: AppUsageModeRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState(isLoading = true))

    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isLoading = true)
            try {
                combine(
                    personProfileRepository.observeAll(),
                    usageModeRepository.observe(),
                    preferenceRepository.observeByPerson(PRIMARY_PROFILE_ID),
                    preferenceRepository.observeByPerson(SECONDARY_PROFILE_ID),
                    combine(_isSaving, _message) { saving, msg -> saving to msg }
                ) { profiles, usageMode, primaryPrefs, secondaryPrefs, (isSaving, message) ->
                    // 主用户：稳定 id 优先，其次 isPrimary=true（不依赖列表顺序）
                    val primary = profiles.firstOrNull { it.id == PRIMARY_PROFILE_ID }
                        ?: profiles.firstOrNull { it.isPrimary }
                    // 第二人物：稳定 id 优先，其次已启用且非主用户
                    val secondary = profiles.firstOrNull { it.id == SECONDARY_PROFILE_ID }
                        ?: profiles.firstOrNull { !it.isPrimary }

                    SettingsUiState(
                        usageMode = usageMode,
                        profiles = profiles,
                        primaryProfile = primary,
                        secondaryProfile = secondary,
                        primaryPreferenceSummary = computeSummary(primaryPrefs),
                        secondaryPreferenceSummary = computeSummary(secondaryPrefs),
                        // 有档案但无主用户：需要修复
                        needsPrimarySelection = profiles.isNotEmpty() && primary == null,
                        candidatesForSelection = profiles,
                        isLoading = false,
                        isSaving = isSaving,
                        message = message
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(
                    isLoading = false,
                    loadError = e.message ?: "加载人物档案失败"
                )
            }
        }
    }

    /** 重试加载 */
    fun retryLoad() {
        observe()
    }

    /**
     * 由用户指定某档案为主用户（情况 B 修复）。
     * 调起仓库将所选档案提升为主用户并重命名为稳定 id。
     */
    fun repairPrimary(profileId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                personProfileRepository.promoteProfileToPrimary(profileId)
                _message.value = "primary_repaired"
            } catch (e: Exception) {
                _message.value = "primary_repair_error"
            }
            _isSaving.value = false
        }
    }

    /**
     * 从偏好列表计算摘要。
     */
    private fun computeSummary(prefs: List<PersonCategoryPreference>): PreferenceSummary {
        val setCount = prefs.count { it.preferenceLevel != 0 }
        val excludedCount = prefs.count { it.hardExcluded }
        return PreferenceSummary(setCount = setCount, excludedCount = excludedCount)
    }

    /**
     * 保存人物名称（通过对话框编辑后调用）。
     */
    fun saveName(personId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        if (trimmedName.length > 20) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = personProfileRepository.getById(personId)
                if (existing != null) {
                    personProfileRepository.upsert(
                        existing.copy(name = trimmedName, updatedAt = System.currentTimeMillis())
                    )
                } else {
                    val isPrimary = personId == PRIMARY_PROFILE_ID
                    personProfileRepository.upsert(
                        PersonProfile(
                            id = personId,
                            name = trimmedName,
                            isPrimary = isPrimary,
                            sortOrder = if (isPrimary) 0 else 1,
                            enabled = true
                        )
                    )
                }
                _message.value = "name_saved"
            } catch (e: Exception) {
                _message.value = "name_save_error"
            }
            _isSaving.value = false
        }
    }

    /**
     * 切换使用模式。
     * 只启停第二人物（稳定 id = person_secondary），绝不动主用户。
     */
    fun setUsageMode(mode: AppUsageMode) {
        viewModelScope.launch {
            _isSaving.value = true
            usageModeRepository.set(mode)

            if (mode == AppUsageMode.SINGLE) {
                // 单人：仅停用第二人物，不删除、不改主用户
                val secondary = personProfileRepository.getById(SECONDARY_PROFILE_ID)
                    ?: personProfileRepository.getAllNow().firstOrNull { !it.isPrimary }
                secondary?.let {
                    personProfileRepository.upsert(it.copy(enabled = false, updatedAt = System.currentTimeMillis()))
                }
            } else {
                // 双人：确保第二人物存在且启用
                val secondary = personProfileRepository.getById(SECONDARY_PROFILE_ID)
                    ?: personProfileRepository.getAllNow().firstOrNull { !it.isPrimary }
                if (secondary != null) {
                    if (!secondary.enabled) {
                        personProfileRepository.upsert(secondary.copy(enabled = true, updatedAt = System.currentTimeMillis()))
                    }
                } else {
                    personProfileRepository.upsert(
                        PersonProfile(
                            id = SECONDARY_PROFILE_ID,
                            name = "另一半",
                            isPrimary = false,
                            sortOrder = 1,
                            enabled = true
                        )
                    )
                }
            }
            _isSaving.value = false
            _message.value = "mode_changed"
        }
    }

    /** 清除消息 */
    fun clearMessage() {
        _message.value = null
    }

    // ── 备份/恢复（v0.9.3） ──

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** 导出备份的默认文件名（what2eat-backup-20260904-1530.json） */
    fun defaultBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "what2eat-backup-$stamp.json"
    }

    /** 导出到 SAF 文件（CreateDocument 回调） */
    fun exportTo(uri: Uri) {
        if (_backupState.value.isWorking) return
        _backupState.value = _backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            try {
                val json = backupManager.exportAll()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法打开导出文件")
                }
                _backupState.value = BackupUiState(message = "备份已导出")
            } catch (e: Exception) {
                _backupState.value = BackupUiState(
                    message = "导出失败：${e.message ?: "未知错误"}"
                )
            }
        }
    }

    /** 读取 SAF 文件并解析校验（OpenDocument 回调），成功后 pendingImport 置位弹确认 */
    fun importFrom(uri: Uri) {
        if (_backupState.value.isWorking) return
        _backupState.value = _backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("无法读取所选文件")
                }
                val validated = backupManager.parseAndValidate(json)
                _backupState.value = BackupUiState(pendingImport = validated)
            } catch (e: BackupFormatException) {
                _backupState.value = BackupUiState(message = e.message, isError = true)
            } catch (e: Exception) {
                _backupState.value = BackupUiState(
                    message = "导入失败：${e.message ?: "文件不可读"}",
                    isError = true
                )
            }
        }
    }

    /** 确认导入：全量替换当前数据 */
    fun confirmImport() {
        val pending = _backupState.value.pendingImport ?: return
        _backupState.value = _backupState.value.copy(isWorking = true)
        viewModelScope.launch {
            try {
                backupManager.importAll(pending)
                _backupState.value = BackupUiState(message = "数据已恢复（备份导出于 ${formatExportedAt(pending)}）")
            } catch (e: Exception) {
                _backupState.value = BackupUiState(
                    message = "导入失败：${e.message ?: "未知错误"}"
                )
            }
        }
    }

    /** 取消导入预览 */
    fun dismissImportPreview() {
        _backupState.value = _backupState.value.copy(pendingImport = null)
    }

    /** 消费备份操作提示 */
    fun consumeBackupMessage() {
        _backupState.value = _backupState.value.copy(message = null)
    }

    private fun formatExportedAt(backup: ValidatedBackup): String {
        val millis = backup.payload.exportedAt
        if (millis <= 0L) return "未知时间"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    // ── v1.1.0：美团登录态（可选，提升店名抓取成功率） ──

    private val _meituanLoggedIn = MutableStateFlow(false)
    val meituanLoggedIn: StateFlow<Boolean> = _meituanLoggedIn.asStateFlow()

    /** 刷新登录态（设置页 onResume 时调用：登录页返回后同步最新状态） */
    fun refreshMeituanLogin() {
        _meituanLoggedIn.value = MeituanCookieStore.isLoggedIn()
    }

    /** 跳转美团登录页（设置页点击进入） */
    fun openMeituanLogin() {
        val intent = Intent(appContext, MeituanLoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    /** 退出美团登录：清除 CookieManager 登录态 cookie */
    fun logoutMeituan() {
        MeituanCookieStore.logout()
        _meituanLoggedIn.value = false
    }
}