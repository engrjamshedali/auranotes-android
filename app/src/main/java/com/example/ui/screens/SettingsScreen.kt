package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AuraUserPreferences
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleSpringClickable
import com.example.ui.theme.getAccentColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AuraUserPreferences,
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
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    "AuraNotes v1.0 · Apple Liquid Glass Design System",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Appearance & Materials Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Appearance & Materials", style = MaterialTheme.typography.titleMedium)
                            }

                            // Apple Segmented Control for Theme Mode
                            Text("Theme Appearance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "System").forEach { (mode, label) ->
                                        val selected = preferences.themeMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(9.dp))
                                                .appleSpringClickable(
                                                    pressedScale = 0.95f,
                                                    enableHaptics = true,
                                                    onClick = { onSetThemeMode(mode) }
                                                )
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Accent Color Palette
                            Text("Liquid Glass System Tint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val accentNames = listOf("Liquid Blue", "Indigo", "Mint", "Rose", "Solar")
                                accentNames.forEachIndexed { index, name ->
                                    val (color, glow) = getAccentColors(index)
                                    val isSelected = preferences.accentIndex == index
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = if (isSelected) {
                                                BorderStroke(2.2.dp, MaterialTheme.colorScheme.onSurface)
                                            } else {
                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(color, glow)
                                                    ),
                                                    CircleShape
                                                )
                                                .appleSpringClickable(
                                                    pressedScale = 0.9f,
                                                    enableHaptics = true,
                                                    onClick = { onSetAccentIndex(index) }
                                                )
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = name,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Accessibility & Fluid Motion Controls (§14)
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Text("Accessibility & Fluid Motion", style = MaterialTheme.typography.titleMedium)
                            }

                            // Reduced Motion
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Reduce Motion", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Replace physical spring slides with gentle cross-fades",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preferences.reducedMotion,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetReducedMotion(it)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                            // Reduced Transparency
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Reduce Transparency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Make frosted glass surfaces solid and opaque for maximum legibility",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preferences.reducedTransparency,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetReducedTransparency(it)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                            // Increase Contrast
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Increase Contrast", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Sharpen surface borders and boost foreground text contrast",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preferences.highContrast,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetHighContrast(it)
                                    }
                                )
                            }
                        }
                    }
                }

                // Default Editor Behavior Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Creation Defaults", style = MaterialTheme.typography.titleMedium)
                            }

                            Text("Default Document Format:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("RICH_TEXT" to "Rich Text", "CODE" to "Code Studio", "CHECKLIST" to "Checklist").forEach { (type, label) ->
                                    FilterChip(
                                        selected = preferences.defaultNoteType == type,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSetDefaultNoteType(type)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Storage & Vault Dashboard
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(10.dp))
                                Text("Local Vault Telemetry", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Active Notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$noteCount", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column {
                                    Text("Vault Media", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$attachmentCount", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column {
                                    Text("Archives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$backupCount", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // Privacy & Offline Guarantee Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF30D158))
                                Spacer(Modifier.width(10.dp))
                                Text("On-Device Privacy Guarantee", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "AuraNotes stores all notes, code files, media attachments, and backup archives strictly on your device's private storage. No accounts, tracking, or cloud servers are required.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Author & Credits Card
                item {
                    AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Author & Developer", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Jamshed Ali >>jamshedali.me",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "AuraNix Technologies · Premium Offline Notes, Media Vault & Universal Code IDE",
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
}
