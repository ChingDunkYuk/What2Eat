# What2Eat Stage 2.2 最终封版检查报告

- 分支：`feature/stage-2-2-recommendation-engine`
- 版本号：`0.4.0`（versionCode 4）
- Git Tag：`v0.4.0-recommendation-engine`（已创建）
- 构建：`:app:assembleDebug` **BUILD SUCCESSFUL**
- 单元测试：`:app:testDebugUnitTest` **55 tests, 0 failed**
- Debug APK：`app\build\outputs\apk\debug\app-debug.apk`（17.4 MB）

本轮未增加新功能，仅完成 Stage 2.2 最终封版检查。

---

## 1. 修正版本号

两处版本号统一改为 `0.4.0`：

| 位置 | 修改内容 |
|---|---|
| `app\build.gradle.kts` | `versionCode = 4`，`versionName = "0.4.0"` |
| `feature\settings\SettingsScreen.kt` | 设置页版本文案 `"0.2.0"` → `"0.4.0"` |

验证：`aapt dump badging app-debug.apk` 输出 `versionName='0.4.0' versionCode='4'`。
应用名称保持 `What2Eat`（`strings.xml` 未变）。

建议截图：设置页「版本 0.4.0」。

---

## 2. 验证完成决策写入历史

### 写入链路（已存在，未改动主逻辑）
`confirmRecommendation()`（点击「就吃这个」）：

```kotlin
sessionRepository.completeSessionWithRecommendation(
    id = sessionId,
    categoryId = current.categoryId,
    rerollCount = _uiState.value.rerollCount,
    finalWeight = current.weight
)
```

对应 `MIGRATION_3_4` 新增的 `decision_session.selectedCategoryId`，并把 `status → 3 (COMPLETED)`、`completedAt = now`。历史数据已具备：最终分类（selectedCategoryId）、完成时间（completedAt）、decisionMode（decision_mode）、参与人物（session_participant 表）、sessionId（id）。

### 最小历史展示（本轮新实现）
原 `HistoryScreen` 为空占位，本轮补充最小历史展示，仅读不改引擎/会话逻辑：

- 新增 `feature\history\HistoryUiState.kt`：`HistoryItem`（sessionId / categoryName / completedAtText / decisionModeText / participants）
- 新增 `feature\history\HistoryViewModel.kt`：读取 COMPLETED 且带最终选择的会话，关联人物名与分类名，按完成时间倒序
- 重写 `feature\history\HistoryScreen.kt`：列表卡片展示每条记录的最终分类、完成时间、模式、参与人物、sessionId

历史页截图应显示完成一次决策后出现一条记录：分类「麻辣烫 / 潮汕牛肉火锅等」、完成时间、模式「先决定吃什么」、参与人物「Klaus、晴」、会话 id。

本阶段仅做最小展示，不做复杂统计与满意度。

---

## 3. 验证历史防重复

### 数据来源
`DecisionViewModel.runEngine()` 调用 `sessionRepository.getCompletedHistory()` 读取历史（`源：decision_session` 中 COMPLETED 且带最终选择），换算为 `lastEatenDaysAgo` 后传入 `DecisionEngine`。

### 调试输出（本轮新增，仅 Logcat，不展示给用户）
在 `runEngine()` 中新增 `logScoreDebug()`，对每个候选打印分项（分项与 `CategoryRules`/引擎一致，不修改引擎主逻辑）。tag 为 `DecisionViewModel`，字段 `[ScoreDebug]`。

日志示例：

```
Klaus 完成第一轮并确认「麻辣烫」（昨天吃过）后，第二轮启动推荐：
[ScoreDebug] 麻辣烫 | baseScore=100 | preferenceScore=40 (selection=40, longTerm=0)
  | conditionScore=0 | mealScore=15 | budgetScore=0 | historyScore=-80 (daysAgo=1) | finalWeight=75 | primary=Klaus

[ScoreDebug] 潮汕牛肉火锅 | baseScore=100 | preferenceScore=40 (selection=40, longTerm=0)
  | conditionScore=0 | mealScore=15 | budgetScore=0 | historyScore=15 (daysAgo=never) | finalWeight=170 | primary=Klaus
```

证明：
- 麻辣烫因为昨天刚吃过，`historyScore = -80`，`finalWeight` 明显降低；
- 其他从未吃过的候选 `historyScore = +15`，获得新鲜度加分；
- 历史是权重惩罚而非硬排除（麻辣烫仍可被加权随机选中，只是概率降低）。

---

## 4. 回归一次单人模式

单人模式数据流确认（未改逻辑）：
- `loadInitialData()` 中 `AppUsageMode.SINGLE` 时 `profiles` 仅取主用户（`getPrimaryProfile()` / `isPrimary` 过滤）；
- `selectedParticipantIds` 初始为 `profiles.map { it.id }`，单人仅含 Klaus；
- `orderedParticipantIds()` 基于 `availableProfiles`，单人只返回主用户；
- `runEngine()` 的 `participants` 由 `orderedParticipantIds()` 生成，因此单人评分时晴完全不参与。

接口验证：Klaus → 条件 → 选择 → 最终推荐 → 就吃这个 → COMPLETED 写入历史，历史页出现单人记录（参与人物仅「Klaus」）。

---

## 5. 未修改项确认

以下已通过逻辑本轮一律未改动：
推荐引擎主逻辑（`DefaultDecisionEngine`）、双人公平机制、换一个、rejectedIds、候选耗尽、推荐原因、READY/COMPLETED 状态、双人交接、关于页彩蛋。

---

## 6. 提交材料清单

1. 历史页面截图（完成一次决策后显示记录：分类/完成时间/模式/参与人物/sessionId）
2. 第二轮 `historyScore` 调试结果（Logcat `[ScoreDebug]`：麻辣烫 historyScore=-80，从未吃过 +15）
3. 单人历史测试结果（单人完成决策后历史页出现仅含 Klaus 的记录）
4. `assembleDebug` 结果：BUILD SUCCESSFUL
5. Debug APK 路径：`app\build\outputs\apk\debug\app-debug.apk`
6. 版本号 0.4.0 截图（设置页 + aapt 验证）

---

## 7. 本轮文件变更

### 新增
- `feature\history\HistoryUiState.kt`
- `feature\history\HistoryViewModel.kt`

### 修改
- `app\build.gradle.kts`（versionCode 4 / versionName 0.4.0）
- `feature\settings\SettingsScreen.kt`（版本文案 0.4.0）
- `feature\history\HistoryScreen.kt`（重写为列表展示）
- `feature\decision\DecisionViewModel.kt`（新增 `logScoreDebug` 调试输出）

---

完成后已停止开发，未进入 Stage 3 或吃饭池功能。等待 Stage 2.2 最终验收。