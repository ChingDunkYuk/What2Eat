# What2Eat Stage 2.2 验收报告
## 候选评分、排序与最终推荐

- 分支：`feature/stage-2-2-recommendation-engine`
- 基于：`v0.3.0-decision-input`（Stage 2.1 已验收）
- 数据库版本：v4（迁移路径 v1→v2→v3→v4）
- 构建：`:app:assembleDebug` **BUILD SUCCESSFUL**
- 单元测试：`:app:testDebugUnitTest` **55 tests, 0 failed**（含 Engine 20 项 + 其他 35 项）
- Debug APK：`What2Eat\app\build\outputs\apk\debug\app-debug.apk`（18.4 MB，含 `feature/stage-2-2` 新功能）

本阶段仅实现「模式一：先决定吃什么」。未开发吃饭池、餐厅搜索、地图、AI、账号等禁入功能。

---

## 1. DecisionEngine 完整结构

纯 Kotlin 引擎包：`app\src\main\java\com\what2eat\domain\engine\`

### 接口（`DecisionEngine.kt`）
```kotlin
interface DecisionEngine {
    fun recommend(
        candidates: List<DecisionCandidateInput>,
        participants: List<ParticipantPreference>,
        context: DecisionContext,
        history: List<MealHistoryInput>,
        rejectedIds: Set<String> = emptySet(),
        seed: Long? = null            // 固定 seed 可复现
    ): RecommendationResult
}
```
- 无 Android Context / Room / Compose / ViewModel 依赖。
- 相同输入 + 相同 seed ⇒ 相同结果（用例 `fixedSeed_isReproducible` 验证）。

### 模型
- `DecisionCandidateInput`：categoryId / categoryName / parentCategoryName / disabled / priceLevel / supportedMealModes / attributes / selectionsByPerson
- `ParticipantPreference`：personId / name / isPrimary / longTermLevelByCategory / hardExcludedCategoryIds
- `DecisionContext`：mealModes / moodTags / budgetLevel
- `MealHistoryInput`：categoryId / lastEatenDaysAgo
- `RecommendationItem`：categoryId / weight / matchLevel / reasons
- `RecommendationResult`：recommended / allSurviving / exhausted
- `MatchLevel`：HIGH / MEDIUM / LOW

### 实现（`DefaultDecisionEngine.kt`）
严格流程：**硬过滤 → 评分 → 公平分 → 加权随机 → 最终推荐**。不使用随机数直接从候选抽取；一定是先算权重再做加权随机。

---

## 2. 所有评分规则

评分集中在 `CategoryRules.kt`（规则表集中维护，不散落 UI）。

### 基础分
- 每个候选基础分 `100`。

### 本次选择（5.1）
- 单人：`WANT +40`，`ACCEPT 0`。
- 双人：分别算个人分，再走公平公式。

### 长期偏好（5.2）
- 非常喜欢 +30 / 喜欢 +15 / 无所谓 0 / 不太喜欢 -20 / 非常不喜欢 -40。
- 长期硬排除（hardExcluded）不参与评分，直接在硬过滤排除。

### 今天状态（六）
| 状态 | 匹配 | 加分 | 反匹配 |
|---|---|---|---|
| 想吃点好的 | 火锅/烤肉/日料/牛排/正餐 | +15 | — |
| 快速解决 | 汉堡/炸鸡/粉面/快餐/便利店/煮面 | +20 | — |
| 想吃热的 | 火锅/面/粉/烤肉/热菜 | +15 | — |
| 想吃清淡 | 清淡标签 | +15 | 明显重口 -15 |
| 想吃重口 | 重口标签 | +15 | 清淡 -15 |
| 想吃肉 | 烤肉/牛排/火锅/烧烤/汉堡 | +15 | — |
| 不想吃太撑 | 轻食/小份粉面 | +15 | 火锅/自助/重型聚餐 -15 |
| 适合约会 | 适合约会标签 | +20 | — |
| 没什么要求 | — | 0 | — |

### 预算（七）
- 价格等级：LOW / MEDIUM / HIGH / PREMIUM（`CategoryPriceLevel`）。
- 明显匹配 +10；明显超预算软匹配 -20。
- **`30元以内`（UNDER_30）为硬上限**：HIGH/PREMIUM 直接硬过滤（`hardExceed=true` 接入 `hardFilter`）。
- 其余档位为软评分。

### 用餐方式（七）
- 匹配 +15；不支持直接过滤（在硬过滤校验）。

### 历史防重复（八）
- 昨天吃过 -80 / 3 天内 -60 / 7 天内 -35 / 14 天内 -15 / 30 天以上 +10 / 从未吃过 +15。
- 仅降低概率，不直接排除；单双人共用同一份本次共同历史。

---

## 3. 双人公平公式

分别计算 `scoreA`、`scoreB`（均为「基础分 + 本次选择 + 长期偏好」），再：

```text
averageScore = (scoreA + scoreB) / 2
minScore     = min(scoreA, scoreB)
fairScore    = minScore * 0.7 + averageScore * 0.3
```

之后再加 `conditionScore + mealScore + budgetScore + historyScore` 得最终权重。

原则：优先保证较不喜欢的一方也能接受（`minScore` 占 0.7 主导）。

---

## 4. 加权随机实现说明

不使用「取最高分」。

```kotlin
// cumulative weighted random
private fun weightedPick(items: List<RecommendationItem>, rng: Random): RecommendationItem {
    if (items.size == 1) return items[0]
    val total = items.sumOf { it.weight }
    var r = rng.nextDouble() * total
    for (item in items) {
        r -= item.weight
        if (r <= 0) return item
    }
    return items.last()
}
```
- 权重越高的候选被选中的概率越高，但低权重候选仍可能出现。
- 固定 seed 时结果可复现。

### 权重修正（十）
- `finalWeight = max(1.0, calculatedScore)`，保证最低 1，不为 0 / 负数 / NaN / Infinity（用例 `negativeWeight_clampedToAtLeastOne` 验证）。

---

## 5. 历史防重复逻辑

- 复用 `DecisionSession` COMPLETED 结果作为历史（`getCompletedHistory()` 返回 `(categoryId, completedAt)`）。
- 在 `DecisionViewModel.runEngine` 中把最近完成时间换算为 `lastEatenDaysAgo`。
- 进入 `CategoryRules.historyScore` 按天数区间扣/加分。
- 近期吃过只会降低概率，不硬排除。

---

## 6. 推荐原因结构

`RecommendationReason(type: ReasonType, textKey: String)`。`ReasonType` 支持 9 种（`DecisionEngine.kt`）：

```kotlin
BOTH_WANT            // 双方都想吃
BOTH_ACCEPT          // 双方都接受
ONE_WANT_ONE_ACCEPT  // 一方想、一方接受
LONG_TERM_LIKE       // 长期偏好喜欢
MATCH_MOOD           // 符合今天状态
MATCH_MEAL_MODE      // 符合用餐方式
MATCH_BUDGET         // 符合预算
NOT_EATEN_RECENTLY   // 最近没吃过
NEVER_EATEN          // 从未吃过
```

- Engine 只返回 `type + textKey`，不硬编码整段中文文案。
- UI（`DecisionViewModel.reasonText`）根据 type 生成自然中文，如「你们都能接受 / Klaus 想吃 / 符合「想吃热的」/ 最近 21 天没有吃过」。

### 匹配度映射
- 不直接显示数学分数。`weight >= 170` → 很高；`>= 130` → 较高；否则 → 一般。

---

## 7. Room Migration（v3 → v4）

`core\database\Migrations.kt` 新增 `MIGRATION_3_4`：

```sql
ALTER TABLE decision_session
  ADD COLUMN selectedCategoryId TEXT          -- 最终选择分类
, ADD COLUMN rerollCount INTEGER NOT NULL DEFAULT 0
, ADD COLUMN finalWeight REAL NOT NULL DEFAULT 0;

CREATE TABLE decision_recommendation (        -- 推荐快照
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  sessionId TEXT NOT NULL,
  categoryId TEXT NOT NULL,
  rank INTEGER NOT NULL DEFAULT 0,
  weight REAL NOT NULL DEFAULT 0,
  selected INTEGER NOT NULL DEFAULT 0,
  rejected INTEGER NOT NULL DEFAULT 0,
  reasonKeys TEXT NOT NULL DEFAULT '',
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_decision_recommendation_sessionId
  ON decision_recommendation(sessionId);
```

`What2EatDatabase` 版本升至 4，`entities` 加入 `DecisionRecommendationEntity`；`DatabaseModule` 注册 `MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4` 并提供 DAO。

### 数据模型补充（十六）
- `DecisionSession` 增加 `selectedCategoryId / rerollCount / finalWeight`（+ 已有 `completedAt`）。
- 新增 `DecisionRecommendation`（sessionId / categoryId / rank / weight / selected / rejected / reasonKeys / createdAt）与实体、DAO、仓储。
- 推荐快照保证：会话恢复后结果一致、换一个后不重复、调试可知推荐原因。

---

## 8. 新增/修改文件

### 新增
- `domain\engine\DecisionEngine.kt`（接口 + 模型 + ReasonType）
- `domain\engine\DefaultDecisionEngine.kt`（实现：硬过滤→评分→公平→加权随机）
- `domain\engine\CategoryRules.kt`（集中规则表 + 状态/预算/用餐方式/历史评分）
- `domain\engine\CategoryEnums.kt`（CategoryPriceLevel / CategoryAttribute）
- `domain\model\DecisionRecommendation.kt`（领域模型）
- `core\database\entity\DecisionRecommendationEntity.kt`
- `core\database\dao\DecisionRecommendationDao.kt`
- `test\...\domain\engine\DecisionEngineTest.kt`（20 个用例）

### 修改
- `core\database\Migrations.kt`（MIGRATION_3_4）
- `core\database\What2EatDatabase.kt`（版本 4 + 实体 + DAO）
- `core\database\dao\DecisionSessionDao.kt`（completeWithRecommendation / getCompletedWithSelection）
- `core\database\di\DatabaseModule.kt`（注册 v1→v4 迁移 + DAO）
- `core\database\entity\DecisionSessionEntity.kt`（3 个新列）
- `data\di\RepositoryModule.kt`（提供 DecisionEngine）
- `data\repository\DecisionSessionRepositoryImpl.kt`（完整/推荐/历史方法 + mapper）
- `domain\repository\DecisionSessionRepository.kt`（接口扩展）
- `domain\model\DecisionSession.kt`（3 个新字段）
- `feature\decision\DecisionUiState.kt`（RECOMMENDATION/COMPLETED 步骤 + 推荐状态）
- `feature\decision\DecisionViewModel.kt`（生成推荐/换一个/确认/恢复/原因文案）
- `feature\decision\DecisionFlowScreen.kt`（推荐页 + 完成页 + 结果页「生成最终推荐」按钮）

---

## 9. 单元测试结果

`:app:testDebugUnitTest` → **BUILD SUCCESSFUL**

- 总测试数：**55**（DecisionEngineTest 20 + PrimaryProfileTest 14 + DecisionFlowStateTest 4 + 其余 17）
- 失败：**0**

Engine 覆盖的需求用例（对应任务第十九节）：
1. `singleCandidate_returnsIt` 一个候选
2. `twoCandidates_returnsOne` 两个候选
3. `fixedSeed_isReproducible` 固定 seed（含相同输入同 seed 相同结果）
4. `negativeWeight_clampedToAtLeastOne` 权重负修正为 1
5. `bothWant_generatesBothWantReason` 双方都 WANT
6. `oneWantOneAccept_generatesMixedReason` 一方 WANT 一方 ACCEPT
7. `longTermPreference_affectsWeight` 双方长期偏好差异
8. `recentlyEaten_reducesWeightButNotExcludes` 近期刚吃过（降权不排除）
9. `neverEaten_addsBonus` 从未吃过
10. `hardExcluded_neverSurvives` hardExcluded 永不出现
11. `notToday_neverSurvives` NOT_TODAY 永不出现
12. `rejectedIds_neverReappear` rejectedIds 永不重现
13. `rerollToLast_oneLeft` 换到最后一个
14. `rerollExhausted` 全部换完
15. 单人评分（覆盖于 singleCandidate / recentlyEaten 等）
16. `dualFairScore_usesMinAndAverage` 双人公平分
17. `fixedSeed_isReproducible` 相同 seed 相同结果
- 附加：`mealModeConflict_filtered` 用餐方式冲突过滤、`moodMatch_addsBonusAndReason` 状态匹配加分、`hardBudget_under30_excludesPremium` 预算硬限制

---

## 10. assembleDebug 结果

```text
:app:assembleDebug → BUILD SUCCESSFUL in 37s
```

---

## 11. Debug APK 路径

```
What2Eat\app\build\outputs\apk\debug\app-debug.apk
```
文件大小：18,418,266 字节（约 18.4 MB），debug 签名，Android API 26+。

---

## 12. 验收流程（录制视频建议）

### 单人
Klaus → 本次条件 → 本次选择 → 候选 → 最终推荐 → 查看推荐原因 → 换一个 → 得到新结果 → 就吃这个 → 决定完成。
- 验证：Klaus 长期偏好影响结果；晴完全不影响单人推荐。

### 双人
Klaus + 晴 → 双方选择 → 共同候选 → 最终推荐 → 换一个 → 最终确认。
- 验证：双方公平逻辑；任一方硬排除不出现；WANT + ACCEPT 正常处理。

### 历史防重复
连续完成两次会话：第一次确认某分类；第二次相似条件下，确认上一轮分类权重明显降低。

### 空候选处理
- 原本无候选：「今天没有符合所有条件的选择」→ [返回修改条件]。
- 全部换完：「候选已经看完了」→ [重新看看这些选项][返回修改条件]。
- 不闪退、不清空整个会话。

---

## 13. 已知问题

- 「去找餐厅」为占位按钮，不跳转地图（本阶段不开发）。
- 预算/用餐方式/状态使用分类级规则表估算，非真实餐厅价格（Stage 2.3 可接真实数据）。
- 历史防重复复用 DecisionSession 最终结果，未建独立 MealHistory 大表（够用且迁移最小）。
- 换一个/推荐使用固定 seed `20260807L`，保证同一会话内可复现；如需真人随机可后续开放 seed。
- 未接真实餐厅评分、大众点评、云同步、AI（均属禁入范围）。

---

完成后已停止开发，未进入 Stage 3 或吃饭池功能。等待 Stage 2.2 验收。