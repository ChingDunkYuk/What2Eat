package com.what2eat.data.repository

import com.what2eat.domain.repository.BackupFormatException
import com.what2eat.domain.repository.BackupPayload
import com.what2eat.domain.repository.BackupSummary
import com.what2eat.domain.repository.DecisionRecommendationBackup
import com.what2eat.domain.repository.DecisionSessionBackup
import com.what2eat.domain.repository.FoodCategoryBackup
import com.what2eat.domain.repository.PersonCategoryPreferenceBackup
import com.what2eat.domain.repository.PersonOptionPreferenceBackup
import com.what2eat.domain.repository.PersonProfileBackup
import com.what2eat.domain.repository.SavedOptionBackup
import com.what2eat.domain.repository.SavedOptionCollectionBackup
import com.what2eat.domain.repository.SavedOptionTagBackup
import com.what2eat.domain.repository.SessionCategorySelectionBackup
import com.what2eat.domain.repository.SessionParticipantBackup
import com.what2eat.domain.repository.ValidatedBackup
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份 JSON 编解码器（v0.9.3）。
 *
 * 使用 Android 内置 org.json 手写映射——不引入 kotlinx-serialization：
 * KSP1 解析内核与编译器插件存在兼容性问题（引用 serialization 类型
 * 会被解析为 error.NonExistentClass），org.json 零依赖零插件最稳。
 *
 * 解码全部用 opt*（缺失字段取默认值），配合默认值容错导入旧格式文件；
 * formatVersion 不认识即拒绝。
 */
object BackupSerializer {

    /** 当前备份格式版本（绑定 DB schema v7） */
    const val CURRENT_FORMAT_VERSION = 1

    fun encode(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("formatVersion", payload.formatVersion)
        root.put("exportedAt", payload.exportedAt)
        root.put("appVersion", payload.appVersion)
        root.put("usageMode", payload.usageMode)
        root.put("personProfiles", JSONArray(payload.personProfiles.map { it.toJson() }))
        root.put("foodCategories", JSONArray(payload.foodCategories.map { it.toJson() }))
        root.put("personCategoryPreferences", JSONArray(payload.personCategoryPreferences.map { it.toJson() }))
        root.put("decisionSessions", JSONArray(payload.decisionSessions.map { it.toJson() }))
        root.put("sessionParticipants", JSONArray(payload.sessionParticipants.map { it.toJson() }))
        root.put("sessionCategorySelections", JSONArray(payload.sessionCategorySelections.map { it.toJson() }))
        root.put("decisionRecommendations", JSONArray(payload.decisionRecommendations.map { it.toJson() }))
        root.put("savedOptions", JSONArray(payload.savedOptions.map { it.toJson() }))
        root.put("savedOptionCollections", JSONArray(payload.savedOptionCollections.map { it.toJson() }))
        root.put("savedOptionTags", JSONArray(payload.savedOptionTags.map { it.toJson() }))
        root.put("personOptionPreferences", JSONArray(payload.personOptionPreferences.map { it.toJson() }))
        return root.toString(2)
    }

    /**
     * 解析并校验备份 JSON。
     * @throws BackupFormatException JSON 畸形 / 缺 formatVersion / 版本不认识
     */
    fun decode(raw: String): ValidatedBackup {
        val root = try {
            JSONObject(raw)
        } catch (e: org.json.JSONException) {
            throw BackupFormatException("不是有效的备份文件（${e.message ?: "解析失败"}）")
        }
        val formatVersion = root.optInt("formatVersion", -1)
        if (formatVersion == -1) {
            throw BackupFormatException("不是有效的备份文件（缺少 formatVersion）")
        }
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw BackupFormatException(
                "备份格式版本不支持（文件为 v formatVersion=$formatVersion，本应用支持 v$CURRENT_FORMAT_VERSION）"
            )
        }
        val payload = BackupPayload(
            formatVersion = formatVersion,
            exportedAt = root.optLong("exportedAt", 0L),
            appVersion = root.optString("appVersion", ""),
            usageMode = root.optString("usageMode", "SINGLE"),
            personProfiles = root.optArray("personProfiles") { it.toPersonProfile() },
            foodCategories = root.optArray("foodCategories") { it.toFoodCategory() },
            personCategoryPreferences = root.optArray("personCategoryPreferences") { it.toPersonCategoryPreference() },
            decisionSessions = root.optArray("decisionSessions") { it.toDecisionSession() },
            sessionParticipants = root.optArray("sessionParticipants") { it.toSessionParticipant() },
            sessionCategorySelections = root.optArray("sessionCategorySelections") { it.toSessionCategorySelection() },
            decisionRecommendations = root.optArray("decisionRecommendations") { it.toDecisionRecommendation() },
            savedOptions = root.optArray("savedOptions") { it.toSavedOption() },
            savedOptionCollections = root.optArray("savedOptionCollections") { it.toSavedOptionCollection() },
            savedOptionTags = root.optArray("savedOptionTags") { it.toSavedOptionTag() },
            personOptionPreferences = root.optArray("personOptionPreferences") { it.toPersonOptionPreference() }
        )
        return ValidatedBackup(payload = payload, summary = summarize(payload))
    }

    fun summarize(payload: BackupPayload): BackupSummary = BackupSummary(
        profileCount = payload.personProfiles.size,
        optionCount = payload.savedOptions.size,
        tagCount = payload.savedOptionTags.map { it.tagId }.distinct().size,
        sessionCount = payload.decisionSessions.size,
        recommendationCount = payload.decisionRecommendations.size
    )

    // ── 数组取值辅助 ──

    private fun <T> JSONObject.optArray(name: String, map: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            (array.opt(i) as? JSONObject)?.let(map)
        }
    }

    /** 可空 Long 字段（JSON null → Kotlin null） */
    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    // ── PersonProfile ──

    private fun PersonProfileBackup.toJson() = JSONObject()
        .put("id", id).put("name", name).put("isPrimary", isPrimary)
        .put("sortOrder", sortOrder).put("enabled", enabled)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun JSONObject.toPersonProfile() = PersonProfileBackup(
        id = optString("id"), name = optString("name"),
        isPrimary = optBoolean("isPrimary", false),
        sortOrder = optInt("sortOrder", 0), enabled = optBoolean("enabled", true),
        createdAt = optLong("createdAt", 0L), updatedAt = optLong("updatedAt", 0L)
    )

    // ── FoodCategory ──

    private fun FoodCategoryBackup.toJson() = JSONObject()
        .put("id", id).put("name", name).put("parentId", parentId ?: JSONObject.NULL)
        .put("sortOrder", sortOrder).put("enabled", enabled).put("isSystemPreset", isSystemPreset)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun JSONObject.toFoodCategory() = FoodCategoryBackup(
        id = optString("id"), name = optString("name"),
        parentId = optNullableString("parentId"),
        sortOrder = optInt("sortOrder", 0), enabled = optBoolean("enabled", true),
        isSystemPreset = optBoolean("isSystemPreset", true),
        createdAt = optLong("createdAt", 0L), updatedAt = optLong("updatedAt", 0L)
    )

    // ── PersonCategoryPreference ──

    private fun PersonCategoryPreferenceBackup.toJson() = JSONObject()
        .put("personId", personId).put("categoryId", categoryId)
        .put("preferenceLevel", preferenceLevel).put("hardExcluded", hardExcluded)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toPersonCategoryPreference() = PersonCategoryPreferenceBackup(
        personId = optString("personId"), categoryId = optString("categoryId"),
        preferenceLevel = optInt("preferenceLevel", 0),
        hardExcluded = optBoolean("hardExcluded", false),
        updatedAt = optLong("updatedAt", 0L)
    )

    // ── DecisionSession ──

    private fun DecisionSessionBackup.toJson() = JSONObject()
        .put("id", id).put("decisionMode", decisionMode).put("status", status)
        .put("startedAt", startedAt).put("completedAt", completedAt ?: JSONObject.NULL)
        .put("mealModes", mealModes).put("moodTags", moodTags)
        .put("budgetLevel", budgetLevel).put("distanceLevel", distanceLevel)
        .put("selectedCategoryId", selectedCategoryId ?: JSONObject.NULL)
        .put("selectedOptionId", selectedOptionId ?: JSONObject.NULL)
        .put("rerollCount", rerollCount).put("finalWeight", finalWeight)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun JSONObject.toDecisionSession() = DecisionSessionBackup(
        id = optString("id"),
        decisionMode = optInt("decisionMode", 0), status = optInt("status", 0),
        startedAt = optLong("startedAt", 0L), completedAt = optNullableLong("completedAt"),
        mealModes = optString("mealModes"), moodTags = optString("moodTags"),
        budgetLevel = optInt("budgetLevel", 5), distanceLevel = optInt("distanceLevel", 4),
        selectedCategoryId = optNullableString("selectedCategoryId"),
        selectedOptionId = optNullableString("selectedOptionId"),
        rerollCount = optInt("rerollCount", 0), finalWeight = optDouble("finalWeight", 0.0),
        createdAt = optLong("createdAt", 0L), updatedAt = optLong("updatedAt", 0L)
    )

    // ── SessionParticipant ──

    private fun SessionParticipantBackup.toJson() = JSONObject()
        .put("sessionId", sessionId).put("personId", personId)
        .put("selectionOrder", selectionOrder).put("completed", completed)

    private fun JSONObject.toSessionParticipant() = SessionParticipantBackup(
        sessionId = optString("sessionId"), personId = optString("personId"),
        selectionOrder = optInt("selectionOrder", 0), completed = optBoolean("completed", false)
    )

    // ── SessionCategorySelection ──

    private fun SessionCategorySelectionBackup.toJson() = JSONObject()
        .put("sessionId", sessionId).put("personId", personId).put("categoryId", categoryId)
        .put("selectionType", selectionType).put("updatedAt", updatedAt)

    private fun JSONObject.toSessionCategorySelection() = SessionCategorySelectionBackup(
        sessionId = optString("sessionId"), personId = optString("personId"),
        categoryId = optString("categoryId"),
        selectionType = optInt("selectionType", 1), updatedAt = optLong("updatedAt", 0L)
    )

    // ── DecisionRecommendation ──

    private fun DecisionRecommendationBackup.toJson() = JSONObject()
        .put("id", id).put("sessionId", sessionId).put("categoryId", categoryId)
        .put("rank", rank).put("weight", weight)
        .put("selected", selected).put("rejected", rejected)
        .put("reasonKeys", reasonKeys).put("createdAt", createdAt)

    private fun JSONObject.toDecisionRecommendation() = DecisionRecommendationBackup(
        id = optLong("id", 0L), sessionId = optString("sessionId"), categoryId = optString("categoryId"),
        rank = optInt("rank", 0), weight = optDouble("weight", 0.0),
        selected = optBoolean("selected", false), rejected = optBoolean("rejected", false),
        reasonKeys = optString("reasonKeys"), createdAt = optLong("createdAt", 0L)
    )

    // ── SavedOption ──

    private fun SavedOptionBackup.toJson() = JSONObject()
        .put("id", id).put("name", name).put("optionType", optionType).put("enabled", enabled)
        .put("sourcePlatform", sourcePlatform)
        .put("sourceUrl", sourceUrl ?: JSONObject.NULL)
        .put("sourcePackage", sourcePackage ?: JSONObject.NULL)
        .put("areaText", areaText ?: JSONObject.NULL)
        .put("priceLevel", priceLevel ?: JSONObject.NULL)
        .put("estimatedMinutes", estimatedMinutes ?: JSONObject.NULL)
        .put("notes", notes ?: JSONObject.NULL)
        .put("coverUri", coverUri ?: JSONObject.NULL)
        .put("importStatus", importStatus)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("lastChosenAt", lastChosenAt ?: JSONObject.NULL)

    private fun JSONObject.toSavedOption() = SavedOptionBackup(
        id = optString("id"), name = optString("name"),
        optionType = optInt("optionType", 0), enabled = optBoolean("enabled", true),
        sourcePlatform = optInt("sourcePlatform", 0),
        sourceUrl = optNullableString("sourceUrl"),
        sourcePackage = optNullableString("sourcePackage"),
        areaText = optNullableString("areaText"),
        priceLevel = optNullableInt("priceLevel"),
        estimatedMinutes = optNullableInt("estimatedMinutes"),
        notes = optNullableString("notes"),
        coverUri = optNullableString("coverUri"),
        importStatus = optInt("importStatus", 0),
        createdAt = optLong("createdAt", 0L), updatedAt = optLong("updatedAt", 0L),
        lastChosenAt = optNullableLong("lastChosenAt")
    )

    // ── SavedOptionCollection ──

    private fun SavedOptionCollectionBackup.toJson() = JSONObject()
        .put("savedOptionId", savedOptionId).put("collectionType", collectionType)
        .put("createdAt", createdAt)

    private fun JSONObject.toSavedOptionCollection() = SavedOptionCollectionBackup(
        savedOptionId = optString("savedOptionId"),
        collectionType = optInt("collectionType", 0),
        createdAt = optLong("createdAt", 0L)
    )

    // ── SavedOptionTag ──

    private fun SavedOptionTagBackup.toJson() = JSONObject()
        .put("savedOptionId", savedOptionId).put("tagId", tagId)

    private fun JSONObject.toSavedOptionTag() = SavedOptionTagBackup(
        savedOptionId = optString("savedOptionId"), tagId = optString("tagId")
    )

    // ── PersonOptionPreference ──

    private fun PersonOptionPreferenceBackup.toJson() = JSONObject()
        .put("personId", personId).put("savedOptionId", savedOptionId)
        .put("preferenceLevel", preferenceLevel).put("hardExcluded", hardExcluded)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toPersonOptionPreference() = PersonOptionPreferenceBackup(
        personId = optString("personId"), savedOptionId = optString("savedOptionId"),
        preferenceLevel = optInt("preferenceLevel", 0),
        hardExcluded = optBoolean("hardExcluded", false),
        updatedAt = optLong("updatedAt", 0L)
    )
}
