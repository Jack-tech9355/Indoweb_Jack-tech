package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.browser.BrowserUiState

@Composable
fun BottomNavBar(
    uiState: BrowserUiState,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleReaderMode: () -> Unit,
    onOpenJsConsole: () -> Unit,
    onOpenScriptStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onOpenSiteShield: () -> Unit = {},
    onSaveMhtml: () -> Unit = {},
    onClearData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = if (uiState.isIncognito) Color(0xFF181820) else MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = onBack,
                enabled = uiState.canGoBack,
                modifier = Modifier.testTag("nav_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = if (uiState.canGoBack)
                        (if (uiState.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }

            // Forward Button
            IconButton(
                onClick = onForward,
                enabled = uiState.canGoForward,
                modifier = Modifier.testTag("nav_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go Forward",
                    tint = if (uiState.canGoForward)
                        (if (uiState.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }

            // Home Button
            IconButton(
                onClick = onHome,
                modifier = Modifier.testTag("nav_home_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (uiState.isIncognito) Color(0xFFA78BFA) else MaterialTheme.colorScheme.primary
                )
            }

            // Tab Switcher Button
            IconButton(
                onClick = onOpenTabSwitcher,
                modifier = Modifier.testTag("nav_tabs_button")
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
                        contentDescription = "Open Tabs",
                        tint = if (uiState.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bookmarks / History Button
            IconButton(
                onClick = onOpenBookmarks,
                modifier = Modifier.testTag("nav_bookmarks_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmarks & History",
                    tint = if (uiState.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }

            // Overflow Menu
            Box {
                IconButton(
                    onClick = { isMenuOpen = true },
                    modifier = Modifier.testTag("nav_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = if (uiState.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false }
                ) {
                    // Bookmark Page
                    DropdownMenuItem(
                        text = {
                            Text(if (uiState.isBookmarked) "Remove Bookmark" else "Bookmark Page")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onToggleBookmark()
                        }
                    )

                    // Desktop Site Toggle
                    DropdownMenuItem(
                        text = {
                            Text(if (uiState.isDesktopMode) "Mobile Site" else "Desktop Site")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = if (uiState.isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onToggleDesktop()
                        }
                    )

                    // Reader Mode Toggle
                    DropdownMenuItem(
                        text = {
                            Text(if (uiState.isReaderMode) "Exit Reader Mode" else "Reader View")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = if (uiState.isReaderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onToggleReaderMode()
                        }
                    )

                    // Site Shield & Privacy
                    DropdownMenuItem(
                        text = { Text("Site Shield & Privacy") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onOpenSiteShield()
                        }
                    )

                    // UserScript Store
                    DropdownMenuItem(
                        text = { Text("UserScript Store") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onOpenScriptStore()
                        }
                    )

                    // Save as Offline Web Archive
                    DropdownMenuItem(
                        text = { Text("Save Offline Archive (.mhtml)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onSaveMhtml()
                        }
                    )

                    // JS Console & Inspector
                    DropdownMenuItem(
                        text = { Text("JS Console & Inspector") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onOpenJsConsole()
                        }
                    )

                    // Find in Page
                    DropdownMenuItem(
                        text = { Text("Find in Page") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FindInPage,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onFindInPage()
                        }
                    )

                    // Share Page
                    DropdownMenuItem(
                        text = { Text("Share Link") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onShare()
                        }
                    )

                    // Settings
                    DropdownMenuItem(
                        text = { Text("Browser Settings") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onOpenSettings()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Clear Cache & Cookies
                    DropdownMenuItem(
                        text = { Text("Clear Browsing Data") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onClearData()
                        }
                    )
                }
            }
        }
    }
}
