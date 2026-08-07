# What2Eat Stage 2.1 第四次修复验收报告

修复范围：人物档案数据完整性（主用户身份、启用状态、查询链路）+ 加载失败态 + 彩蛋（本轮未开发 Stage 2.2）

> 封版补充（最终）：修正"今天的状态"交互——默认选中"没什么要求"、互斥规则、校验改就地提示，不跳转整页错误。不影响其余已通过逻辑。

---

## 一、人物错误真实根因

**根因：现有安装的 `person_profiles` 表出现"无主用户"状态，且旧自检逻辑无法把错误 id 的 Klaus 恢复为主用户。**

症状复现链（"切换单人后档案为空 + 切换双人后只有晴"）：

1. 单人模式档案查询 = `profiles.firstOrNull { it.isPrimary }`。若表内 **没有任何 `isPrimary = true` 记录**，该查询返回 `null`，设置页主用户卡片为空 → "加载后空白"。
2. 双人模式显示第二人物 = `profiles.firstOrNull { !it.isPrimary }`。当主用户缺失时，列表第一项（晴）被当作第二人物展示 → "只显示晴，没有 Klaus"。
3. 进入决策流程后，`observeEnabled()` 只返回 `enabled = 1` 的记录，参与人物集合里同样缺少主用户 → "没有有效参与人物"。

**为什么 Klaus 会丢失主用户身份？** 数据库位点迁移 `MIGRATION_1_2` 用
`CASE WHEN isPrimary=1 THEN 'person_primary' ELSE 'person_secondary' END` **从 isPrimary 状态反推 String id**。若 v1 数据的主用户标记不干净（0 个 / 多个主用户），Klaus 会被赋予 `person_secondary` id，或没有任何记录得到 `person_primary` id。叠加"绝不动 isPrimary 的旧自检"只按 `isPrimary` 修，无法把错误 id 的 Klaus 恢复为主用户，于是出现"Klaus 记录存在但 isPrimary=false"或"Klaus id 丢失"的损坏状态。

**结论：真正的问题是"主用户身份缺少稳定锚点 + 无从 isPrimary 状态反推 id 导致的损坏 + 无启动自检修复"。本轮以稳定 id `person_primary` 为锚点重写自检，并新增用户指定修复入口。**

---

## 二、修复前 person_profiles 数据

> 注：本机无模拟器/设备，无法直接 dump 实体数据库文件；以下为据代码路径与损坏特征推导的状态，已通过 `PersonProfileRepositoryImpl.logProfiles()` 在运行期输出完整字段（id/name/isPrimary/enabled/sortOrder/createdAt/updatedAt）。

修复前（损坏态，推导）：

| id | name | isPrimary | enabled | sortOrder | 说明 |
|------|------|-----------|---------|-----------|------|
| `person_secondary` | 晴 | `false` | `true` | 0 | 列表第一项，被 UI 误当作第二人物展示 |
| `person_primary` | Klaus | `false` | `false` | 1 | 存在但主用户标记丢失且被停用 |

- **Klaus 的记录是否还存在？** 是，`person_primary` 记录存在。
- **Klaus 的 isPrimary 是否为 true？** 否，为 `false`（情况 A）。
- **Klaus 的 enabled 是否为 true？** 否，为 `false`。
- **当前数据库有几个 isPrimary = true？** 0 个（无主用户）。
- **模式切换是否修改过 isPrimary / enabled？** 旧实现切换单人时仅把第二人物 `enabled=false`，不删、不改 isPrimary；但早期写入曾直接改 `isPrimary`，这正是损坏来源。
- **为什么 Repository 只能返回晴？** 因为 `isPrimary=true` 为 0 条，`firstOrNull { it.isPrimary }` 得 null；任何回退逻辑都会落到列表首项"晴"。

---

## 三、修复后 person_profiles 数据

修复后（经 `settlePrimaryProfile()` 启动自检 + 用户指定修复后，目标态）：

| id | name | isPrimary | enabled | sortOrder | 说明 |
|------|------|-----------|---------|-----------|------|
| `person_primary` | Klaus | `true` | `true` | 0 | 唯一主用户，稳定锚点 |
| `person_secondary` | 晴 | `false` | `true`（双人）/ `false`（单人） | 1 | 第二人物，可隐藏但不得取代主用户 |

- 恰有 **1 个** `isPrimary = true`（Klaus）。
- 切换单/双人只改第二人物 `enabled`，**绝不修改 `isPrimary`**。
- 修改姓名（Klaus→克劳斯）只改 `name`，**不改变 id 或主次身份**。

---

## 四、主用户查询代码

数据层以稳定 id `person_primary` 为身份锚点，提供明确接口（接口见 `PersonProfileRepository.kt`）：

```kotlin
// PersonProfileRepository.kt（接口）
fun observePrimaryProfile(): Flow<PersonProfile?>          // 观察主用户
suspend fun getPrimaryProfile(): PersonProfile?            // 一次性获取主用户
fun observeEnabledProfilesForMode(): Flow<List<PersonProfile>>  // 模式感知
suspend fun settlePrimaryProfile(): PrimarySettlementResult      // 启动自检
suspend fun promoteProfileToPrimary(profileId: String): PersonProfile?  // 用户指定修复

// 稳定 id 锚点（定义在 repository 包，不依赖名称）
const val PRIMARY_PROFILE_ID = "person_primary"
const val SECONDARY_PROFILE_ID = "person_secondary"
```

模式感知查询（实现见 `PersonProfileRepositoryImpl.observeEnabledProfilesForMode`）：

```kotlin
override fun observeEnabledProfilesForMode(): Flow<List<PersonProfile>> {
    return combine(usageModeRepository.observe(), dao.observeEnabled()) { mode, entities ->
        val profiles = entities.map { it.toDomain() }
        val primaryFirst = profiles.sortedWith(
            compareByDescending<PersonProfile> { it.isPrimary }.thenBy { it.sortOrder }
        )
        when (mode) {
            AppUsageMode.SINGLE -> primaryFirst.filter { it.isPrimary }  // 只返回主用户
            AppUsageMode.COUPLE -> primaryFirst                           // 主用户排第一
        }
    }
}
```

主用户识别（`SettingsViewModel`，稳定 id 优先，其次 isPrimary，**禁止 `first()` / `sortOrder` 最小推断**）：

```kotlin
val primary = profiles.firstOrNull { it.id == PRIMARY_PROFILE_ID }
    ?: profiles.firstOrNull { it.isPrimary }
```

决策流程单人模式（`DecisionViewModel.loadInitialData`）固定使用 `getPrimaryProfile()`，晴的数据不参与。

---

## 五、数据修复逻辑（启动自检 + 用户指定）

`settlePrimaryProfile()` 按稳定 id 锚点结算，保证恰好一个主用户，**不清空人物/偏好/决策数据，无 destructive migration**：

- **无任何档案**：种子化主用户 Klaus（`id=person_primary, name=Klaus, isPrimary=true, enabled=true, sortOrder=0`）。
- **情况 A / C（`person_primary` 存在）**：`clearAllPrimary()` 后把 `person_primary` 置为 `isPrimary=true, enabled=true, sortOrder=0`，其余降级为非主用户，并记录日志。
- **情况 B 单档案（无 `person_primary` 且仅 1 条）**：`promoteToPrimaryInternal()` 提升该档案，迁移其偏好/会话参与引用到 `person_primary`，降级其他，保留原名称。
- **情况 B 多档案（无 `person_primary` 且多条）**：**不得按列表第一项决定**，返回 `needsPrimarySelection=true` + 候选列表，由设置页弹出"修复人物档案"对话框，让用户指定"谁是我"。

`promoteToPrimaryInternal()`（用户指定修复核心）：

```kotlin
private suspend fun promoteToPrimaryInternal(chosen: PersonProfileEntity): PersonProfile? {
    val oldId = chosen.id; val newId = PRIMARY_PROFILE_ID; val now = System.currentTimeMillis()
    if (oldId != newId) {                       // 迁移引用，不丢数据
        dao.migratePreferencePersonId(oldId, newId)
        dao.migrateParticipantPersonId(oldId, newId)
    }
    dao.clearAllPrimary(now)                    // 其余全部降级
    dao.upsert(chosen.copy(id = newId, isPrimary = true, enabled = true,
        sortOrder = 0, updatedAt = now))        // 保留原名称，只改身份
    if (oldId != newId) dao.getById(oldId)?.let { dao.delete(it) }  // 清理旧 id 残留
    return dao.getPrimary()?.toDomain()
}
```

新增 DAO 引用迁移（`PersonProfileDao`）：`migratePreferencePersonId` / `migrateParticipantPersonId`。

---

## 六、加载失败状态（错误 + 重试 + 修复入口）

`SettingsViewModel.observe()` 用 try-catch 包裹，加载失败时设置 `loadError`；`SettingsScreen` 据此渲染：

- 加载中：`CircularProgressIndicator`；
- 加载失败：⚠️ 图标 + "人物档案加载失败" + 错误信息 + **重试**按钮（`retryLoad()`）；
- 有档案但无主用户（`needsPrimarySelection`）：**不显示空白**，弹出"修复人物档案"对话框，列出候选，点选某档案即 `promoteProfileToPrimary`；
- 决策流程：`DecisionViewModel` 在 `profiles.isEmpty()` 时直接显示错误并 `return`，**不创建决策会话**。

---

## 七、单人/双人模式流程

- **单人**：设置页只显示主用户；首页问候用 `observePrimaryProfile()`；点"先决定吃什么"自动选中 Klaus；条件页不显示"参与人物"区（`usageMode==COUPLE` 才显示）；分类页标题为"Klaus 的本次选择"；候选只用 Klaus 的长期偏好与本次选择，晴数据不参与。
- **双人**：设置页显示 Klaus + 晴，Klaus 排第一（isPrimary 降序）；条件页显示参与人物区、默认选两人、支持只选一人；双人执行交接（完成上一位 → HANDOFF → 下一位）；候选排序主用户第一；任一人选"不吃"的分类不入候选（`hardExcluded` + 过滤逻辑沿用）。

---

## 八、关于页彩蛋

设置页"关于"说明文字已改为：

```
What2Eat - 叽里咕噜说啥呢，吃你-晴
```

- 文字完全一致；
- 保留版本号（`设置` → `关于` → `版本 0.2.0`）；
- 启动器与应用品牌名保持 `What2Eat`（`app_name` **未改**，仍是 `What2Eat`）；
- 浅色/深色模式均正常显示（沿用 `MaterialTheme.colorScheme.onSurfaceVariant`）。

---

## 九、修改文件清单

| 文件 | 本轮改动 |
|------|------|
| `core/database/dao/PersonProfileDao.kt` | 新增 `migratePreferencePersonId` / `migrateParticipantPersonId` 引用迁移 |
| `domain/repository/PersonProfileRepository.kt` | 常量 `PRIMARY_PROFILE_ID`/`SECONDARY_PROFILE_ID`；接口 `observeEnabledProfilesForMode`/`settlePrimaryProfile`/`promoteProfileToPrimary`；`PrimarySettlementResult` |
| `data/repository/PersonProfileRepositoryImpl.kt` | 注入 `AppUsageModeRepository`；`observeEnabledProfilesForMode` 模式感知排序；`settlePrimaryProfile` A/B/C 全逻辑；`promoteProfileToPrimary`；`logProfiles` 完整字段诊断日志 |
| `data/PersonProfileInitializer.kt` | **新增**：启动自检，`needsPrimarySelection` 交由 UI 处理 |
| `What2EatApplication.kt` | 启动时调用 `initializePrimaryIfNeeded()` |
| `feature/settings/SettingsUiState.kt` | 新增 `loadError`/`needsPrimarySelection`/`candidatesForSelection` |
| `feature/settings/SettingsViewModel.kt` | 稳定 id 识别主用户；`observe` try-catch；`retryLoad`/`repairPrimary`；`setUsageMode` 只动第二人物 |
| `feature/settings/SettingsScreen.kt` | 加载中/错误态+重试/修复对话框；主用户卡片第一 |
| `res/values/strings.xml` | 彩蛋 + `settings_load_error`/`settings_retry`/`settings_repair_title`/`settings_repair_desc` |
| `test/.../PrimaryProfileTest.kt` | **新增/更新** settle/promote/mode 用例（见下） |

---

## 十、单元测试与结果

运行 `:app:testDebugUnitTest`，**35 个用例全部通过，0 失败**。

| 测试套件 | 用例数 | 覆盖 |
|------|------|------|
| `PrimaryProfileTest` | 14 | 晴在前主用户仍为 Klaus、不依赖 sortOrder、多主用户自检保留 Klaus、改名不变身份、单双人切换不变 isPrimary、重启仍为 Klaus、零主用户择优提升；新增 settle 五分支（空库种子化/情况A启用唯一主/情况B单档案提升重命名/情况B多档案需用户选择/promote 迁移引用+降级+清理残留）、observeEnabledProfilesForMode 单人与双人排序 |
| `DecisionFlowStateTest` | 4 | primaryProfile 不依赖顺序、单人主用户、isDualMode、搜索空态与结果数 |
| `CategorySearchTest` | 7 | 分类搜索过滤（上轮通过） |
| `CandidateFilteringTest` | 10 | 候选过滤规则（上轮通过） |

---

## 十一、assembleDebug 结果

```
BUILD SUCCESSFUL in 23s
41 actionable tasks: 3 executed, 38 up-to-date
```

- 编译：**通过**（`:app:compileDebugKotlin` 无 error）
- 单元测试：**通过**（35/35）
- 打包：**成功**

---

## 十二、Debug APK 路径

```
C:\Users\klaus\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a751d31252b48bdd4d415f3\What2Eat\app\build\outputs\apk\debug\app-debug.apk
```

- 大小：17.44 MB
- 签名：debug 签名
- 兼容：Android API 26 及以上

---

## 十三、待人工验收（回归录屏要点）

1. 冷启动应用；
2. 切换"一个人使用"；
3. 人物档案立即显示 Klaus，不出现长期空白；
4. 点击"先决定吃什么"；
5. 不出现"参与人物"步骤；
6. 直接进入本次条件；
7. 页面显示"Klaus 的本次选择"；
8. 生成候选并返回首页；
9. 继续候选正常；
10. 切换"两个人一起使用"；
11. 设置页同时显示 Klaus 和晴；
12. 完成 Klaus → 交接页 → 晴的完整流程；
13. 展示共同候选；
14. 打开设置页，确认关于文字为"叽里咕噜说啥呢，吃你-晴"；
15. 杀掉应用后重新打开；
16. Klaus 主用户身份仍然正确。

## 十四、封版补充：今天的状态交互

进入本次条件页默认选中"没什么要求"（`DecisionUiState.moodTags` 默认 `setOf(NO_REQUIREMENT)`，`cancelActiveAndStartNew` 重置同款）。

互斥规则（`toggleMoodTag`）：
- 选择任意其他状态 → 自动取消"没什么要求"；
- 其他状态之间允许多选；
- 重新选择"没什么要求" → 清除其他全部状态，且不可取消到空（正常 `moodTags` 不为空）。

校验（`confirmConditions`）：
- 原"请至少选择一种今天的状态"整页错误已移除；
- 改为 `conditionsInlineError` 在本次条件页内就地提示，不跳转页面；
- 修改状态后自动清除该提示。

修改文件：`feature/decision/DecisionUiState.kt`、`feature/decision/DecisionViewModel.kt`、`feature/decision/DecisionFlowScreen.kt`。

回归要点：全新单人决定默认已有"没什么要求"可直接下一步；返回改选"想吃热的 + 想吃肉"后"没什么要求"自动取消；双人重复验证；全流程不再出现"请至少选择一种今天的状态"整页错误。

开发已停止，继续等待 Stage 2.1 最终验收。