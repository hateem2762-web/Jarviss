package com.example.news

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.R
import com.example.model.JarvisMood
import com.example.model.JarvisVisualState
import com.example.ui.components.LiveSubtitleDisplay
import com.example.ui.components.ParticleNebulaCanvas
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun NewsBriefingStage(
    state: NewsBriefingState,
    compactOverlay: Boolean = false,
    onSelectIndex: (Int) -> Unit,
    onTogglePauseTts: () -> Unit,
    onToggleDetail: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ttsCadenceRms by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isSpeakingBriefing, state.isTtsPaused) {
        if (!state.isSpeakingBriefing || state.isTtsPaused) {
            ttsCadenceRms = 0f
            return@LaunchedEffect
        }
        var t = 0f
        while (state.isSpeakingBriefing && !state.isTtsPaused) {
            t += 0.28f
            ttsCadenceRms = (0.28f + 0.26f * abs(sin(t)) + 0.14f * abs(sin(t * 2.1f)))
                .coerceIn(0.16f, 0.72f)
            delay(65L)
        }
    }

    val visualState = when {
        state.isLoading -> JarvisVisualState.THINKING
        state.errorMessage != null -> JarvisVisualState.ERROR
        state.isSpeakingBriefing && !state.isTtsPaused -> JarvisVisualState.SPEAKING
        else -> JarvisVisualState.IDLE
    }
    val anchorMood = if (state.errorMessage != null) JarvisMood.ALERT else JarvisMood.SERIOUS
    val accentColor = if (state.isSpeakingBriefing && !state.isTtsPaused) JarvisAmber else JarvisCyan

    val currentItem = state.currentItem
    val hudHeaderLabel = if (state.topicQuery.isNotBlank()) {
        "GÜNDEM // ${state.topicQuery.uppercase(Locale("tr", "TR"))}"
    } else {
        stringResource(R.string.news_hud_label)
    }
    val subtitleText = when {
        state.isLoading -> if (state.topicQuery.isNotBlank()) {
            "\"${state.topicQuery}\" ile ilgili güncel haberler taranıyor ve özet hazırlanıyor…"
        } else {
            "Güncel haber kaynakları taranıyor ve özet hazırlanıyor…"
        }
        state.errorMessage != null -> state.errorMessage
        currentItem == null -> "Güncel bülten bulunamadı."
        state.showingDetail -> currentItem.detailedSummary
        else -> currentItem.briefSummary
    }
    val subtitleKey = when {
        state.isLoading -> "news_loading_${state.topicQuery}"
        state.errorMessage != null -> "news_err_${state.errorMessage}"
        currentItem != null -> "news_${currentItem.id}_${state.showingDetail}"
        else -> "news_empty"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("news_briefing_stage"),
        verticalArrangement = Arrangement.spacedBy(if (compactOverlay) 10.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Sleek Bar: Mini Particle Nebula + JARVIS // GÜNDEM + Optional Air Gesture Indicator + Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactOverlay) 38.dp else 44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0F16)),
                contentAlignment = Alignment.Center,
            ) {
                ParticleNebulaCanvas(
                    visualState = visualState,
                    mood = anchorMood,
                    rmsLevel = ttsCadenceRms,
                    compactMode = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hudHeaderLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = state.gestureStatusText.isNotBlank() || state.lastGestureLabel.isNotBlank()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PanTool,
                            contentDescription = "El hareketi sensörü",
                            tint = if (state.gestureSensorActive) JarvisEmerald else Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = state.lastGestureLabel.ifBlank { state.gestureStatusText },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                            ),
                            color = if (state.gestureSensorActive) JarvisEmerald else Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                IconButton(
                    onClick = onTogglePauseTts,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("news_tts_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (state.isTtsPaused || !state.isSpeakingBriefing) {
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.Rounded.Pause
                        },
                        contentDescription = stringResource(R.string.news_pause_tts),
                        tint = accentColor,
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_close_button"),
            ) {
                Icon(
                    imageVector = if (compactOverlay) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.news_return_chat),
                    tint = Color(0xFFCBD5E1),
                )
            }
        }

        // Main News Card Stage (ViewPager2 + Coil) or Loading / Error State
        when {
            state.isLoading -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactOverlay) 210.dp else 270.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0E131B),
                    border = BorderStroke(1.dp, Color(0xFF222B38)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = JarvisCyan,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.topicQuery.isNotBlank()) {
                                "\"${state.topicQuery}\" haberleri taranıyor…"
                            } else {
                                "Gündem taranıyor…"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                            ),
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF151218),
                    border = BorderStroke(1.dp, Color(0xFF3E2430)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFECDD3),
                        )
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tekrar Dene")
                        }
                    }
                }
            }

            state.items.isNotEmpty() -> {
                NewsViewPagerCard(
                    items = state.items,
                    currentIndex = state.currentIndex,
                    compactOverlay = compactOverlay,
                    onPageSelected = onSelectIndex,
                    onCardTapped = onToggleDetail,
                )
            }
        }

        // Live Anchor Subtitle Stream
        LiveSubtitleDisplay(
            subtitleText = subtitleText,
            subtitleKey = subtitleKey,
            visualState = visualState,
            mood = anchorMood,
            modifier = Modifier.heightIn(min = 48.dp),
        )

        // Bottom Minimalist Indicator Strip & Touch Controls
        if (state.items.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        if (state.currentIndex > 0) {
                            onSelectIndex(state.currentIndex - 1)
                        }
                    },
                    enabled = state.currentIndex > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("news_prev_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.news_previous),
                        tint = if (state.currentIndex > 0) Color(0xFFE2E8F0) else Color(0xFF475569),
                    )
                }

                // Minimalist dot/pill indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.items.forEachIndexed { idx, _ ->
                        val active = idx == state.currentIndex
                        val pillWidth by animateDpAsState(
                            targetValue = if (active) 20.dp else 6.dp,
                            animationSpec = tween(220),
                            label = "news_dot_width",
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(pillWidth)
                                .clip(CircleShape)
                                .background(
                                    if (active) accentColor else Color(0xFF334155)
                                )
                                .clickable { onSelectIndex(idx) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onToggleDetail,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("news_detail_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.showingDetail) "Kısa Özet" else stringResource(R.string.news_detail),
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.currentIndex < state.items.lastIndex) {
                                onSelectIndex(state.currentIndex + 1)
                            }
                        },
                        enabled = state.currentIndex < state.items.lastIndex,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("news_next_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.news_next),
                            tint = if (state.currentIndex < state.items.lastIndex) Color(0xFFE2E8F0) else Color(0xFF475569),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsViewPagerCard(
    items: List<NewsBriefingItem>,
    currentIndex: Int,
    compactOverlay: Boolean,
    onPageSelected: (Int) -> Unit,
    onCardTapped: () -> Unit,
) {
    val cardHeight = if (compactOverlay) 216.dp else 284.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .testTag("news_viewpager_container"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A0E14),
        border = BorderStroke(1.dp, Color(0xFF232D3D)),
        shadowElevation = 10.dp,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val adapter = NewsPagerAdapter(onCardTapped = onCardTapped)
                val pager = ViewPager2(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    orientation = ViewPager2.ORIENTATION_HORIZONTAL
                    offscreenPageLimit = 1
                    this.adapter = adapter
                    registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            onPageSelected(position)
                        }
                    })
                }
                pager.tag = adapter
                pager
            },
            update = { pager ->
                val adapter = pager.adapter as? NewsPagerAdapter ?: return@AndroidView
                adapter.submitItems(items)
                if (items.isNotEmpty()) {
                    val safeIndex = currentIndex.coerceIn(0, items.lastIndex)
                    if (pager.currentItem != safeIndex) {
                        pager.setCurrentItem(safeIndex, true)
                    }
                }
            },
        )
    }
}

private class NewsPagerAdapter(
    private val onCardTapped: () -> Unit,
) : RecyclerView.Adapter<NewsPagerAdapter.NewsCardHolder>() {

    private var items: List<NewsBriefingItem> = emptyList()

    fun submitItems(newItems: List<NewsBriefingItem>) {
        if (items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsCardHolder {
        val ctx = parent.context
        val dp = ctx.resources.displayMetrics.density

        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.rgb(10, 14, 20))
        }

        // Sleek abstract dark gradient fallback surface
        val fallbackSurface = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    AndroidColor.rgb(14, 25, 42),
                    AndroidColor.rgb(11, 16, 26),
                    AndroidColor.rgb(22, 18, 34),
                )
            )
        }

        val photoView = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Smooth dark scrim so headline & badges always remain razor sharp
        val scrimView = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    AndroidColor.argb(95, 8, 11, 16),
                    AndroidColor.argb(65, 8, 11, 16),
                    AndroidColor.argb(215, 8, 11, 16),
                    AndroidColor.argb(248, 8, 11, 16),
                )
            )
        }

        // Top Row: Category Badge + Page Index
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                val pad = (14 * dp).toInt()
                setMargins(pad, pad, pad, 0)
            }
        }

        val categoryBadge = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
            setTextColor(AndroidColor.rgb(103, 232, 249))
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 99f * dp
                setColor(AndroidColor.argb(180, 10, 18, 28))
                setStroke((1 * dp).toInt(), AndroidColor.argb(140, 56, 189, 248))
            }
        }

        val counterBadge = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(AndroidColor.argb(210, 203, 213, 225))
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 99f * dp
                setColor(AndroidColor.argb(160, 10, 14, 20))
            }
        }

        topRow.addView(categoryBadge)
        topRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        topRow.addView(counterBadge)

        // Bottom Text Overlay: Source/Time + Headline
        val bottomCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                val hPad = (16 * dp).toInt()
                val bPad = (15 * dp).toInt()
                setMargins(hPad, 0, hPad, bPad)
            }
        }

        val metaText = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(AndroidColor.rgb(148, 163, 184))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val headlineText = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(AndroidColor.rgb(248, 250, 252))
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(2f * dp, 1.08f)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        bottomCol.addView(metaText)
        bottomCol.addView(headlineText)

        root.addView(fallbackSurface)
        root.addView(photoView)
        root.addView(scrimView)
        root.addView(topRow)
        root.addView(bottomCol)

        root.setOnClickListener { onCardTapped() }

        return NewsCardHolder(
            root = root,
            photoView = photoView,
            categoryBadge = categoryBadge,
            counterBadge = counterBadge,
            metaText = metaText,
            headlineText = headlineText,
        )
    }

    override fun onBindViewHolder(holder: NewsCardHolder, position: Int) {
        val item = items[position]
        holder.categoryBadge.text = item.category.label
        holder.counterBadge.text = "${position + 1} / ${items.size}"
        holder.metaText.text = "${item.sourceName}  •  ${item.publishedAtText}"
        holder.headlineText.text = item.headline

        val accentArgb = when (item.category) {
            NewsCategory.GUNDEM -> AndroidColor.rgb(56, 189, 248)
            NewsCategory.TEKNOLOJI -> AndroidColor.rgb(167, 139, 250)
            NewsCategory.SPOR -> AndroidColor.rgb(52, 211, 153)
            NewsCategory.DUNYA -> AndroidColor.rgb(251, 191, 36)
        }
        holder.categoryBadge.setTextColor(accentArgb)

        val imgUrl = item.imageUrl
        if (!imgUrl.isNullOrBlank()) {
            holder.photoView.visibility = View.VISIBLE
            holder.photoView.load(imgUrl) {
                crossfade(true)
                listener(
                    onError = { _, _ ->
                        holder.photoView.visibility = View.INVISIBLE
                    }
                )
            }
        } else {
            holder.photoView.setImageDrawable(null)
            holder.photoView.visibility = View.INVISIBLE
        }
    }

    class NewsCardHolder(
        val root: FrameLayout,
        val photoView: ImageView,
        val categoryBadge: TextView,
        val counterBadge: TextView,
        val metaText: TextView,
        val headlineText: TextView,
    ) : RecyclerView.ViewHolder(root)
}
