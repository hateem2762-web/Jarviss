package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.model.ActionExecutionStatus
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.model.JarvisMood
import com.example.model.JarvisVisualState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

fun resolveMoodAccentColor(
    visualState: JarvisVisualState,
    mood: JarvisMood,
): Color {
    if (visualState == JarvisVisualState.ERROR || mood == JarvisMood.ALERT) return JarvisCrimson
    if (visualState == JarvisVisualState.LISTENING) return JarvisEmerald
    return when (mood) {
        JarvisMood.NEUTRAL -> if (visualState == JarvisVisualState.IDLE) Color(0xFF60A5FA) else JarvisCyan
        JarvisMood.SERIOUS -> JarvisAmber
        JarvisMood.ANALYTICAL -> Color(0xFFA78BFA)
        JarvisMood.ENERGETIC -> Color(0xFF22D3EE)
        JarvisMood.ALERT -> JarvisCrimson
    }
}

@Composable
fun JarvisHudHeader(
    topicHeader: String,
    visualState: JarvisVisualState,
    mood: JarvisMood,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        targetValue = resolveMoodAccentColor(visualState, mood),
        animationSpec = tween(350),
        label = "hud_header_accent",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("jarvis_hud_header"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = topicHeader.ifBlank { "ÇEKİRDEK HAZIR // BEKLEMEDE" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(148.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.65f),
                            Color.Transparent,
                        )
                    )
                ),
        )
    }
}

@Composable
fun LiveSubtitleDisplay(
    subtitleText: String,
    subtitleKey: Any,
    visualState: JarvisVisualState,
    mood: JarvisMood,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        targetValue = resolveMoodAccentColor(visualState, mood),
        animationSpec = tween(300),
        label = "subtitle_accent",
    )

    val shouldAnimateTypewriter =
        visualState == JarvisVisualState.SPEAKING ||
            (visualState == JarvisVisualState.IDLE && subtitleText.isNotBlank())

    var visibleChars by remember(subtitleKey, subtitleText) {
        mutableIntStateOf(if (shouldAnimateTypewriter) 0 else subtitleText.length)
    }

    LaunchedEffect(subtitleKey, subtitleText, shouldAnimateTypewriter) {
        if (!shouldAnimateTypewriter || subtitleText.isBlank()) {
            visibleChars = subtitleText.length
            return@LaunchedEffect
        }
        visibleChars = 0
        val step = when {
            subtitleText.length > 260 -> 4
            subtitleText.length > 110 -> 2
            else -> 1
        }
        val delayMs = when {
            subtitleText.length > 260 -> 12L
            subtitleText.length > 110 -> 16L
            else -> 22L
        }
        while (visibleChars < subtitleText.length) {
            visibleChars = (visibleChars + step).coerceAtMost(subtitleText.length)
            delay(delayMs)
        }
    }

    val displayedText = if (subtitleText.isBlank()) {
        when (visualState) {
            JarvisVisualState.LISTENING -> "Dinliyorum…"
            JarvisVisualState.THINKING -> "İşleniyor…"
            JarvisVisualState.ERROR -> "Sistem hatası algılandı."
            else -> "Komut bekleniyor"
        }
    } else {
        subtitleText.take(visibleChars.coerceIn(0, subtitleText.length))
    }

    val textColor = when {
        visualState == JarvisVisualState.ERROR -> Color(0xFFFECDD3)
        subtitleText.isBlank() && visualState == JarvisVisualState.IDLE ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        visualState == JarvisVisualState.LISTENING -> accent
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("live_subtitle_display"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 20.sp,
                fontWeight = if (visualState == JarvisVisualState.LISTENING) FontWeight.Medium else FontWeight.Normal,
            ),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Bubble-free minimalist RecyclerView chat stream with smooth top fade-out mask.
 */
@Composable
fun MinimalistChatStreamView(
    messages: List<JarvisMessage>,
    accentColor: Color,
    speakingMessageId: Long?,
    onRunAction: (Long, JarvisAction) -> Unit,
    onCopyMessage: (String) -> Unit,
    onToggleSpeakMessage: (JarvisMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceFadeColor = MaterialTheme.colorScheme.background
    val accentArgb = accentColor.toArgb()

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val layoutManager = LinearLayoutManager(ctx).apply {
                    stackFromEnd = true
                }
                val adapter = ChatStreamAdapter(
                    onRunAction = onRunAction,
                    onCopyMessage = onCopyMessage,
                    onToggleSpeakMessage = onToggleSpeakMessage,
                )
                RecyclerView(ctx).apply {
                    setHasFixedSize(false)
                    itemAnimator = null
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalFadingEdgeEnabled = true
                    setFadingEdgeLength((36 * ctx.resources.displayMetrics.density).toInt())
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    this.layoutManager = layoutManager
                    this.adapter = adapter
                }
            },
            update = { rv ->
                val adapter = rv.adapter as? ChatStreamAdapter ?: return@AndroidView
                val previousCount = adapter.itemCount
                adapter.updateAppearance(accentArgb, speakingMessageId)
                adapter.submitList(messages) {
                    if (messages.isNotEmpty() && messages.size != previousCount) {
                        rv.scrollToPosition(messages.lastIndex)
                    }
                }
            },
        )

        // Soft top fade-out veil so older lines dissolve seamlessly as they scroll upward
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surfaceFadeColor.copy(alpha = 0.94f),
                            surfaceFadeColor.copy(alpha = 0.45f),
                            Color.Transparent,
                        )
                    )
                ),
        )
    }
}

private class ChatStreamAdapter(
    private val onRunAction: (Long, JarvisAction) -> Unit,
    private val onCopyMessage: (String) -> Unit,
    private val onToggleSpeakMessage: (JarvisMessage) -> Unit,
) : ListAdapter<JarvisMessage, ChatStreamAdapter.StreamViewHolder>(DiffCallback) {

    private var accentColorArgb: Int = AndroidColor.rgb(56, 189, 248)
    private var speakingId: Long? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateAppearance(accentArgb: Int, speakingMessageId: Long?) {
        if (accentColorArgb != accentArgb || speakingId != speakingMessageId) {
            accentColorArgb = accentArgb
            speakingId = speakingMessageId
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val ctx = parent.context
        val dp = ctx.resources.displayMetrics.density

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding((16 * dp).toInt(), (7 * dp).toInt(), (16 * dp).toInt(), (7 * dp).toInt())
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val speakerLabel = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
        }

        val metaTools = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(AndroidColor.argb(175, 148, 163, 184))
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
        }

        headerRow.addView(speakerLabel)
        headerRow.addView(
            View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }
        )
        headerRow.addView(metaTools)

        val bodyText = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(2f * dp, 1.08f)
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }

        val actionChip = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (5 * dp).toInt()
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(AndroidColor.argb(42, 30, 41, 59))
                setStroke((1 * dp).toInt(), AndroidColor.argb(90, 100, 116, 139))
            }
        }

        val hairline = View(ctx).apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (0.6f * dp).toInt().coerceAtLeast(1),
            ).apply {
                topMargin = (8 * dp).toInt()
            }
            layoutParams = lp
            setBackgroundColor(AndroidColor.argb(26, 148, 163, 184))
        }

        container.addView(headerRow)
        container.addView(bodyText)
        container.addView(actionChip)
        container.addView(hairline)

        return StreamViewHolder(container, speakerLabel, metaTools, bodyText, actionChip)
    }

    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        val item = getItem(position)
        val isSpeaking = speakingId == item.id

        if (item.isUser) {
            holder.speakerLabel.text = "SEN"
            holder.speakerLabel.setTextColor(AndroidColor.argb(190, 148, 163, 184))
            holder.bodyText.setTextColor(AndroidColor.rgb(214, 222, 235))
            holder.metaTools.visibility = View.GONE
        } else {
            holder.speakerLabel.text = "JARVIS"
            holder.speakerLabel.setTextColor(accentColorArgb)
            holder.bodyText.setTextColor(AndroidColor.rgb(241, 245, 249))
            holder.metaTools.visibility = View.VISIBLE
            holder.metaTools.text = if (isSpeaking) "■ DURDUR" else "▶ DİNLE  •  KOPYALA"
            holder.metaTools.setOnClickListener {
                onToggleSpeakMessage(item)
            }
        }

        holder.bodyText.text = item.text
        holder.bodyText.setOnLongClickListener {
            onCopyMessage(item.text)
            true
        }
        holder.container.setOnClickListener {
            if (!item.isUser) {
                onCopyMessage(item.text)
            }
        }

        val action = item.action
        if (action != null) {
            holder.actionChip.visibility = View.VISIBLE
            val statusMark = when (item.actionStatus) {
                ActionExecutionStatus.EXECUTED -> "✓"
                ActionExecutionStatus.FAILED -> "!"
                ActionExecutionStatus.PENDING -> "▶"
            }
            val detailSuffix = item.actionStatusDetail
                .takeIf { it.isNotBlank() && it != item.text }
                ?.let { " · $it" }
                .orEmpty()
            holder.actionChip.text = "$statusMark ${action.intent.displayLabel}$detailSuffix"
            holder.actionChip.setTextColor(
                when (item.actionStatus) {
                    ActionExecutionStatus.EXECUTED -> AndroidColor.rgb(16, 185, 129)
                    ActionExecutionStatus.FAILED -> AndroidColor.rgb(244, 63, 94)
                    ActionExecutionStatus.PENDING -> accentColorArgb
                }
            )
            holder.actionChip.setOnClickListener {
                onRunAction(item.id, action)
            }
        } else {
            holder.actionChip.visibility = View.GONE
            holder.actionChip.setOnClickListener(null)
        }
    }

    class StreamViewHolder(
        val container: LinearLayout,
        val speakerLabel: TextView,
        val metaTools: TextView,
        val bodyText: TextView,
        val actionChip: TextView,
    ) : RecyclerView.ViewHolder(container)

    private object DiffCallback : DiffUtil.ItemCallback<JarvisMessage>() {
        override fun areItemsTheSame(oldItem: JarvisMessage, newItem: JarvisMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: JarvisMessage, newItem: JarvisMessage): Boolean =
            oldItem == newItem
    }
}
