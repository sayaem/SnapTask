package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay

/**
 * Ultra-minimal, typography-first splash screen.
 * Displays only the single-color "SnapTask" wordmark in a premium, confident display typeface
 * surrounded by generous negative space at the optical center of the screen.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    animationDurationMs: Int = 380,
    holdDurationMs: Long = 200L
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Very subtle entrance animation: alpha 0 -> 1 and scale 0.98 -> 1.0
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDurationMs,
                easing = FastOutSlowInEasing
            )
        )
        if (holdDurationMs > 0) {
            delay(holdDurationMs)
        }
        onSplashFinished()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("splash_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            // Optical visual center: slightly above mathematical center to avoid perceived sag
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.06f)
        ) {
            // Responsive scale: 34sp on compact/narrow devices up to 42sp on expanded/tablets
            val responsiveFontSize = when {
                maxWidth < 360.dp -> 34.sp
                maxWidth > 600.dp -> 42.sp
                else -> 38.sp
            }

            val scale = 0.98f + (0.02f * animatable.value)

            Text(
                text = "SnapTask",
                style = TextStyle(
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold, // 800: bold, confident, elegant
                    fontSize = responsiveFontSize,
                    lineHeight = (responsiveFontSize.value * 1.15f).sp,
                    letterSpacing = (-0.6).sp, // slightly tightened, natural tracking
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = animatable.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag("splash_wordmark")
            )
        }
    }
}
