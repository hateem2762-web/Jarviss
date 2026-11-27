package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gesture.GlobalAirGestureHudState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun GlobalAirGestureHudPill(
    state: GlobalAirGestureHudState,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActionFlash = state.lastActionLabel.isNotBlank()
    val targetBorderColor = when {
        hasActionFlash -> JarvisAmber
        state.isHandTracked -> JarvisEmerald
        state.isCameraActive -> JarvisCyan.copy(alpha = 0.65f)
        else -> Color(0xFF334155)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 220),
        label = "air_hud_border",
    )

    val dotColor = when {
        hasActionFlash -> JarvisAmber
        state.isHandTracked -> JarvisEmerald
        state.isCameraActive -> JarvisCyan
        else -> Color(0xFF64748B)
    }

    val pulseTransition = rememberInfiniteTransition(label = "air_hud_dot")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "air_hud_dot_alpha",
    )

    val displayText = when {
        hasActionFlash -> state.lastActionLabel
        state.isHandTracked -> "HAND TRACKED // READY"
        else -> state.statusLabel.ifBlank { "AIR GESTURE // HAZIR" }
    }

    Surface(
        modifier = modifier
            .testTag("global_air_gesture_hud_pill")
            .semantics { contentDescription = "Uzaktan el hareketi durumu: $displayText" },
        shape = RoundedCornerShape(999.dp),
        color = Color(0xEB0A101B),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .alpha(if (state.isCameraActive) dotAlpha else 0.7f)
                    .background(dotColor),
            )

            Icon(
                imageVector = Icons.Rounded.PanTool,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(13.dp),
            )

            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.7.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (hasActionFlash) JarvisAmber else Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .testTag("global_air_gesture_hud_close"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "El kontrolünü kapat",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
