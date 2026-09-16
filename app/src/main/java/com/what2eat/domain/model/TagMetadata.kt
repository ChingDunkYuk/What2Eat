package com.what2eat.domain.model

/**
 * 标签元数据（v1.6.0 标签颜色）。
 *
 * 懒元数据策略：标签本体仍是字符串（saved_option_tag.tagId），
 * 本模型只在用户显式设置过颜色时才有对应行；查找无行 → 回落默认色
 * （[TagPalette.DEFAULT]），「无行」与「行为默认色」语义等价。
 */
data class TagMetadata(
    val name: String,
    val colorArgb: Int,
    val createdAt: Long = 0L
)

/**
 * 标签预设色板（v1.6.0）。
 *
 * 延续「奶油橘 + 米白」温馨基调的 8 预设色（避开粉色系）；
 * 深浅主题共用同一 ARGB，UI 层以淡着色 alpha 保证可读性。
 * 纯 Kotlin 常量，JVM 可测（无重复、不含默认色）。
 */
object TagPalette {

    /** 默认色（中性燕麦灰，与主题 outline 同族）：未设置/已重置的标签 */
    const val DEFAULT: Int = 0xFF7A685A.toInt()

    /** 奶油橘（主题主色同族） */
    const val ORANGE: Int = 0xFFA84B17.toInt()

    /** 杏黄 */
    const val APRICOT: Int = 0xFFB07A1E.toInt()

    /** 抹茶绿（主题 tertiary 鼠尾草绿同族） */
    const val MATCHA: Int = 0xFF586720.toInt()

    /** 青碧 */
    const val TEAL: Int = 0xFF1F6E63.toInt()

    /** 天青蓝 */
    const val AZURE: Int = 0xFF2D5F8A.toInt()

    /** 芋紫 */
    const val TARO: Int = 0xFF6B4E8E.toInt()

    /** 暖棕（主题 secondary 同族） */
    const val COCOA: Int = 0xFF77563C.toInt()

    /** 砖红 */
    const val BRICK: Int = 0xFF9E3B2E.toInt()

    /** 预设色板（不含默认色；管理面板色板对话框展示序） */
    val presets: List<Int> = listOf(ORANGE, APRICOT, MATCHA, TEAL, AZURE, TARO, COCOA, BRICK)
}
