package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.webagent.WebAgentPhase
import com.example.webagent.WebAgentUiState

@Composable
fun WebAgentCard(
    state: WebAgentUiState,
    onConfirmDownload: () -> Unit,
    onOpenDownloadedFile: () -> Unit,
    onDismiss: () -> Unit,
    compactOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val accent = when (state.phase) {
        WebAgentPhase.AWAITING_CONFIRMATION -> JarvisAmber
        WebAgentPhase.COMPLETED -> JarvisEmerald
        WebAgentPhase.FAILED -> JarvisCrimson
        else -> JarvisCyan
    }

    val icon = when (state.phase) {
        WebAgentPhase.AWAITING_CONFIRMATION -> Icons.Rounded.Security
        WebAgentPhase.COMPLETED -> Icons.Rounded.CheckCircle
        WebAgentPhase.FAILED -> Icons.Rounded.ErrorOutline
        else -> Icons.Rounded.CloudDownload
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("web_agent_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEC0C1320),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compactOverlay) 12.dp else 14.dp,
                vertical = if (compactOverlay) 10.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = state.headerTitle.ifBlank { "WEB AJANI" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accent,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("web_agent_dismiss_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Web ajanını kapat",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = state.statusText,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = Color(0xFFE2E8F0),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (state.phase == WebAgentPhase.SCANNING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisCyan,
                    trackColor = JarvisCyan.copy(alpha = 0.16f),
                )
            } else if (state.phase == WebAgentPhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (state.progressPercent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisCyan,
                    trackColor = JarvisCyan.copy(alpha = 0.16f),
                )
            }

            if (state.phase == WebAgentPhase.AWAITING_CONFIRMATION) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("web_agent_cancel_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Vazgeç", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onConfirmDownload,
                        modifier = Modifier
                            .weight(1.3f)
                            .minimumInteractiveComponentSize()
                            .testTag("web_agent_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisAmber,
                            contentColor = Color(0xFF0A0F18),
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Güvenli İndir", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (state.phase == WebAgentPhase.COMPLETED) {
                val isApk = state.discovered?.isApk == true
                Button(
                    onClick = onOpenDownloadedFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("web_agent_open_file_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisEmerald,
                        contentColor = Color(0xFF06120D),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isApk) "APK Kurulumunu Başlat" else "Dosyayı Aç",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
