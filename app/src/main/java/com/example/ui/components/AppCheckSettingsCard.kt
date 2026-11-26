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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.AppCheckStatusInfo
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AppCheckSettingsCard(
    status: AppCheckStatusInfo,
    onGenerateDebugToken: () -> Unit,
    onSaveDebugToken: (String) -> Unit,
    onCopyDebugToken: (String) -> Unit,
    onResetToRecaptcha: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tokenInput by remember(status.debugToken) { mutableStateOf(status.debugToken) }

    LaunchedEffect(status.debugToken) {
        if (status.debugToken.isNotBlank()) {
            tokenInput = status.debugToken
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Firebase App Check (Yapay Zeka Doğrulaması)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = "Aktif mod: ${status.activeModeLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan,
            )

            Text(
                text = "Telefondaki APK'da 403 App Check hatası alıyorsanız Debug Token oluşturup kopyalayın ve Firebase Console > App Check > Apps > Manage debug tokens bölümüne ekleyin (veya oradan aldığınız token'ı buraya yapıştırın).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (status.isDebugBuild) {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("appcheck_debug_token_input"),
                    singleLine = true,
                    label = { Text("App Check Debug Token (UUID)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onGenerateDebugToken,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("appcheck_generate_token_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Oluştur", style = MaterialTheme.typography.labelMedium)
                    }

                    if (status.debugToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onCopyDebugToken(status.debugToken) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("appcheck_copy_token_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kopyala", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = { onSaveDebugToken(tokenInput) },
                        enabled = tokenInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("appcheck_save_token_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kaydet", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (status.hasSavedDebugToken) {
                    OutlinedButton(
                        onClick = {
                            tokenInput = ""
                            onResetToRecaptcha()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("appcheck_reset_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Token'ı Temizle (reCAPTCHA'ya Dön)")
                    }
                }
            }
        }
    }
}
