package com.what2eat.core.designsystem.animation

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * What2Eat 动效体系（v0.9.4）——「弹跳微交互」。
 *
 * 调性与手绘圆润风配套：全部基于 spring 物理回弹（MediumBouncy 过冲），
 * 不做辉光/脉冲/闪烁类效果。四个 Modifier 可单独或叠加使用。
 */

/**
 * 按压缩小 + 松手弹回（bouncy spring）。
 *
 * 替代 Card(onClick=...) / clickable 的卡片用法：把 Card 换成不可点重载，
 * 本修饰符挂 Card 上，按压时整卡缩放、松手带回弹过冲，同时保留涟漪反馈。
 */
fun Modifier.bounceClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.955f,
    enabled: Boolean = true
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncePressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * v1.3.0：bounceClickable 的长按变体（待整理多选：单击选择/长按进入选择态）。
 * 与 bounceClickable 同一按压回弹手感，手势层换成 combinedClickable。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.955f,
    enabled: Boolean = true
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncePressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

/**
 * 入场弹跳：缩放 fromScale→1 带过冲回弹 + 淡入。
 *
 * @param key 变化时重播（如「换一个」后新结果重新弹出）
 * @param delayMillis 交错入场延迟（列表 stagger 用）
 */
fun Modifier.entranceBounce(
    key: Any? = null,
    delayMillis: Int = 0,
    fromScale: Float = 0.55f
): Modifier = composed {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else fromScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "entranceScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "entranceAlpha"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * 无限轻浮：上下 ±amplitude dp + 左右微摆 swayDegrees°。
 *
 * 吉祥物专用，缓入缓出正弦节奏，安静不抢戏。
 */
fun Modifier.gentleBob(
    amplitude: Float = 3f,
    swayDegrees: Float = 1.5f,
    durationMillis: Int = 1400
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "gentleBob")
    val t by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobT"
    )
    this.graphicsLayer {
        translationY = t * amplitude
        rotationZ = t * swayDegrees
    }
}

/**
 * 按压回弹（配合自带 interactionSource 的组件：Button / FAB）。
 *
 * 用法：
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * Button(onClick = ..., interactionSource = interaction,
 *     modifier = Modifier.bouncyPress(interaction)) { ... }
 * ```
 */
fun Modifier.bouncyPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonPressScale"
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
}
