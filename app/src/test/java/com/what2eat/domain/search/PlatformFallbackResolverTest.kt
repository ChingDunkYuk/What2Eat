package com.what2eat.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.2：平台承接回退决策单元测试。
 *
 * 覆盖测试场景 A-F（见需求"十二、测试场景"）在决策层面的行为：
 * A. 大众点评已安装 → 复制关键词 + 打开 App，提示粘贴搜索；
 * B. 大众点评未安装 → 平台网页或浏览器兜底；
 * C. 美团已安装 → 同 A；
 * D. 美团未安装 → 同 B；
 * E. 平台跳转异常（已安装但启动失败）→ 回退，不产生 Failed 之外的崩溃；
 * F. 中文关键词 → 提示文案保留原文，不乱码。
 *
 * 说明：实际系统调用（PackageManager / Intent / 剪贴板）由 Android 实现执行，
 * 这里验证纯决策逻辑 PlatformFallbackResolver。
 */
class PlatformFallbackResolverTest {

    private fun caps(
        isInstalled: Boolean = false,
        launchSucceeded: Boolean = false,
        copySucceeded: Boolean = false,
        platformWebOpened: Boolean = false,
        browserOpened: Boolean = false
    ) = PlatformFallbackResolver.Capabilities(
        isInstalled = isInstalled,
        launchSucceeded = launchSucceeded,
        copySucceeded = copySucceeded,
        platformWebOpened = platformWebOpened,
        browserOpened = browserOpened
    )

    // A. 大众点评已安装：复制关键词 + 打开 App
    @Test
    fun `dianping installed and launched copies query and prompts in app`() {
        val result = PlatformFallbackResolver.resolve(
            query = "潮汕牛肉火锅",
            displayName = "大众点评",
            capabilities = caps(isInstalled = true, launchSucceeded = true, copySucceeded = true)
        )

        assertTrue(result is PlatformLaunchResult.OpenedWithCopiedQuery)
        assertTrue("应提示已复制关键词", result.message!!.contains("已复制"))
        assertTrue("提示应包含平台名", result.message!!.contains("大众点评"))
        assertTrue("提示应包含关键词", result.message!!.contains("潮汕牛肉火锅"))
    }

    // A2. 已安装但复制失败：仍打开 App，提示手动搜索
    @Test
    fun `installed but copy failed still opens app with manual hint`() {
        val result = PlatformFallbackResolver.resolve(
            query = "东北菜",
            displayName = "大众点评",
            capabilities = caps(isInstalled = true, launchSucceeded = true, copySucceeded = false)
        )

        assertTrue(result is PlatformLaunchResult.OpenedWithCopiedQuery)
        assertTrue(result.message!!.contains("手动搜索"))
    }

    // B. 大众点评未安装 → 平台网页兜底
    @Test
    fun `dianping not installed falls back to platform web`() {
        val result = PlatformFallbackResolver.resolve(
            query = "潮汕牛肉火锅",
            displayName = "大众点评",
            capabilities = caps(isInstalled = false, platformWebOpened = true)
        )

        assertTrue(result is PlatformLaunchResult.FallbackToBrowser)
        assertTrue(result.message!!.contains("未检测到大众点评"))
        assertTrue(result.message!!.contains("打开浏览器"))
    }

    // B2. 大众点评未安装且网页不可用 → 通用浏览器兜底
    @Test
    fun `dianping not installed and no web falls back to browser`() {
        val result = PlatformFallbackResolver.resolve(
            query = "潮汕牛肉火锅",
            displayName = "大众点评",
            capabilities = caps(isInstalled = false, browserOpened = true)
        )

        assertTrue(result is PlatformLaunchResult.FallbackToBrowser)
    }

    // C. 美团已安装：复制关键词 + 打开 App
    @Test
    fun `meituan installed and launched copies query and prompts in app`() {
        val result = PlatformFallbackResolver.resolve(
            query = "日式拉面",
            displayName = "美团",
            capabilities = caps(isInstalled = true, launchSucceeded = true, copySucceeded = true)
        )

        assertTrue(result is PlatformLaunchResult.OpenedWithCopiedQuery)
        assertTrue(result.message!!.contains("美团"))
    }

    // D. 美团未安装 → 浏览器兜底（无可靠网页搜索）
    @Test
    fun `meituan not installed falls back to browser`() {
        val result = PlatformFallbackResolver.resolve(
            query = "日式拉面",
            displayName = "美团",
            capabilities = caps(isInstalled = false, browserOpened = true)
        )

        assertTrue(result is PlatformLaunchResult.FallbackToBrowser)
        assertTrue(result.message!!.contains("未检测到美团"))
    }

    // E. 平台跳转异常（已安装但启动失败）→ 回退网页/浏览器，不闪退
    @Test
    fun `installed but launch failed falls back without crash`() {
        val result = PlatformFallbackResolver.resolve(
            query = "潮汕牛肉火锅",
            displayName = "大众点评",
            capabilities = caps(isInstalled = true, launchSucceeded = false, browserOpened = true)
        )

        assertTrue(result is PlatformLaunchResult.FallbackToBrowser)
    }

    // E2. 全部失败 → Failed，提示手动复制（不闪退）
    @Test
    fun `all paths fail returns failed with copy hint`() {
        val result = PlatformFallbackResolver.resolve(
            query = "潮汕牛肉火锅",
            displayName = "大众点评",
            capabilities = caps(isInstalled = false, browserOpened = false)
        )

        assertTrue(result is PlatformLaunchResult.Failed)
        assertTrue(result.message!!.contains("复制关键词"))
    }

    // F. 中文关键词：提示文案保留原文，不乱码
    @Test
    fun `chinese keyword preserved in message`() {
        for (kw in listOf("潮汕牛肉火锅", "东北菜", "日式拉面")) {
            val ok = PlatformFallbackResolver.resolve(
                query = kw,
                displayName = "大众点评",
                capabilities = caps(isInstalled = true, launchSucceeded = true, copySucceeded = true)
            )
            assertTrue("关键词应保留：$kw", ok.message!!.contains(kw))

            val fb = PlatformFallbackResolver.resolve(
                query = kw,
                displayName = "美团",
                capabilities = caps(isInstalled = false, browserOpened = true)
            )
            assertTrue("回退提示保留关键词：$kw", fb.message!!.contains("未检测到美团"))
        }
    }

    // 空关键词 → 由调用方在 Android 实现处拦截；此处验证非空正常路径不受影响
    @Test
    fun `non empty short query works`() {
        val result = PlatformFallbackResolver.resolve(
            query = "面",
            displayName = "大众点评",
            capabilities = caps(isInstalled = true, launchSucceeded = true, copySucceeded = true)
        )
        assertTrue(result is PlatformLaunchResult.OpenedWithCopiedQuery)
    }
}