# 更新日志（CHANGELOG）

What2Eat 的版本记录。格式：版本号（versionCode）｜日期｜要点。
各版本详细设计见开发计划书（`.trae/documents/What2Eat-vX.Y.Z-*.md`，不随仓库分发）。

---

## v1.5.0（54）｜2026-09-09

### 优化
- **历史页取数重构**（v0.9.1 批量化之上的第二层）：v1.3.0 起筛选流与数据流合并，
  每点一次筛选 chip 都会重跑 5 次全量查询；现拆两层——数据层各自
  `map + distinctUntilChanged`（仅数据变化才查库/重建查找表），组装层
  `combine(dataFlow, filterFlow)` 纯内存计算，**筛选切换零数据库查询**；
  participants 挂 sessions 流刷新（参与者随会话写入、会话表必同步变化）

### 杂项清理
- SettingsScreen `LocalLifecycleOwner` 过时告警迁移（androidx.lifecycle.compose）
- ShareImportScreen 重复 import 清除；proguard-rules 注释与 v1.1.0 现实对齐

## v1.4.0（53）｜2026-09-09

### 新增
- **历史页筛选维度扩展**：在 v1.3.0 时间/人物/模式三维基础上，新增「所属列表」
  （常吃/吃过/待尝试…）与「标签」两个筛选维度；只展示历史中真实出现过的维度值；
  分类决策无列表/标签概念，选中维度后自然只看池决策
- 维度数据经 `observeAllCollections` / `observeAllTags` 批量取齐，沿用纯内存过滤架构，
  零新增 DAO 查询；HistoryFilter 领域判定同步扩展（默认参数兼容既有调用）

### 测试
- HistoryFilterTest 扩展 4 用例（列表/标签维度 null 通过/命中/未命中/空集拒绝/组合）
- 全套 271 项 JVM 测试通过

## v1.3.2（52）｜2026-09-09

### 修复
- **历史页闪退（release 真机报障）**：`filterFlow` 声明在 `init { observeHistory() }` 之后，
  Kotlin 按声明顺序初始化属性，`viewModelScope`（Main.immediate）协程在构造期即执行 combine，
  收集到未初始化的 null 流 → NPE。修复：声明移至 init 之前，附注释防再犯。

### 基础设施（v1.3.1）
- 崩溃捕获：Application 全局未捕获异常写 `filesDir/last_crash.txt` 后交还原处理器正常闪退，
  MainActivity 下次启动弹窗展示堆栈一键复制（无 adb 排障闭环，长期留用）。

## v1.3.0（50）｜2026-09-09

### 新增
- **待整理批量整理**：待整理 Tab 长按进多选态，批量确认入库 / 批量删除
  （被历史引用项自动跳过并 Toast 汇总）；DAO 新增 `updateImportStatus` / `deleteByIds`
- **编辑页标签联想 chips**：复用 `observeTagUsage()`（按使用数降序取前 12），
  已选不再出现，点选即填入
- **历史页三维筛选**：时间（近 7 天/本月）/ 人物 / 模式（分类决策/池决策），纯内存过滤；
  统计卡与月报保持全量口径
- **月度吃饭报告**：本月次数 / 最常吃 Top5 / 换一率 / 上月环比，BottomSheet 展示
- **DecisionFlowScreen 动效铺开**（v0.9.4 悬挂）：卡片按压回弹、候选交错入场、
  六个主按钮压弹、换一个后推荐理由重弹

### 测试
- 新增 HistoryFilterTest（6）/ MonthlyReportCalculatorTest（5）/ SavedOptionBatchOpsTest（3）
- 全套 267 项 JVM 测试通过

## v1.2.5（49）｜2026-09-09

### 修复
- 登录态自查：`isLoggedIn` 剔除 uuid/_hc.v（匿名设备 ID 误报「已登录」），
  改查 passport 域真实凭证；登录入口文案诚实化（美团子域 SSO 不互通，对店名识别帮助有限）

### 发布
- v1.2.5-release.apk（1.86MB，R8 + 签名）；项目上传 GitHub（main 分支，全历史密钥零泄漏验证）

## v1.2.0 ~ v1.2.4（44~48）｜2026-09-08

### 新增（核心突破：验证墙人工通过）
- 美团店名识别主路径闭环：撞 yoda 风控墙 → 弹人工验证（VerifyPassActivity 全屏、默认 UA）
  → 用户滑块通过 → 落地店铺页直接带回店名 / 自动重试抓取；
  通过态由服务端记录，后续一段时间分享直接出店名
- 验证页环境三连修：全屏 Activity（滑块显示不全）→ 去 XWEB UA 声称
  （反自动化故意渲染残缺的根因）→ 代理 302 出新鲜 challenge
  （一次性 requestCode 被无头链消费报「请求异常，拒绝操作」的根因）

## v1.1.1 ~ v1.1.3（41~43）｜2026-09-08

### 修复
- 主文档代理线程崩溃（IO 线程调 WebView settings）修复，UA 主线程预取
- 30x 改喂 meta-refresh 壳页（验证页拿到真实 location/requestCode）
- 护栏加固：登录页/「温馨提示」标题不再被当店名；验证/登录死路快速失败（单候选省约 20s）
- 候选重排：m.dianping.com 提为首选（实测唯一无登录态直出店名标题的候选）
- XHR 劫持回传响应状态码与错误体片段（诊断面板可见）

## v1.1.0（40）｜2026-09-08

### 新增
- JS 数据劫持：XHR/fetch 拦截美团数据接口 JSON 提取店名（绕开 SPA 壳页）
- 可选美团登录（设置页，CookieManager 穿透抓取链）+ H5guard 签名重试
- 抓取诊断面板：分享确认页内实时日志 + 一键复制（无 adb 排障）

## v1.0.0（39）｜2026-09-08

### 发布
- Release 正式版：自建 keystore 签名 + R8 混淆 + 资源收缩（12.2MB → 1.86MB）
- 批次内含 v0.9.x：历史页统计卡与按日分组、N+1 查询批量化、标签管理、
  数据备份/恢复（SAF + org.json）、UI 弹跳微交互体系

---

更早（Stage 0 ~ v0.9.x）为开发期版本，从仓库 git 历史可追溯。
