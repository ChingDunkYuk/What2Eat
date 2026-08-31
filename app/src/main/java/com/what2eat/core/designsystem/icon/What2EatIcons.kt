package com.what2eat.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * What2Eat 手绘可爱图标库。
 *
 * 风格总纲（与温馨 UI「奶油橘 + 米白」配套）：
 * - 24×24 viewport，四周留边 ≥ 2.5，小尺寸不被裁
 * - 主笔画 2.2f（胖于 Material Outlined），细节 1.4–1.6f，全部圆头圆角
 * - 食物/物品类图标带统一「卡哇伊小脸」（圆点眼 ×2 + 微笑弧）
 * - 半透明装饰用 alpha 表达（Icon 的 tint 只认 alpha，明暗主题自适应）
 */
object What2EatIcons {

    /** 底部导航-首页：圆弧顶小屋，屋顶小爱心炊烟，屋里住着笑脸 */
    val Home: ImageVector by lazy {
        cute("Home") {
            // 屋顶（大圆弧）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                arcCC(12f, 11.3f, 7.7f, 180f, 180f)
            }
            // 房身（三边圆头折线）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6.0f, 10.7f)
                lineTo(6.0f, 19.5f)
                lineTo(18.0f, 19.5f)
                lineTo(18.0f, 10.7f)
            }
            // 爱心炊烟
            path(fill = SolidColor(Color.Black)) {
                heart(15.4f, 3.4f, 0.85f)
            }
            kawaiiFace(cx = 12.0f, cy = 14.6f)
        }
    }

    /** 吃饭池/餐厅：胖勺子 + 胖叉子微外八，柄间一颗小爱心 */
    val Restaurant: ImageVector by lazy {
        cute("Restaurant") {
            // 勺子头（蛋形）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(8.2f, 4.6f)
                quadTo(5.7f, 4.8f, 5.8f, 7.2f)
                quadTo(5.9f, 9.5f, 8.2f, 9.6f)
                quadTo(10.5f, 9.5f, 10.6f, 7.2f)
                quadTo(10.7f, 4.8f, 8.2f, 4.6f)
                close()
            }
            // 勺柄（微外八）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(8.2f, 9.9f)
                lineTo(7.0f, 19.7f)
            }
            // 叉齿 ×3
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(14.3f, 4.6f); lineTo(14.3f, 7.7f)
                moveTo(15.6f, 4.4f); lineTo(15.6f, 7.7f)
                moveTo(16.9f, 4.6f); lineTo(16.9f, 7.7f)
                // 叉底圆弧
                moveTo(14.3f, 7.5f)
                quadTo(15.6f, 9.4f, 16.9f, 7.5f)
            }
            // 叉柄（微外八）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(15.6f, 9.0f)
                lineTo(17.0f, 19.7f)
            }
            // 柄间小爱心
            path(fill = SolidColor(Color.Black)) {
                heart(12.3f, 4.9f, 1.0f)
            }
        }
    }

    /** 历史：圆滚滚时钟，脸即表盘，左侧逆时针回绕箭头 */
    val History: ImageVector by lazy {
        cute("History") {
            // 表盘
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12.5f, 12.0f, 7.3f)
            }
            // 回绕弧
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                arcCC(12.5f, 12.0f, 8.8f, 150f, 60f)
            }
            // 箭头小翅膀
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5.05f, 9.5f)
                lineTo(4.88f, 7.6f)
                lineTo(3.16f, 8.77f)
            }
            kawaiiFace(cx = 12.5f, cy = 12.1f)
        }
    }

    /** 设置：胖圆齿轮（8 个圆头齿），中心住着小脸 */
    val Settings: ImageVector by lazy {
        cute("Settings") {
            // 齿轮齿（8 根圆头短辐条）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45f).toDouble())
                    val c = cos(a).toFloat()
                    val s = sin(a).toFloat()
                    moveTo(12f + 5.5f * c, 12f + 5.5f * s)
                    lineTo(12f + 7.3f * c, 12f + 7.3f * s)
                }
            }
            // 中心圆
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 4.3f)
            }
            kawaiiFace(cx = 12.0f, cy = 11.9f, s = 0.8f)
        }
    }

    /** 问候：圆润小手套挥手，掌中小脸，右上运动线 + 小闪光 */
    val WavingHand: ImageVector by lazy {
        cute("WavingHand") {
            // 手套轮廓（三圆头指 + 圆掌）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(7.3f, 13.2f)
                curveTo(6.8f, 8.6f, 9.2f, 6.8f, 10.7f, 7.7f)
                curveTo(11.1f, 6.3f, 12.9f, 6.3f, 13.3f, 7.7f)
                curveTo(14.8f, 6.8f, 17.2f, 8.6f, 16.7f, 13.2f)
                curveTo(17.5f, 16.4f, 16.3f, 19.4f, 12.0f, 19.4f)
                curveTo(7.7f, 19.4f, 6.5f, 16.4f, 7.3f, 13.2f)
                close()
            }
            kawaiiFace(cx = 12.0f, cy = 13.6f, s = 0.95f)
            // 运动线
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(18.4f, 7.4f)
                quadTo(20.0f, 9.1f, 19.5f, 11.0f)
            }
            // 小闪光
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                star4(20.3f, 5.6f, 0.9f)
            }
        }
    }

    /** 先决定吃什么：2×2 圆角格，右上格圆形带小脸（被选中的分类） */
    val Category: ImageVector by lazy {
        cute("Category") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                roundedRect(4.0f, 4.0f, 10.4f, 10.4f, 1.9f)
                roundedRect(4.0f, 13.6f, 10.4f, 20.0f, 1.9f)
                roundedRect(13.6f, 13.6f, 20.0f, 20.0f, 1.9f)
                circlePath(16.8f, 7.2f, 3.2f)
            }
            kawaiiFace(cx = 16.8f, cy = 7.0f, s = 0.9f)
        }
    }

    /** 继续决定：实心圆角胖三角，镂空小脸（EvenOdd 挖孔透底色） */
    val PlayArrow: ImageVector by lazy {
        cute("PlayArrow") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // 胖三角轮廓（圆角）
                moveTo(6.4f, 6.2f)
                quadTo(6.4f, 4.2f, 8.3f, 5.3f)
                lineTo(17.8f, 10.9f)
                quadTo(19.4f, 12.0f, 17.8f, 13.1f)
                lineTo(8.3f, 18.7f)
                quadTo(6.4f, 19.8f, 6.4f, 17.8f)
                close()
                // 镂空眼
                circlePath(11.4f, 11.3f, 0.75f)
                circlePath(14.4f, 11.3f, 0.75f)
                // 镂空微笑（月牙孔）
                moveTo(11.7f, 13.5f)
                quadTo(12.9f, 15.2f, 14.1f, 13.5f)
                quadTo(12.9f, 14.4f, 11.7f, 13.5f)
                close()
            }
        }
    }

    /** 占位/稍后：圆滚滚闹钟，铃铛耳朵 + 小脚，表盘小脸 */
    val Schedule: ImageVector by lazy {
        cute("Schedule") {
            // 铃铛耳朵
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(6.2f, 7.4f)
                quadTo(5.9f, 4.3f, 8.8f, 4.0f)
                moveTo(17.8f, 7.4f)
                quadTo(18.1f, 4.3f, 15.2f, 4.0f)
            }
            // 表盘
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12.6f, 6.6f)
            }
            // 小脚
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(7.4f, 18.4f); lineTo(6.2f, 20.2f)
                moveTo(16.6f, 18.4f); lineTo(17.8f, 20.2f)
            }
            kawaiiFace(cx = 12.0f, cy = 12.3f)
        }
    }

    /** 吃饭池空态：摊开的圆角菜单书，左页小碗冒热气，右页菜单条目 */
    val RestaurantMenu: ImageVector by lazy {
        cute("RestaurantMenu") {
            // 书页 + 书脊
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12.0f, 6.0f)
                quadTo(8.0f, 4.5f, 4.4f, 5.1f)
                lineTo(4.4f, 16.1f)
                quadTo(8.0f, 15.3f, 12.0f, 16.9f)
                quadTo(16.0f, 15.3f, 19.6f, 16.1f)
                lineTo(19.6f, 5.1f)
                quadTo(16.0f, 4.5f, 12.0f, 6.0f)
                moveTo(12.0f, 6.0f)
                lineTo(12.0f, 16.9f)
            }
            // 左页小碗
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(6.2f, 11.0f)
                quadTo(8.3f, 14.8f, 10.4f, 11.0f)
            }
            // 碗里热气（小圆圈）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.2f) {
                circlePath(8.3f, 9.0f, 0.55f)
            }
            // 右页菜单条目（圆点 + 线 ×2）
            path(fill = SolidColor(Color.Black)) {
                circlePath(14.4f, 9.9f, 0.55f)
                circlePath(14.4f, 12.9f, 0.55f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(15.5f, 9.9f); lineTo(17.9f, 9.9f)
                moveTo(15.5f, 12.9f); lineTo(17.9f, 12.9f)
            }
        }
    }

    /** 警示：圆角胖三角 + 圆头感叹号 + 半透明小汗珠（不凶版） */
    val WarningAmber: ImageVector by lazy {
        cute("WarningAmber") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6.4f, 19.4f)
                lineTo(17.6f, 19.4f)
                quadTo(19.6f, 19.4f, 18.65f, 17.75f)
                lineTo(13.05f, 8.05f)
                quadTo(12.0f, 6.2f, 10.95f, 8.05f)
                lineTo(5.35f, 17.75f)
                quadTo(4.4f, 19.4f, 6.4f, 19.4f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(12.0f, 9.6f)
                lineTo(12.0f, 14.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                circlePath(12.0f, 17.0f, 1.0f)
            }
            // 小汗珠
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                moveTo(18.3f, 5.9f)
                curveTo(19.4f, 7.4f, 19.2f, 8.6f, 18.3f, 8.6f)
                curveTo(17.4f, 8.6f, 17.2f, 7.4f, 18.3f, 5.9f)
                close()
            }
        }
    }

    /** 一致通过：两只圆手套相碰，交握上方一颗爱心 */
    val Handshake: ImageVector by lazy {
        cute("Handshake") {
            // 左手套
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4.6f, 12.8f)
                quadTo(4.4f, 9.6f, 7.8f, 9.4f)
                quadTo(11.0f, 9.2f, 11.2f, 11.6f)
                quadTo(11.4f, 14.4f, 8.0f, 14.8f)
                quadTo(5.0f, 15.0f, 4.6f, 12.8f)
                close()
            }
            // 右手套
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(19.4f, 16.2f)
                quadTo(19.6f, 13.0f, 16.2f, 12.8f)
                quadTo(13.0f, 12.6f, 12.8f, 15.0f)
                quadTo(12.6f, 17.8f, 16.0f, 18.2f)
                quadTo(19.0f, 18.4f, 19.4f, 16.2f)
                close()
            }
            // 成交爱心
            path(fill = SolidColor(Color.Black)) {
                heart(12.0f, 6.8f, 1.35f)
            }
        }
    }

    /** 搜索：粗圆镜片 + 短胖手柄，镜片里的小脸在偷看 */
    val Search: ImageVector by lazy {
        cute("Search") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(10.6f, 10.2f, 6.0f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(15.0f, 14.7f)
                lineTo(19.2f, 18.9f)
            }
            kawaiiFace(cx = 10.6f, cy = 10.3f, s = 0.95f)
        }
    }

    /** 完成：特粗圆头对勾 + 末端小闪光 */
    val Check: ImageVector by lazy {
        cute("Check") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4.9f, 12.6f)
                lineTo(9.5f, 17.2f)
                lineTo(19.2f, 7.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                star4(18.2f, 4.3f, 1.1f)
            }
        }
    }

    /** 换一批：对置圆弧 + 实心圆头端点，中心半透明小点 */
    val Refresh: ImageVector by lazy {
        cute("Refresh") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                arcCC(12f, 12f, 7.4f, -35f, 250f)
                arcCC(12f, 12f, 7.4f, 145f, 250f)
            }
            path(fill = SolidColor(Color.Black)) {
                circlePath(5.94f, 7.76f, 1.15f)
                circlePath(18.06f, 16.24f, 1.15f)
            }
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                circlePath(12f, 12f, 1.0f)
            }
        }
    }

    /** 关闭：特粗圆头 X */
    val Close: ImageVector by lazy {
        cute("Close") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(6.6f, 6.6f); lineTo(17.4f, 17.4f)
                moveTo(17.4f, 6.6f); lineTo(6.6f, 17.4f)
            }
        }
    }

    /** 返回：实心圆角胖箭头，箭身微微上拱（RTL 由 What2EatBackIcon 镜像） */
    val ArrowBack: ImageVector by lazy {
        cute("ArrowBack") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.3f, 10.3f)
                quadTo(14.6f, 9.3f, 18.7f, 10.6f)
                quadTo(19.9f, 12.0f, 18.7f, 13.4f)
                quadTo(14.6f, 14.7f, 9.3f, 13.7f)
                lineTo(9.3f, 16.0f)
                quadTo(9.3f, 17.1f, 8.35f, 16.6f)
                lineTo(5.7f, 13.5f)
                quadTo(4.7f, 12.0f, 5.7f, 10.5f)
                lineTo(8.35f, 7.4f)
                quadTo(9.3f, 6.9f, 9.3f, 8.0f)
                lineTo(9.3f, 10.3f)
                close()
            }
        }
    }

    /** 编辑：斜胖铅笔 + 笔尖小点 + 笔下小波浪 */
    val Edit: ImageVector by lazy {
        cute("Edit") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(7.8f, 19.0f)
                lineTo(18.4f, 8.4f)
                quadTo(18.9f, 5.9f, 16.1f, 5.6f)
                lineTo(5.0f, 16.2f)
                quadTo(4.5f, 18.7f, 7.8f, 19.0f)
                close()
            }
            // 橡皮分隔线
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(17.9f, 9.4f)
                lineTo(15.1f, 6.6f)
            }
            // 笔尖小点
            path(fill = SolidColor(Color.Black)) {
                circlePath(6.9f, 17.0f, 0.62f)
            }
            // 小波浪
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(5.0f, 20.5f)
                quadTo(6.6f, 19.3f, 8.2f, 20.5f)
                quadTo(9.8f, 21.6f, 11.3f, 20.5f)
            }
        }
    }

    /** 列表项右箭头：超粗圆头 V */
    val ChevronRight: ImageVector by lazy {
        cute("ChevronRight") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9.2f, 5.2f)
                lineTo(16.4f, 12.0f)
                lineTo(9.2f, 18.8f)
            }
        }
    }

    /** 禁止/排除：粗圆 + 圆头斜杠（全圆角化） */
    val Block: ImageVector by lazy {
        cute("Block") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 7.4f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(6.8f, 17.2f)
                lineTo(17.2f, 6.8f)
            }
        }
    }

    /** 添加：特粗圆头加号 */
    val Add: ImageVector by lazy {
        cute("Add") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(12.0f, 5.4f); lineTo(12.0f, 18.6f)
                moveTo(5.4f, 12.0f); lineTo(18.6f, 12.0f)
            }
        }
    }

    /** 清除：圆头小扫帚 + 扫痕 + 小闪光（扫干净啦） */
    val Clear: ImageVector by lazy {
        cute("Clear") {
            // 扫帚柄
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(17.8f, 4.9f)
                lineTo(12.7f, 10.0f)
            }
            // 扫帚头（梯形）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(11.8f, 9.4f)
                lineTo(6.3f, 13.1f)
                lineTo(9.3f, 16.1f)
                lineTo(12.9f, 10.6f)
                close()
            }
            // 扫痕
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(4.9f, 18.4f); lineTo(6.2f, 19.7f)
                moveTo(3.4f, 16.0f); lineTo(4.7f, 17.3f)
            }
            // 小闪光 ×2
            path(fill = SolidColor(Color.Black)) {
                star4(19.2f, 12.6f, 1.0f)
            }
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                star4(15.8f, 16.6f, 0.8f)
            }
        }
    }

    /** 复制：两张叠放圆角卡片，前卡带小脸 */
    val ContentCopy: ImageVector by lazy {
        cute("ContentCopy") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                roundedRect(3.8f, 3.8f, 14.8f, 14.8f, 2.0f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                roundedRect(8.8f, 8.8f, 20.2f, 20.2f, 2.0f)
            }
            kawaiiFace(cx = 14.5f, cy = 14.1f, s = 0.9f)
        }
    }

    /** 地图：圆角地图 + 折痕 + 圆点路线 + 终点小旗 */
    val Map: ImageVector by lazy {
        cute("Map") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                roundedRect(4.2f, 5.0f, 19.8f, 19.0f, 2.0f)
            }
            // 折痕
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(9.4f, 5.6f); lineTo(9.4f, 18.4f)
                moveTo(14.8f, 5.6f); lineTo(14.8f, 18.4f)
            }
            // 路线
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(7.0f, 15.4f)
                quadTo(11.0f, 10.6f, 16.0f, 11.0f)
            }
            // 起点圆点
            path(fill = SolidColor(Color.Black)) {
                circlePath(7.0f, 15.4f, 0.7f)
            }
            // 终点小旗
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(16.8f, 11.0f)
                lineTo(16.8f, 7.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.8f, 7.0f)
                lineTo(19.4f, 8.1f)
                lineTo(16.8f, 9.2f)
                close()
            }
        }
    }

    /** 语言：圆地球 + 顶部经线小叶 + 中央小脸 + 右上对话气泡 */
    val Language: ImageVector by lazy {
        cute("Language") {
            // 地球
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(11.2f, 12.4f, 7.2f)
            }
            // 顶部经线（小叶形）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(11.2f, 5.6f)
                quadTo(9.0f, 8.6f, 11.2f, 10.4f)
                quadTo(13.4f, 8.6f, 11.2f, 5.6f)
                close()
            }
            kawaiiFace(cx = 11.2f, cy = 14.2f, s = 0.85f)
            // 对话气泡
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                roundedRect(15.4f, 3.2f, 20.2f, 7.4f, 1.5f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.9f, 7.2f)
                lineTo(14.9f, 9.6f)
                lineTo(17.6f, 7.4f)
                close()
                circlePath(16.6f, 5.3f, 0.5f)
                circlePath(18.9f, 5.3f, 0.5f)
            }
        }
    }

    // ── 偏好五档心情脸 ──────────────────────────────

    /** 偏好 -2 非常不喜欢：大哭脸（倒八字眉 + 下弯嘴 + 泪滴） */
    val MoodCry: ImageVector by lazy {
        cute("MoodCry") {
            // 脸
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 9f)
            }
            // 倒八字眉
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(8.3f, 9.6f); lineTo(10.6f, 10.6f)
                moveTo(15.7f, 9.6f); lineTo(13.4f, 10.6f)
            }
            // 点眼
            path(fill = SolidColor(Color.Black)) {
                circlePath(9.8f, 12.6f, 0.75f)
                circlePath(14.2f, 12.6f, 0.75f)
            }
            // 下弯嘴
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(10.2f, 17.2f)
                quadTo(12f, 15.6f, 13.8f, 17.2f)
            }
            // 泪滴
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                moveTo(7.6f, 13.6f)
                curveTo(8.5f, 15.0f, 8.4f, 16.0f, 7.6f, 16.0f)
                curveTo(6.8f, 16.0f, 6.7f, 15.0f, 7.6f, 13.6f)
                close()
            }
        }
    }

    /** 偏好 -1 不太喜欢：不开心（点眼 + 撇嘴） */
    val MoodFrown: ImageVector by lazy {
        cute("MoodFrown") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 9f)
            }
            path(fill = SolidColor(Color.Black)) {
                circlePath(9.6f, 11.4f, 0.75f)
                circlePath(14.4f, 11.4f, 0.75f)
            }
            // 撇嘴（微微下撇）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(9.8f, 16.8f)
                quadTo(12f, 15.9f, 14.2f, 16.8f)
            }
        }
    }

    /** 偏好 0 无所谓：平静脸（点眼 + 直线小嘴） */
    val MoodNeutral: ImageVector by lazy {
        cute("MoodNeutral") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 9f)
            }
            path(fill = SolidColor(Color.Black)) {
                circlePath(9.6f, 11.2f, 0.75f)
                circlePath(14.4f, 11.2f, 0.75f)
            }
            // 直线嘴
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(10.2f, 16.2f); lineTo(13.8f, 16.2f)
            }
        }
    }

    /** 偏好 +1 喜欢：微笑脸 + 腮红 */
    val MoodSmile: ImageVector by lazy {
        cute("MoodSmile") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 9f)
            }
            path(fill = SolidColor(Color.Black)) {
                circlePath(9.6f, 11.0f, 0.75f)
                circlePath(14.4f, 11.0f, 0.75f)
            }
            // 微笑
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(9.4f, 15.2f)
                quadTo(12f, 17.6f, 14.6f, 15.2f)
            }
            // 腮红
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                circlePath(6.9f, 14.0f, 0.9f)
                circlePath(17.1f, 14.0f, 0.9f)
            }
        }
    }

    /** 偏好 +2 非常喜欢：眯眯笑眼 + 大笑嘴 + 星星 */
    val MoodLove: ImageVector by lazy {
        cute("MoodLove") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round) {
                circlePath(12f, 12f, 9f)
            }
            // 眯眯眼（上弯弧）
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(8.6f, 11.4f)
                quadTo(9.6f, 10.2f, 10.6f, 11.4f)
                moveTo(13.4f, 11.4f)
                quadTo(14.4f, 10.2f, 15.4f, 11.4f)
            }
            // 大笑嘴（张开的月牙）
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.2f, 14.6f)
                quadTo(12f, 18.4f, 14.8f, 14.6f)
                quadTo(12f, 15.9f, 9.2f, 14.6f)
                close()
            }
            // 星星装饰
            path(fill = SolidColor(Color.Black.copy(alpha = 0.45f))) {
                star4(18.6f, 7.4f, 1.0f)
            }
        }
    }

    /**
     * 品牌吉祥物「饭碗小精灵」（多彩矢量，与启动器 Logo 同款）。
     *
     * 多色图标：使用时 Icon(tint = Color.Unspecified) 保留原色。
     * 米白饭团脸 + 奶油橘碗身 + 三缕热气 + 腮红。
     */
    val Mascot: ImageVector by lazy {
        ImageVector.Builder(
            name = "What2Eat.Mascot",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            val brown = Color(0xFF6B3F1D)
            val bowlOrange = Color(0xFFE08A3C)
            val cream = Color(0xFFFFF8F2)
            val blush = Color(0xFFFFC9A3)
            val ink = Color(0xFF3A1700)
            val steam = Color(0xFFD9772F)
            // 热气三小圆
            path(fill = SolidColor(steam.copy(alpha = 0.85f))) {
                circlePath(19.0f, 8.6f, 1.5f)
                circlePath(24.0f, 6.2f, 1.8f)
                circlePath(29.0f, 8.6f, 1.5f)
            }
            // 饭团圆顶（米白 + 棕描边）
            path(
                fill = SolidColor(cream),
                stroke = SolidColor(brown), strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.0f, 25.0f)
                curveTo(11.0f, 15.2f, 37.0f, 15.2f, 37.0f, 25.0f)
                close()
            }
            // 碗身（奶油橘）
            path(fill = SolidColor(bowlOrange), stroke = SolidColor(brown), strokeLineWidth = 1.6f) {
                moveTo(9.0f, 25.0f)
                lineTo(39.0f, 25.0f)
                curveTo(39.0f, 35.2f, 33.0f, 40.5f, 24.0f, 40.5f)
                curveTo(15.0f, 40.5f, 9.0f, 35.2f, 9.0f, 25.0f)
                close()
            }
            // 碗身装饰条纹（米白）
            path(stroke = SolidColor(cream), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(13.5f, 29.5f); lineTo(34.5f, 29.5f)
            }
            // 眼睛 + 微笑 + 腮红
            path(fill = SolidColor(ink)) {
                circlePath(19.6f, 32.4f, 1.5f)
                circlePath(28.4f, 32.4f, 1.5f)
            }
            path(stroke = SolidColor(ink), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round) {
                moveTo(20.8f, 35.6f)
                quadTo(24.0f, 38.4f, 27.2f, 35.6f)
            }
            path(fill = SolidColor(blush)) {
                circlePath(14.8f, 33.6f, 1.6f)
                circlePath(33.2f, 33.6f, 1.6f)
            }
        }.build()
    }
}

// ─────────────────────────────────────────────────────────────
// 私有构建工具
// ─────────────────────────────────────────────────────────────

/** 统一构建器：24dp 画布。 */
private fun cute(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "What2Eat.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(content).build()

/**
 * 统一卡哇伊小脸：圆点眼 ×2 + 微笑弧。
 *
 * @param s 缩放（默认 1；小容器内用 0.8–0.95）
 */
private fun ImageVector.Builder.kawaiiFace(cx: Float, cy: Float, s: Float = 1f) {
    val eyeR = 0.62f * s
    val gap = 1.05f * s
    path(fill = SolidColor(Color.Black)) {
        circlePath(cx - gap, cy, eyeR)
        circlePath(cx + gap, cy, eyeR)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round
    ) {
        val mw = 1.1f * s
        moveTo(cx - mw, cy + 1.7f * s)
        quadTo(cx, cy + 3.0f * s, cx + mw, cy + 1.7f * s)
    }
}

/** 整圆（闭合子路径，描边/填充通用）。 */
private fun PathBuilder.circlePath(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    close()
}

/** 圆角矩形（闭合子路径）。 */
private fun PathBuilder.roundedRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) {
    moveTo(x0 + r, y0)
    lineTo(x1 - r, y0)
    quadTo(x1, y0, x1, y0 + r)
    lineTo(x1, y1 - r)
    quadTo(x1, y1, x1 - r, y1)
    lineTo(x0 + r, y1)
    quadTo(x0, y1, x0, y1 - r)
    lineTo(x0, y0 + r)
    quadTo(x0, y0, x0 + r, y0)
    close()
}

/**
 * 开放圆弧（三次贝塞尔逼近，自动分段）。
 * 角度制：0° 指向右，90° 指向下（屏幕坐标系），正 sweep 为顺时针。
 */
private fun PathBuilder.arcCC(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
    val segs = ceil(abs(sweepDeg) / 90f).toInt().coerceAtLeast(1)
    val step = sweepDeg / segs
    val k = (4f / 3f) * tan(Math.toRadians((abs(step) / 2f).toDouble())).toFloat()
    val a0 = Math.toRadians(startDeg.toDouble())
    moveTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat())
    for (i in 0 until segs) {
        val b0 = Math.toRadians((startDeg + step * i).toDouble())
        val b1 = Math.toRadians((startDeg + step * (i + 1)).toDouble())
        val p0x = cx + r * cos(b0).toFloat()
        val p0y = cy + r * sin(b0).toFloat()
        val p1x = cx + r * cos(b1).toFloat()
        val p1y = cy + r * sin(b1).toFloat()
        val t0x = -sin(b0).toFloat()
        val t0y = cos(b0).toFloat()
        val t1x = -sin(b1).toFloat()
        val t1y = cos(b1).toFloat()
        curveTo(
            p0x + t0x * k * r, p0y + t0y * k * r,
            p1x - t1x * k * r, p1y - t1y * k * r,
            p1x, p1y
        )
    }
}

/** 实心小爱心（[s] 控制大小）。 */
private fun PathBuilder.heart(cx: Float, cy: Float, s: Float) {
    moveTo(cx, cy + s)
    curveTo(cx - 1.4f * s, cy + 0.15f * s, cx - 0.75f * s, cy - 0.9f * s, cx, cy - 0.3f * s)
    curveTo(cx + 0.75f * s, cy - 0.9f * s, cx + 1.4f * s, cy + 0.15f * s, cx, cy + s)
    close()
}

/** 四角小闪光/星星（[r] 控制大小）。 */
private fun PathBuilder.star4(cx: Float, cy: Float, r: Float) {
    val q = r * 0.28f
    moveTo(cx, cy - r)
    quadTo(cx + q, cy - q, cx + r, cy)
    quadTo(cx + q, cy + q, cx, cy + r)
    quadTo(cx - q, cy + q, cx - r, cy)
    quadTo(cx - q, cy - q, cx, cy - r)
    close()
}
