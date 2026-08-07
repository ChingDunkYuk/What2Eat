# What2Eat Stage 3.1 验收报告
## 通用搜索承接（地图 / 浏览器 / 复制）

- 分支：`feature/stage-3-1-search-handoff`（基于 `v0.4.0-recommendation-engine`）
- 版本号：`0.4.0`（本轮不改版本号）
- 构建：`:app:assembleDebug` **BUILD SUCCESSFUL**
- 单元测试：`:app:testDebugUnitTest` **64 tests, 0 failed**（新增 SearchQueryBuilderTest 9 项）
- Debug APK：`app\build\outputs\apk\debug\app-debug.apk`（17.6 MB）

本阶段仅实现「通用搜索承接」：地图搜索、浏览器搜索、复制关键词。未接大众点评/美团 Deep Link、地图 SDK、定位权限、餐厅数据等禁入功能。

---

## 1. SearchQueryBuilder 实现说明

文件：`app\src\main\java\com\what2eat\domain\search\SearchQueryBuilder.kt`（纯 Kotlin，无 Android 依赖，可独立单测）

```kotlin
object SearchQueryBuilder {
    fun build(categoryName: String?, areaText: String?): SearchQueryResult
}
// SearchQueryResult(valid: Boolean, query: String, reason: String?)
```

规则：
1. `categoryName` 必须有效（空/空白 → 返回 `valid=false`，reason「搜索关键词不能为空」）；
2. 自动 `trim`；
3. `areaText` 为空：仅分类名，如「潮汕牛肉火锅」；
4. `areaText` 有值：`"$area $categoryName"`，如「佛山南海 潮汕牛肉火锅」。

本阶段 `areaText` 恒为空（无区域输入），完成页直接以 `categoryName` 生成关键词。UI 不允许自行拼接搜索字符串，统一走 builder。

---

## 2. SearchLauncher 实现说明

文件：`app\src\main\java\com\what2eat\domain\search\SearchLauncher.kt`（接口）+ `app\src\main\java\com\what2eat\data\search\AndroidSearchLauncher.kt`（实现）

```kotlin
interface SearchLauncher {
    fun openMapSearch(query: String): LaunchResult
    fun openBrowserSearch(query: String): LaunchResult
    fun copyQuery(query: String): LaunchResult
}
// LaunchResult(success: Boolean, message: String)  // message 为面向用户的友好提示
```

- 实现用 `@ApplicationContext` 注入，**不持有 Activity**；
- 所有 Intent 使用 `FLAG_ACTIVITY_NEW_TASK`（applicationContext 启动所需）；
- 所有异常统一转为 `LaunchResult` 友好文案，不抛技术栈，应用不崩溃；
- 通过 `@Binds` 绑定到 `RepositoryModule`，由 Hilt 注入 `DecisionViewModel`。

---

## 3. Intent 处理与回退逻辑

### 地图搜索（`openMapSearch`）
- `Intent(ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))`；
- **不指定高德/百度/Google Maps 包名**，用 `resolveActivity` 让系统自行选择可用地图应用；
- 不申请定位权限、不读取 GPS、不引入地图 SDK；
- 中文关键词经 `Uri.encode` 正确编码；
- 无地图应用或 `ActivityNotFoundException` → **回退浏览器搜索**；
- 回退成功提示「没有可用的地图应用，已尝试浏览器搜索」；回退也失败提示「没有可用的地图应用，浏览器也不可用，你可以复制关键词」。

### 浏览器搜索（`openBrowserSearch`）
- `Intent(ACTION_VIEW, Uri.parse("https://www.baidu.com/s?wd=" + Uri.encode(query)))`；
- 不指定具体浏览器，`resolveActivity` 解析；
- 中文参数 URL Encode；浏览器不存在 → 友好提示「没有可用的浏览器，你可以复制关键词」，不闪退。

### 复制关键词（`copyQuery`）
- 写入系统 `ClipboardManager`（`ClipData.newPlainText`）；
- 不离开 What2Eat；ViewModel 返回「已复制：$query」交由 Snackbar 展示。

### 异常处理覆盖
`ActivityNotFoundException`、`SecurityException`、无可处理 Intent（`resolveActivity == null`）、空关键词——全部转为友好提示，用户看不到技术堆栈。

---

## 4. 完成页交互与面板

- 完成页「去找餐厅」按钮：`viewModel.showSearchPanel()` 打开底部面板（`ModalBottomSheet`）；
- 面板内容：标题「去找餐厅」、搜索关键词卡片、[地图搜索] [浏览器搜索] [复制关键词]；
- 面板关闭（下滑/点击外部）调用 `hideSearchPanel()`，**不影响已完成决策**；
- 搜索反馈通过 `Snackbar` 展示（`searchMessage` 状态流，UI `LaunchedEffect` 消费），如「已复制：潮汕牛肉火锅」。

### DecisionSession 规则
所有搜索动作（打开面板、地图、浏览器、复制、关闭）只改 UI 状态，**绝不触碰会话状态**，`COMPLETED` 保持不变。满足第九节要求。

---

## 5. 修改文件清单

### 新增（5）
- `app\src\main\java\com\what2eat\domain\search\SearchQueryBuilder.kt`
- `app\src\main\java\com\what2eat\domain\search\SearchLauncher.kt`
- `app\src\main\java\com\what2eat\data\search\AndroidSearchLauncher.kt`
- `app\src\test\java\com\what2eat\domain\search\SearchQueryBuilderTest.kt`

### 修改（4）
- `app\src\main\java\com\what2eat\data\di\RepositoryModule.kt`（绑定 SearchLauncher）
- `app\src\main\java\com\what2eat\feature\decision\DecisionUiState.kt`（showSearchPanel / searchMessage）
- `app\src\main\java\com\what2eat\feature\decision\DecisionViewModel.kt`（注入 SearchLauncher + 搜索方法）
- `app\src\main\java\com\what2eat\feature\decision\DecisionFlowScreen.kt`（找餐厅按钮 + 搜索面板 + Snackbar）

说明：`git status` 中出现的数据库/迁移/RepositoryStage2.2 相关 M 文件属于上一阶段未提交工作区变更，本阶段未改动。

---

## 6. 单元测试结果

`:app:testDebugUnitTest` → **BUILD SUCCESSFUL**，**64 tests, 0 failed**。

新增 `SearchQueryBuilderTest`（9 项）：
1. 仅 categoryName → 返回分类名
2. categoryName + 空 areaText → 分类名
3. categoryName + 空白 areaText（视为空）→ 分类名
4. categoryName + 有值 areaText → 「佛山南海 潮汕牛肉火锅」
5. 首尾空格自动 trim
6. 空 categoryName → invalid
7. 空白 categoryName → invalid
8. null categoryName → invalid
9. areaText trim 后正确拼接

修复既有概率性测试缺陷：`DecisionEngineTest.moodMatch_addsBonusAndReason` 原断言「推荐项含 MATCH_MOOD 原因」，受加权随机影响可能选中非匹配项而偶发失败；改为断言 surviving 中匹配项含该原因（更符合测试意图，不涉及引擎逻辑改动）。

---

## 7. assembleDebug 结果

```text
:app:assembleDebug → BUILD SUCCESSFUL in 31s
```

---

## 8. Debug APK 路径

```
What2Eat\app\build\outputs\apk\debug\app-debug.apk
```
文件大小：17,530,xxx 字节（约 17.6 MB），debug 签名，Android API 26+。

---

## 9. 验收视频（建议录制）

完成一次决策 → 就吃这个 → 决定好了 → 去找餐厅 → 地图搜索 → 返回 What2Eat → 再次打开搜索面板 → 浏览器搜索 → 返回 What2Eat → 再次打开搜索面板 → 复制关键词 → 确认 Snackbar「已复制：潮汕牛肉火锅」→ 返回首页 → 打开历史 → 确认刚才决策仍然存在。

另需验证：关闭面板不影响结果；中文无乱码；浅色/深色模式正常；无闪退。

---

## 10. 已知问题

- 「历史页 [再次搜索]」按任务第十节为 P1 可选，本阶段未实现（避免扩大改动范围，不影响 Stage 3.1 验收）。
- 浏览器搜索使用百度通用搜索 URL（`baidu.com/s?wd=`），未绑定具体浏览器；如需更换可在 `AndroidSearchLauncher.WEB_SEARCH_URL` 调整。
- 本阶段 areaText 恒为空，仅以分类名搜索；区域信息待后续阶段接入。
- 复制关键词使用系统剪贴板，跨应用分享（Share Intent）属禁入范围，未实现。

---

完成后已停止开发，未进入 Stage 3.2。等待 Stage 3.1 验收。