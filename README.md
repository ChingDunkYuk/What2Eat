# What2Eat

<img width="400" height="800" alt="image" src="https://github.com/user-attachments/assets/b9645183-2109-4811-92d5-4863951ab6e8" />
<img width="400" height="800" alt="image" src="https://github.com/user-attachments/assets/97a10d33-ab5d-4b2e-aa4f-5f6d7eb527a3" />

> 一款帮助情侣共同决定「今晚吃什么」的 Android 应用。本地优先、开源、无账号。
> An Android app that helps couples decide what to eat together. Local-first, open source, no accounts.

[![Release](https://img.shields.io/github/v/release/ChingDunkYuk/What2Eat)](https://github.com/ChingDunkYuk/What2Eat/releases)
[![Tests](https://img.shields.io/badge/JVM%20tests-271%20green)](CHANGELOG.md)
[![minSdk](https://img.shields.io/badge/minSdk-26-blue)](app/build.gradle.kts)

---

## 简介 / Overview

**What2Eat** 是一款面向情侣、伴侣以及有选择困难用户的本地优先型吃饭决策应用。

它不是另一个餐厅点评平台，也不维护庞大的商户数据库。它只关注一个更简单、但每天都很真实的问题：

> 今晚到底吃什么？

应用通过双方偏好、当前状态、预算、用餐方式、历史记录和自建吃饭池，快速形成一个可执行的共同决定；决定之后再把结果交给大众点评 / 美团 / 地图 / 浏览器去执行。

Instead of becoming another restaurant review platform, What2Eat helps you **decide first**, then hands off to maps, browsers, or food platforms for execution.

---

## 为什么做这个项目 / Why

> 「今晚吃什么？」
> 「都可以。」
> 「那吃火锅？」
> 「今天不太想。」
> 「那你想吃什么？」
> 「不知道。」

问题通常不是「附近没有餐厅」，而是：双方没有明确目标、一直互相否定选项、收藏了很多店但真正决定时找不到、总在同几家店之间循环。

What2Eat 把这个过程从 `反复讨论` 变成：

`表达偏好 → 找共同候选 → 给出推荐 → 去找餐厅`

---

## 功能一览 / Features

### 双决策模式
- **先决定吃什么**：本次条件（人物/用餐方式/状态/预算/距离）→ 双方分别选择（想吃/可以/不吃/都可以）→ 共同候选 → 最终推荐 → 外部平台搜索
- **从吃饭池决定**：直接基于长期积累的个人数据决策，越用越懂你
- 双人隐私交接（各自独立选择，互不干扰）、换一个、候选耗尽处理、加权随机 + 双人公平权重

### 吃饭池（你的长期数据资产）
- 六大列表：常吃 / 吃过 / 待尝试 / 外卖 / 在家做 / 踩雷
- 标签（含联想 chips 与管理面板：重命名/合并/删除）、区域、预计用时、备注、原始链接
- **待整理收件箱**：分享进来的店先收着，支持**长按多选批量确认入库/批量删除**
- 具体人物独立偏好、长期不吃（硬排除）、停用/删除（历史引用保护）

### 分享导入与店名识别（本项目最硬核的部分）
从美团 / 大众点评 App 直接分享到 What2Eat，自动解析店铺链接并**抓取真实店名**：

- dpurl.cn 短链解析 → HTTP 重定向链探测 → 唤起页 poiId 提取 → 多候选 URL 派生
- **主文档代理**：WebView 永不自己请求主文档，302→`imeituan://` 唤起链在 Java 侧掐断（**绝不会把你拽回美团 App**）
- JS 数据劫持：拦截美团数据接口 JSON 提取店名；多重护栏防止营销页/登录页/验证码标题被当店名
- **验证墙人工通过**：撞风控墙时弹出全屏验证页，滑一次块 → 设备进入信任期 → 之后分享直接出店名
- 一切失败安全兜底：拿不到店名就落「待整理」手动填，不阻塞收纳

### 历史与统计
- 统计卡（总决定/本月/平均换一个/最常吃 Top5）+ 按日分组时间线
- **五维筛选**：时间（近 7 天/本月）/ 人物 / 模式 / 所属列表 / 标签
- **月度吃饭报告**：本月次数、最常吃、换一率、上月环比
- 再次搜索：任意历史记录一键回平台找店

### 工程基建
- 数据备份/恢复（SAF 导出导入，全 11 表 JSON，事务原子替换）
- 弹跳微交互体系（按压回弹/交错入场/换一个重弹）
- **崩溃捕获**：闪退后重开自动弹堆栈一键复制（无 adb 排障）
- 抓取诊断面板：分享确认页内实时日志一键复制

---

## 下载 / Download

[**Releases 页面**](https://github.com/ChingDunkYuk/What2Eat/releases) 下载最新 APK，覆盖安装即可（签名一致，数据保留）。要求 Android 8.0（API 26）及以上。

完整版本历史见 [CHANGELOG.md](CHANGELOG.md)。

---

## 构建 / Build

```bash
# Debug（无需任何配置）
./gradlew assembleDebug

# 单元测试（271 项 JVM 测试）
./gradlew testDebugUnitTest

# Release（需要自备签名密钥）
./gradlew assembleRelease
```

Release 构建需自行准备签名：在 `app/keystore.properties` 填入你自己的 `storeFile/storePassword/keyAlias/keyPassword`
（该文件与 `app/keystore/` 均被 .gitignore 排除，不会入库；仓库内不含任何真实密钥）。

## 技术栈 / Tech Stack

- **Kotlin** + **Jetpack Compose**（Material 3）+ **Navigation Compose**
- **Room**（11 表，DB v7）+ **DataStore** + **Hilt**（DI）
- **StateFlow / Coroutines**（viewModelScope 驱动）
- **org.json**（备份序列化；刻意避开反射型序列化库以保 R8 兼容）
- **WebView + HttpURLConnection**（分享店名抓取链：主文档代理/双层拦截/JS 数据劫持）
- 纯 Kotlin 领域层（决策引擎 / 分享解析 / 历史统计 / 筛选判定均可 JVM 单测）

架构：`feature（UI）→ domain（纯 Kotlin 逻辑）→ data（Room/系统服务）`，领域逻辑不依赖 Compose 与 Room。

---

## 开发进度 / Status

| 版本 | 内容 | 状态 |
|---|---|---|
| Stage 0 ~ 5 | 项目骨架 / 人物偏好 / 决策流 / 推荐引擎 / 吃饭池 | ✅ |
| v0.7.x ~ 0.8.x | 分享导入 / 吃饭池决策 / 历史页升级 / 抓取防唤起 | ✅ |
| v0.9.x | 体验补强 / 性能 / 标签管理 / 备份恢复 / UI 动效 | ✅ |
| v1.0.0 | 首个签名 Release（R8，12.2MB→1.86MB） | ✅ |
| v1.1.x | 美团店名抓取强化（JS 数据劫持 / 代理修复 / 护栏） | ✅ |
| v1.2.x | **验证墙人工通过**（滑块信任期）+ 登录态修正 + GitHub 上线 | ✅ |
| v1.3.x | 批量整理 / 标签联想 / 历史三维筛选 / 月报 / 崩溃捕获 / 闪退修复 | ✅ |
| v1.4.0 | 历史筛选 +列表/标签维度（当前最新） | ✅ |

---

## 项目原则 / Principles

- **Local First**：核心数据只存本地，无账号无云同步，备份靠手动导出
- **Decision First**：优先解决「做决定」，而不是展示海量餐厅
- **User Data First**：自己存的店和历史，比任何平台数据都重要
- **No Hard Dependency**：点评/美团/地图只用于结果承接，任何一家不可用 App 都照常工作
- **唤起防护优先于功能**：宁可店名抓不到（手动填），绝不允许把用户拽去第三方 App

---

## 项目结构 / Project Structure

```text
com.what2eat
├── core
│   ├── database          # Room 11 表 / DAO / 迁移
│   ├── datastore         # 使用模式等偏好
│   ├── designsystem      # 主题 / 图标 / 动效体系
│   └── navigation        # NavHost
├── data
│   ├── repository        # Repository 实现（Room 事务）
│   ├── search            # 平台搜索承接
│   └── share             # HTTP/WebView 店名抓取链（主文档代理/JS 劫持/诊断日志）
├── domain                # 纯 Kotlin：决策引擎 / 分享解析 / 历史统计 / 筛选判定
│   ├── engine
│   ├── foodpool
│   ├── history
│   ├── share
│   └── repository        # 接口
└── feature               # Compose UI：home / decision / foodpool / history /
                          #   pooldecision / shareimport / settings / onboarding
```

---

## 隐私 / Privacy

所有数据保存在设备本地（Room + DataStore），应用无后端、无账号、无埋点。
分享导入功能仅抓取美团/点评的**公开网页**获取店名，不触碰任何账号数据；
美团登录为可选实验功能，仅将 Cookie 存于本机 WebView。

---

如果它解决了你的「今晚吃什么」，欢迎 Star；问题与建议请提 [Issues](https://github.com/ChingDunkYuk/What2Eat/issues)。
