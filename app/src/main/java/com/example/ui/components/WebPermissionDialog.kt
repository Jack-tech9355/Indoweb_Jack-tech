package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.browser.WebPermissionPrompt
import java.net.URI

@Composable
fun WebPermissionDialog(
    prompt: WebPermissionPrompt,
    onGrant: (remember: Boolean) -> Unit,
    onDeny: (remember: Boolean) -> Unit
) {
    var rememberDecision by remember { mutableStateOf(true) }

    val (origin, title, message) = when (prompt) {
        is WebPermissionPrompt.Device -> {
            val domain = try { URI(prompt.origin).host ?: prompt.origin } catch (_: Exception) { prompt.origin }
            val permNames = prompt.labels.joinToString(" and ")
            Triple(domain, "$domain requests access", "This site wants permission to access your $permNames.")
        }
        is WebPermissionPrompt.Geolocation -> {
            val domain = try { URI(prompt.origin).host ?: prompt.origin } catch (_: Exception) { prompt.origin }
            Triple(domain, "$domain requests location", "This site wants permission to access your device location.")
        }
    }

    AlertDialog(
        onDismissRequest = { onDeny(false) },
        icon = {
            Icon(
                imageVector = when {
                    prompt is WebPermissionPrompt.Geolocation -> Icons.Default.LocationOn
                    (prompt as? WebPermissionPrompt.Device)?.labels?.contains("Camera") == true -> Icons.Default.CameraAlt
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberDecision,
                        onCheckedChange = { rememberDecision = it },
                        modifier = Modifier.testTag("remember_permission_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remember decision for this site",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGrant(rememberDecision) },
                modifier = Modifier.testTag("allow_permission_button")
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onDeny(rememberDecision) },
                modifier = Modifier.testTag("block_permission_button")
            ) {
                Text("Block")
            }
        }
    )
}
