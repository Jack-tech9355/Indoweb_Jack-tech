package com.example.ui.scripts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserScript
import com.example.ui.browser.ScriptLog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScriptsScreen(
    scripts: List<UserScript>,
    logs: List<ScriptLog>,
    currentWebUrl: String,
    onToggleScript: (String, Boolean) -> Unit,
    onEditScript: (UserScript) -> Unit,
    onDeleteScript: (String) -> Unit,
    onAddNewScript: () -> Unit,
    onImportScript: (UserScript) -> Unit,
    onResetDefaults: () -> Unit,
    onRunScriptNow: (UserScript) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var mainTab by remember { mutableIntStateOf(0) } // 0: Scripts, 1: Execution Logs
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Active", "Installed", "Built-In"
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var isImportDialogOpen by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tampermonkey Scripts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Export JSON Button
                    IconButton(
                        onClick = {
                            val jsonArray = JSONArray()
                            scripts.forEach { s ->
                                val obj = JSONObject().apply {
                                    put("name", s.name)
                                    put("description", s.description)
                                    put("urlPattern", s.urlPattern)
                                    put("scriptCode", s.scriptCode)
                                    put("runAt", s.runAt)
                                    put("author", s.author)
                                    put("version", s.version)
                                    put("isEnabled", s.isEnabled)
                                }
                                jsonArray.put(obj)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Indoweb Scripts", jsonArray.toString(2)))
                            Toast.makeText(context, "Exported ${scripts.size} scripts to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Export Scripts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Import Button
                    IconButton(onClick = { isImportDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Import Script",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = mainTab, modifier = Modifier.fillMaxWidth()) {
                Tab(
                    selected = mainTab == 0,
                    onClick = { mainTab = 0 },
                    text = { Text("Scripts (${scripts.size})") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = mainTab == 1,
                    onClick = { mainTab = 1 },
                    text = { Text("Console Logs (${logs.size})") },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mainTab == 0) {
                // Filters & Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Active", "Installed", "Built-In").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter, fontSize = 11.sp) }
                            )
                        }
                    }

                    TextButton(onClick = onResetDefaults) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp)
                    }
                }

                val filteredScripts = scripts.filter {
                    when (selectedFilter) {
                        "Active" -> it.isEnabled
                        "Installed" -> !it.isBuiltIn
                        "Built-In" -> it.isBuiltIn
                        else -> true
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (filteredScripts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No scripts found.\nClick any .user.js link or tap '+ New Script' to install.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredScripts, key = { it.id }) { script ->
                                ScriptItemCard(
                                    script = script,
                                    currentWebUrl = currentWebUrl,
                                    onToggle = { onToggleScript(script.id, it) },
                                    onEdit = { onEditScript(script) },
                                    onDelete = { onDeleteScript(script.id) },
                                    onRunNow = { onRunScriptNow(script) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(70.dp)) }
                        }
                    }

                    // Floating Add Button
                    ExtendedFloatingActionButton(
                        onClick = onAddNewScript,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("New Script") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 12.dp, end = 4.dp)
                            .testTag("add_script_fab")
                    )
                }
            } else {
                // Live Execution Logs
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No script executions logged yet.\nBrowse to any web page matching your scripts.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (log.isSuccess)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = timeFormat.format(Date(log.timestamp)),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = log.scriptName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (log.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                            ) {
                                                Text(
                                                    text = if (log.isSuccess) "PASS" else "FAIL",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = log.message,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Import Script / JSON Dialog
    if (isImportDialogOpen) {
        AlertDialog(
            onDismissRequest = { isImportDialogOpen = false },
            title = { Text("Import UserScript / JSON") },
            text = {
                Column {
                    Text(
                        text = "Paste a GreasyFork script (with // ==UserScript== header) or exported JSON array.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("// ==UserScript==\n// @name My Script\n...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = importInputText.trim()
                        if (input.isNotBlank()) {
                            if (input.startsWith("[") && input.endsWith("]")) {
                                try {
                                    val array = JSONArray(input)
                                    for (i in 0 until array.length()) {
                                        val obj = array.getJSONObject(i)
                                        val imported = UserScript(
                                            name = obj.getString("name"),
                                            description = obj.optString("description", ""),
                                            urlPattern = obj.optString("urlPattern", "*"),
                                            scriptCode = obj.getString("scriptCode"),
                                            runAt = obj.optString("runAt", "document_end"),
                                            author = obj.optString("author", "User"),
                                            version = obj.optString("version", "1.0"),
                                            isEnabled = obj.optBoolean("isEnabled", true),
                                            isBuiltIn = false
                                        )
                                        onImportScript(imported)
                                    }
                                    Toast.makeText(context, "Imported ${array.length()} scripts!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid JSON: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val parsed = UserScriptParser.parse(input)
                                onImportScript(parsed)
                                Toast.makeText(context, "Imported '${parsed.name}'", Toast.LENGTH_SHORT).show()
                            }
                            isImportDialogOpen = false
                            importInputText = ""
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { isImportDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ScriptItemCard(
    script: UserScript,
    currentWebUrl: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val matchesCurrent = currentWebUrl.isNotBlank() && script.matchesUrl(currentWebUrl)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("script_card_${script.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = script.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (script.isBuiltIn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Built-in",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (script.description.isNotBlank()) {
                        Text(
                            text = script.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Badges row: Match pattern and RunAt stage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "Match: ${script.urlPattern.take(24)}${if (script.urlPattern.length > 24) "..." else ""}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (script.runAt == "document_start")
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (script.runAt == "document_start") "start" else "end",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (script.runAt == "document_start")
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (matchesCurrent) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Matches page",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Switch Toggle
                Switch(
                    checked = script.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("script_toggle_${script.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Code viewer toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(4.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide Code" else "View Code",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Run Now on current page button
                    TextButton(onClick = onRunNow) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Run", fontSize = 11.sp)
                    }

                    // Edit
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Code Preview
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = script.scriptCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
