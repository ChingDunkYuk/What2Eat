package com.what2eat.domain.share

/**
 * 链接标题抓取（可注入，便于单元测试替换实现）。
 *
 * 用于分享文本解析不出店名时（美团到店分享常只有地址/电话/短链），
 * 从链接指向的网页 <title> 补店名。抓不到则静默回退手动填写。
 */
interface LinkTitleFetcher {

    /**
     * 抓取网页标题并净化为店名候选。
     * 失败/超时/非 HTML/标题无效 → null（调用方不阻塞、不报错）。
     */
    suspend fun fetchTitle(url: String): String?
}
