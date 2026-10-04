package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.browser.BrowserUiState

@Composable
fun TopUrlBar(
    uiState: BrowserUiState,
    tabCount: Int,
    onUrlChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStopLoading: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onShowSslInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Surface(
        color = if (uiState.isIncognito) Color(0xFF181820) else MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SSL Lock indicator or Incognito Mask
                IconButton(
                    onClick = onShowSslInfo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("ssl_info_button")
                ) {
                    if (uiState.isIncognito) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Private Mode",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (uiState.isSecure) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Connection",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Not Secure",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Interactive URL / Search Box
                OutlinedTextField(
                    value = uiState.inputUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("url_input_field"),
                    placeholder = {
                        Text(
                            text = if (uiState.isIncognito) "Search privately or type URL" else "Search or type URL",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (uiState.isIncognito) Color(0xFF282834) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = if (uiState.isIncognito) Color(0xFF22222D) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = if (uiState.isIncognito) Color(0xFFA78BFA) else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            onNavigate(uiState.inputUrl)
                        }
                    ),
                    trailingIcon = {
                        if (uiState.inputUrl.isNotBlank()) {
                            IconButton(
                                onClick = { onUrlChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Tab Switcher Button with count
                IconButton(
                    onClick = onOpenTabSwitcher,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("tabs_toolbar_button")
                ) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = if (uiState.isIncognito) Color(0xFFA78BFA) else MaterialTheme.colorScheme.primary,
                                contentColor = if (uiState.isIncognito) Color.Black else MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = "$tabCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tab,
                            contentDescription = "Tabs Switcher",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // UserScript Pill / Badge button
                IconButton(
                    onClick = onOpenScripts,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("userscripts_toolbar_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (uiState.injectedScriptsCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "${uiState.injectedScriptsCount}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.injectedScriptsCount > 0)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "UserScripts Manager",
                                tint = if (uiState.injectedScriptsCount > 0)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Reload or Stop Button
                IconButton(
                    onClick = {
                        if (uiState.isLoading) onStopLoading() else onReload()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reload_button")
                ) {
                    if (uiState.isLoading) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop Loading",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Page",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Smooth Horizontal Progress Bar
            AnimatedVisibility(visible = uiState.isLoading) {
                LinearProgressIndicator(
                    progress = { uiState.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (uiState.isIncognito) Color(0xFFA78BFA) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
