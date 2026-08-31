package com.what2eat.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.what2eat.MainViewModel
import com.what2eat.core.designsystem.icon.What2EatIcons
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R

/**
 * 首次打开的可爱引导页。
 *
 * 吉祥物饭碗小精灵欢迎 + 修改人物名字 → 进入主界面。
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf(MainViewModel.DEFAULT_USER_NAME) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 吉祥物（呼吸浮动）
        val float = rememberInfiniteTransition(label = "mascotFloat")
        val offsetY by float.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "mascotOffsetY"
        )
        Icon(
            imageVector = What2EatIcons.Mascot,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { translationY = offsetY }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "你好呀！我是饭饭",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "今天吃什么，交给我来帮你决定吧",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 名字输入
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(12) },
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                viewModel.completeOnboarding(name)
                onFinished()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "开始干饭之旅",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "名字之后可以随时在设置里修改",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * 可爱加载页（启动瞬间展示）。
 *
 * 吉祥物呼吸 + 三颗跳动的小点。
 */
@Composable
fun CuteSplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 吉祥物呼吸
        val breathe = remember { Animatable(1f) }
        LaunchedEffect(Unit) {
            while (true) {
                breathe.animateTo(1.06f, tween(900, easing = FastOutSlowInEasing))
                breathe.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }
        Icon(
            imageVector = What2EatIcons.Mascot,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(140.dp)
                .scale(breathe.value)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "What2Eat",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "今天吃什么？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 三颗跳动小点
        val dots = rememberInfiniteTransition(label = "loadingDots")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { i ->
                val scale by dots.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = i * 160, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(scale)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            CircleShape
                        )
                )
            }
        }
    }
}
