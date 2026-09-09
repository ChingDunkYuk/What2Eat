# What2Eat
<img width="400" height="800" alt="image" src="https://github.com/user-attachments/assets/b9645183-2109-4811-92d5-4863951ab6e8" />
<img width="400" height="800" alt="image" src="https://github.com/user-attachments/assets/97a10d33-ab5d-4b2e-aa4f-5f6d7eb527a3" />


> 一款帮助情侣共同决定“今晚吃什么”的 Android 应用。  
> An Android app that helps couples decide what to eat together.

---

## 简介 / Overview

**What2Eat** 是一款面向情侣、伴侣以及有选择困难用户的本地优先型吃饭决策应用。

它不是另一个餐厅点评平台，也不尝试维护庞大的商户数据库。

What2Eat 更关注一个更简单、但每天都很真实的问题：

> 今晚到底吃什么？

应用通过双方偏好、当前状态、预算、用餐方式、历史记录和自建吃饭池，帮助用户快速形成一个可执行的共同决定。

**What2Eat** is a local-first meal decision app designed for couples, partners, and anyone who struggles with the daily question:

> What should we eat tonight?

Instead of becoming another restaurant review platform, What2Eat focuses on helping users reach a decision first, then hands the result off to maps, browsers, or food platforms for execution.

---

## 为什么做这个项目 / Why This Project

很多情侣都会经历类似的对话：

> “今晚吃什么？”  
> “都可以。”  
> “那吃火锅？”  
> “今天不太想。”  
> “那你想吃什么？”  
> “不知道。”

问题通常不是“附近没有餐厅”，而是：

- 双方没有明确目标
- 一直互相否定选项
- 最近吃过什么想不起来
- 收藏了很多店，但真正决定时找不到
- 总是在同几家店之间循环

What2Eat 希望把这个过程从：

`反复讨论`

变成：

`表达偏好 → 找共同候选 → 给出推荐 → 去找餐厅`

Many couples face the same issue:

- neither person knows what they want
- suggestions get rejected repeatedly
- saved restaurants are scattered across different apps
- the same few choices keep repeating
- deciding takes longer than actually eating

What2Eat turns the process into:

`preferences → shared candidates → recommendation → restaurant search`

---

## 核心模式 / Core Modes

### 1. 先决定吃什么 / Decide What to Eat First

适合：

- 不知道今天想吃什么
- 想尝试新的餐厅
- 两个人意见不统一
- 还没有明确目标

流程：

`选择条件 → 双方分别选择 → 生成共同候选 → 最终推荐 → 外部平台搜索`

Typical flow:

`conditions → individual preferences → shared candidates → final recommendation → external search`

---

### 2. 从我的吃饭池决定 / Decide from My Food Pool

用户可以建立自己的“吃饭池”，保存：

- 常吃
- 吃过
- 待尝试
- 外卖
- 在家做
- 踩雷

后续可以直接从自己的长期数据中做决策。

Users can build a personal food pool containing:

- Frequent
- Visited
- Want to Try
- Takeout
- Home Cooking
- Avoided

This turns What2Eat into a decision tool that becomes more useful over time.

---

## 当前功能 / Current Features

### 人物与偏好 / Profiles & Preferences

- 单人模式
- 双人模式
- 主用户与另一半独立档案
- 餐饮分类长期偏好
- 具体吃饭选项独立偏好
- 长期不吃 / Hard Exclusion

### 决策流程 / Decision Flow

- 用餐方式
- 今日状态
- 预算
- 距离
- 双方分别选择
- 双人隐私交接
- 共同候选生成
- 加权推荐
- 换一个
- 候选耗尽处理

### 推荐引擎 / Recommendation Engine

推荐逻辑综合考虑：

- 本次想吃 / 可以接受
- 长期偏好
- 当前状态
- 用餐方式
- 预算
- 历史防重复
- 双人公平权重
- 加权随机

The recommendation engine considers:

- current WANT / ACCEPT selections
- long-term preferences
- current mood
- meal mode
- budget
- recent history
- couple fairness
- weighted randomness

### 外部搜索 / Search Handoff

最终决定后可以：

- 大众点评
- 美团
- 地图搜索
- 浏览器搜索
- 复制关键词

What2Eat does not depend on any single third-party platform.

### 吃饭池 / Food Pool

支持管理：

- 餐厅
- 外卖商家
- 在家做
- 餐饮类型

支持：

- 多列表归属
- 标签
- 区域
- 预计时间
- 备注
- 原始链接
- 停用
- 删除
- 具体人物偏好

---

## 当前开发进度 / Development Status

| Stage | 内容 / Feature | 状态 |
|---|---|---|
| Stage 0.1 | 项目骨架 / Project Foundation | ✅ |
| Stage 1.1 | 双人物档案与长期偏好 / Profiles & Preferences | ✅ |
| Stage 2.1 | 本次决策输入 / Decision Input | ✅ |
| Stage 2.2 | 推荐引擎 / Recommendation Engine | ✅ |
| Stage 3.1 | 通用搜索承接 / Generic Search Handoff | ✅ |
| Stage 3.2 | 大众点评 / 美团承接 | ✅ |
| Stage 4 | 我的吃饭池 / Food Pool | ✅ |
| Stage 5 | Android Share Intent 导入 | 🚧 |
| Stage 6 | 从吃饭池决定 / Food Pool Recommendation | ⏳ |
| Stage 7 | 历史、反馈与备份 / History & Backup | ⏳ |
| Stage 8 | Release Candidate / v1.0 | ⏳ |

---

## 技术栈 / Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **Material 3**
- **Navigation Compose**
- **Room**
- **DataStore**
- **ViewModel**
- **StateFlow**
- **Kotlin Coroutines**
- **Kotlin Serialization**

Architecture:

- UI Layer
- Domain Layer
- Data Layer

The recommendation engine is designed as pure Kotlin logic and does not directly depend on Compose or Room.

---

## 项目原则 / Design Principles

### Local First

核心数据默认保存在本地。

Core user data is stored locally by default.

### Decision First

What2Eat 优先解决“做决定”，而不是展示海量餐厅。

What2Eat focuses on decision-making rather than restaurant discovery.

### User Data First

用户自己保存的：

- 常吃店
- 待尝试
- 踩雷
- 在家做
- 历史记录

比第三方平台数据更重要。

Your own food history and saved places are treated as the most valuable data source.

### No Hard Dependency on External Platforms

大众点评、美团、地图等只用于结果承接。

If one platform becomes unavailable, What2Eat should still work.

---

## 项目结构 / Project Structure

```text
com.what2eat.app
├── app
├── core
│   ├── database
│   ├── datastore
│   ├── model
│   ├── navigation
│   ├── designsystem
│   └── util
├── data
│   ├── local
│   ├── backup
│   ├── parser
│   └── repository
├── domain
│   ├── decision
│   ├── filter
│   ├── ranking
│   ├── share
│   └── repository
└── feature
    ├── onboarding
    ├── home
    ├── decision
    ├── foodpool
    ├── history
    ├── search
    ├── shareimport
    └── settings
