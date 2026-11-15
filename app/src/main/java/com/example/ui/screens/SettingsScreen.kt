package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.auth.GoogleUserProfile
import com.example.data.local.AuraUserPreferences
import com.example.data.local.CloudSyncStatus
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconContainer
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuButton
import com.example.ui.components.AuraNeuDivider
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraNeuSegmentedControl
import com.example.ui.components.AuraNeuSwitch
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleSpringClickable
import com.example.ui.components.neuInset
import com.example.ui.components.neuRaised
import com.example.ui.theme.LocalAuraNeumorphicPalette
import com.example.ui.theme.getAccentColors

@Composable
fun SettingsScreen(
    preferences: AuraUserPreferences,
    userProfile: GoogleUserProfile? = null,
    syncStatus: CloudSyncStatus = CloudSyncStatus.AUTH_REQUIRED,
    isSigningIn: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSwitchAccount: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenCloudBackups: () -> Unit = {},
    showDrawerButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
    onSetThemeMode: (String) -> Unit,
    onSetAccentIndex: (Int) -> Unit,
    onSetDefaultNoteType: (String) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onSetReducedTransparency: (Boolean) -> Unit = {},
    onSetHighContrast: (Boolean) -> Unit = {},
    noteCount: Int,
    attachmentCount: Int,
    backupCount: Int
) {
    val neu = LocalAuraNeumorphicPalette.current
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDrawerButton) {
                AuraNeuIconButton(
                    onClick = onOpenDrawer,
                    size = 44.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    AuraIcon(
                        AuraIcons.Menu,
                        contentDescription = "Open Navigation Drawer",
                        size = AuraIconSize.Toolbar
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Version 2.0 · Soft UI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Google Account & Cloud Sync Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AuraIconContainer(
                                        imageVector = AuraIcons.Cloud,
                                        containerSize = 38.dp,
                                        iconSize = AuraIconSize.Toolbar,
                                        cornerRadius = 12.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Google Account & Sync", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = if (userProfile == null) "Offline" else if (preferences.driveBackupEnabled) syncStatus.label else "Paused",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (userProfile != null && preferences.driveBackupEnabled && syncStatus == CloudSyncStatus.SYNCED) {
                                        neu.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (userProfile == null) {
                                Text(
                                    "Sign in with Google to synchronize backups privately into your Google Drive AppData folder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AuraNeuButton(
                                    onClick = onSignInWithGoogle,
                                    enabled = !isSigningIn,
                                    isAccent = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("settings_google_signin_button")
                                ) {
                                    if (isSigningIn) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Signing in...", style = MaterialTheme.typography.labelLarge)
                                    } else {
                                        GoogleLogoMark(modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Continue with Google", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neuInset(
                                            cornerRadius = 16.dp,
                                            backgroundColor = neu.surfaceRecessed,
                                            shadowOffset = 3.dp,
                                            blurRadius = 6.dp
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!userProfile.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = userProfile.photoUrl,
                                            contentDescription = "Google Profile Picture",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        AuraIconContainer(
                                            imageVector = AuraIcons.UserRound,
                                            containerSize = 42.dp,
                                            iconSize = AuraIconSize.Button,
                                            cornerRadius = 21.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userProfile.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = userProfile.email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AuraNeuButton(
                                        onClick = onOpenCloudBackups,
                                        isAccent = false,
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                                    ) {
                                        Text("Drive Backups", style = MaterialTheme.typography.labelMedium)
                                    }
                                    AuraNeuButton(
                                        onClick = onSwitchAccount,
                                        isAccent = false,
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                                    ) {
                                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                                    }
                                    AuraNeuIconButton(
                                        onClick = { showSignOutConfirm = true },
                                        size = 46.dp,
                                        cornerRadius = 14.dp,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("settings_signout_button")
                                    ) {
                                        AuraIcon(
                                            AuraIcons.LogOut,
                                            contentDescription = "Sign Out",
                                            size = AuraIconSize.Button,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Appearance & Accent Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.Palette,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                            }

                            Text(
                                "Theme Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AuraNeuSegmentedControl(
                                options = listOf("LIGHT" to "Light", "DARK" to "Dark", "SYSTEM" to "System"),
                                selectedOption = preferences.themeMode,
                                onSelect = onSetThemeMode
                            )

                            Text(
                                "Accent Color",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val accentNames = listOf("Blue", "Violet", "Mint", "Coral", "Amber")
                                accentNames.forEachIndexed { index, name ->
                                    val (color, glow) = getAccentColors(index)
                                    val isSelected = preferences.accentIndex == index
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.neuInset(
                                                            cornerRadius = 23.dp,
                                                            backgroundColor = neu.surfaceRecessed,
                                                            shadowOffset = 3.dp,
                                                            blurRadius = 6.dp
                                                        )
                                                    } else {
                                                        Modifier.neuRaised(
                                                            cornerRadius = 23.dp,
                                                            backgroundColor = neu.surface,
                                                            shadowOffset = 4.dp,
                                                            blurRadius = 8.dp
                                                        )
                                                    }
                                                )
                                                .appleSpringClickable(
                                                    pressedScale = 0.9f,
                                                    enableHaptics = true,
                                                    onClick = { onSetAccentIndex(index) }
                                                )
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(color, glow)))
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            ) {
                                                if (isSelected) {
                                                    AuraIcon(
                                                        AuraIcons.Check,
                                                        contentDescription = name,
                                                        tint = Color.White,
                                                        size = 16.dp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Accessibility & Motion Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.Accessibility,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Accessibility & Motion", style = MaterialTheme.typography.titleMedium)
                            }

                            SettingSwitchRow(
                                title = "Reduce Motion",
                                subtitle = "Use gentle cross-fades instead of spring slides",
                                checked = preferences.reducedMotion,
                                onCheckedChange = onSetReducedMotion
                            )

                            AuraNeuDivider()

                            SettingSwitchRow(
                                title = "Reduce Transparency",
                                subtitle = "Use solid surfaces across overlays and toolbars",
                                checked = preferences.reducedTransparency,
                                onCheckedChange = onSetReducedTransparency
                            )

                            AuraNeuDivider()

                            SettingSwitchRow(
                                title = "Increase Contrast",
                                subtitle = "Sharpen outlines and boost text contrast",
                                checked = preferences.highContrast,
                                onCheckedChange = onSetHighContrast
                            )
                        }
                    }
                }

                // Creation Defaults Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.FilePenLine,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Creation Defaults", style = MaterialTheme.typography.titleMedium)
                            }

                            Text(
                                "Default Document Format",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AuraNeuSegmentedControl(
                                options = listOf(
                                    "RICH_TEXT" to "Rich Text",
                                    "CODE" to "Code Studio",
                                    "CHECKLIST" to "Checklist"
                                ),
                                selectedOption = preferences.defaultNoteType,
                                onSelect = onSetDefaultNoteType
                            )
                        }
                    }
                }

                // Local Vault Telemetry Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.HardDrive,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Local Storage Summary", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TelemetryWell(label = "Notes", value = "$noteCount", modifier = Modifier.weight(1f))
                                TelemetryWell(label = "Media", value = "$attachmentCount", modifier = Modifier.weight(1f))
                                TelemetryWell(label = "Backups", value = "$backupCount", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // On-Device Privacy Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.ShieldCheck,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = neu.success
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Your notes, code scripts, and media are stored locally first. Optional Google Drive backups are saved strictly inside your private appDataFolder over HTTPS with no third-party tracking.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Developer Profile Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuraIconContainer(
                                    imageVector = AuraIcons.UserRound,
                                    containerSize = 38.dp,
                                    iconSize = AuraIconSize.Toolbar,
                                    cornerRadius = 12.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Developer", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Jamshed Ali · jamshedali.me",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "AuraNix Technologies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AuraScrollEdgeMask(
                modifier = Modifier.align(Alignment.TopCenter),
                fromTop = true,
                height = 10.dp
            )
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = neu.surfaceElevated,
            title = { Text("Sign Out of Google?") },
            text = {
                Text(
                    "Your local notes and media remain intact on this device. Cloud backups will pause until you sign back in.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AuraNeuButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    isAccent = true
                ) {
                    Text("Sign Out", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AuraNeuSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun TelemetryWell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val neu = LocalAuraNeumorphicPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .neuInset(
                cornerRadius = 16.dp,
                backgroundColor = neu.surfaceRecessed,
                shadowOffset = 3.dp,
                blurRadius = 6.dp
            )
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
