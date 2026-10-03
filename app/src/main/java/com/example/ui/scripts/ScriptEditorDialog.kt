package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserScript

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorDialog(
    initialScript: UserScript? = null,
    onSave: (UserScript) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialScript?.name ?: "") }
    var description by remember { mutableStateOf(initialScript?.description ?: "") }
    var urlPattern by remember { mutableStateOf(initialScript?.urlPattern ?: "*") }
    var runAt by remember { mutableStateOf(initialScript?.runAt ?: "document_end") }
    var scriptCode by remember {
        mutableStateOf(
            initialScript?.scriptCode ?: """
                (function() {
                    // Indoweb UserScript
                    console.log('UserScript running on: ' + window.location.href);
                    
                    // Add your custom DOM modifications or JavaScript logic here
                    
                })();
            """.trimIndent()
        )
    }
    var isEnabled by remember { mutableStateOf(initialScript?.isEnabled ?: true) }
    var isTemplateMenuOpen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (initialScript == null) "New UserScript" else "Edit UserScript",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    errorMessage = "Script name is required"
                                    return@Button
                                }
                                if (scriptCode.isBlank()) {
                                    errorMessage = "Script code cannot be empty"
                                    return@Button
                                }

                                val savedScript = (initialScript ?: UserScript(
                                    name = name.trim(),
                                    scriptCode = scriptCode
                                )).copy(
                                    name = name.trim(),
                                    description = description.trim(),
                                    urlPattern = urlPattern.trim().ifEmpty { "*" },
                                    runAt = runAt,
                                    scriptCode = scriptCode,
                                    isEnabled = isEnabled,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(savedScript)
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("save_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Script Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Script Name *") },
                    placeholder = { Text("e.g. My Website Tweaks") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("script_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // URL Match Pattern
                OutlinedTextField(
                    value = urlPattern,
                    onValueChange = { urlPattern = it },
                    label = { Text("Target URL Pattern") },
                    placeholder = { Text("* for all sites, or wikipedia.org, *github*") },
                    singleLine = true,
                    supportingText = {
                        Text("Use * for all sites, or domain name like wikipedia.org")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("script_url_pattern_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Injection Stage (Run-At)
                Text(
                    text = "Execution Stage (Run At):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = runAt == "document_start",
                        onClick = { runAt = "document_start" },
                        label = { Text("Document Start (Early)") }
                    )
                    FilterChip(
                        selected = runAt == "document_end",
                        onClick = { runAt = "document_end" },
                        label = { Text("Document End (Page Loaded)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("What this script does...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Templates and Quick Insert Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "JavaScript Code",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Box {
                        FilledTonalButton(
                            onClick = { isTemplateMenuOpen = true },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DataObject,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Insert Template", fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = isTemplateMenuOpen,
                            onDismissRequest = { isTemplateMenuOpen = false }
                        ) {
                            ScriptTemplates.STARTER_TEMPLATES.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.title, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${template.description} (${template.runAt})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        scriptCode = template.code
                                        runAt = template.runAt
                                        if (name.isBlank()) name = template.title
                                        if (description.isBlank()) description = template.description
                                        isTemplateMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Snippet Chips for Mobile Keyboard Convenience
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val snippets = listOf(
                        "console.log()" to "console.log('Debug:', window.location);",
                        "querySelector" to "document.querySelector('');",
                        "addStyle" to "const s = document.createElement('style'); s.innerHTML = '/* css */'; (document.head||document.documentElement).appendChild(s);",
                        "hideElement" to "document.querySelectorAll('').forEach(e => e.style.display = 'none');",
                        "addEventListener" to "window.addEventListener('scroll', () => {});",
                        "alert" to "alert('Hello from UserScript!');"
                    )

                    snippets.forEach { (label, code) ->
                        AssistChip(
                            onClick = {
                                scriptCode += "\n" + code
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Code Editor TextArea
                OutlinedTextField(
                    value = scriptCode,
                    onValueChange = {
                        scriptCode = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("script_code_editor"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
