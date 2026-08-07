package com.what2eat.data

import android.util.Log
import com.what2eat.domain.repository.PersonProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主用户档案初始化器。
 *
 * 启动时执行主用户自检，保证：
 * - 数据库恰好存在一个主用户（稳定 id = person_primary）；
 * - 无档案时种子化 Klaus；
 * - person_primary 缺失或降级时修复；
 * - 无法可靠识别时返回 needsPrimarySelection，由界面引导用户指定。
 */
@Singleton
class PersonProfileInitializer @Inject constructor(
    private val repository: PersonProfileRepository
) {
    companion object {
        private const val TAG = "PersonProfileInit"
    }

    /**
     * 启动自检。
     * @return true 表示主用户身份已就绪；false 表示需要用户在界面指定主用户。
     */
    suspend fun initializePrimaryIfNeeded(): Boolean {
        // 情况 B 多档案且无法识别时，可能返回 needsPrimarySelection
        val result = repository.settlePrimaryProfile()

        if (result.needsPrimarySelection) {
            Log.w(TAG, "主用户身份无法自动识别，需要用户指定。候选: " +
                result.candidatesForSelection.joinToString { it.name })
            return false
        }

        if (result.primary == null) {
            Log.e(TAG, "主用户自检后仍无主用户，请检查数据完整性")
            return false
        }

        Log.d(TAG, "主用户自检完成: id=${result.primary.id}, name=${result.primary.name}, " +
            "isPrimary=${result.primary.isPrimary}, enabled=${result.primary.enabled}, " +
            "sort=${result.primary.sortOrder}, repaired=${result.repaired}")
        return true
    }
}