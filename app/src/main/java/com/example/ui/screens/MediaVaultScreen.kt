package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.AttachmentEntity
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconContainer
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleSpringClickable
import com.example.ui.components.neuRaised
import com.example.ui.theme.LocalAuraNeumorphicPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaVaultScreen(
    attachments: List<AttachmentEntity>,
    showDrawerButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
    onAddAttachmentUri: (Uri, String?, String?) -> Unit,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    val neu = LocalAuraNeumorphicPalette.current

    var selectedTab by remember { mutableStateOf("ALL") }
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            onAddAttachmentUri(it, "vault_img_${System.currentTimeMillis()}.jpg", mime)
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val name = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex >= 0) c.getString(nameIndex) else null
            }
            val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
            onAddAttachmentUri(it, name, mime)
        }
    }

    val filtered = remember(attachments, selectedTab) {
        when (selectedTab) {
            "IMAGES" -> attachments.filter { it.mimeType.startsWith("image/") }
            "AUDIO" -> attachments.filter { it.mimeType.startsWith("audio/") }
            "DOCS" -> attachments.filter { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("audio/") }
            else -> attachments
        }
    }

    val totalBytes = remember(attachments) { attachments.sumOf { it.fileSizeBytes } }

    fun playAudio(path: String) {
        try {
            if (currentPlayingPath == path && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                currentPlayingPath = null
            } else {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener { currentPlayingPath = null }
                    start()
                }
                currentPlayingPath = path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFile(att: AttachmentEntity) {
        try {
            val file = File(att.localPath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = att.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${att.fileName}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                        "Media Vault",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "${attachments.size} files · ${(totalBytes / (1024 * 1024.0)).let { String.format(Locale.US, "%.2f MB", it) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AuraNeuIconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    size = 44.dp,
                    cornerRadius = 14.dp,
                    isAccent = true
                ) {
                    AuraIcon(
                        AuraIcons.ImagePlus,
                        contentDescription = "Import Photo",
                        size = AuraIconSize.Toolbar,
                        tint = Color.White
                    )
                }

                AuraNeuIconButton(
                    onClick = { docPicker.launch(arrayOf("*/*")) },
                    size = 44.dp,
                    cornerRadius = 14.dp
                ) {
                    AuraIcon(
                        AuraIcons.Paperclip,
                        contentDescription = "Import Document",
                        size = AuraIconSize.Toolbar
                    )
                }
            }
        }

        // Neumorphic Filter Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tabs = listOf(
                "ALL" to "All (${attachments.size})",
                "IMAGES" to "Photos (${attachments.count { it.mimeType.startsWith("image/") }})",
                "AUDIO" to "Voice (${attachments.count { it.mimeType.startsWith("audio/") }})",
                "DOCS" to "Docs (${attachments.count { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("audio/") }})"
            )

            items(tabs, key = { it.first }) { (key, label) ->
                val isSelected = selectedTab == key
                Box(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier.neuRaised(
                                    cornerRadius = 14.dp,
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    shadowOffset = 4.dp,
                                    blurRadius = 8.dp
                                )
                            } else {
                                Modifier.neuRaised(
                                    cornerRadius = 14.dp,
                                    backgroundColor = neu.surface,
                                    shadowOffset = 3.5.dp,
                                    blurRadius = 7.dp
                                )
                            }
                        )
                        .appleSpringClickable(
                            pressedScale = 0.94f,
                            enableHaptics = true,
                            onClick = { selectedTab = key }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        AuraIconContainer(
                            imageVector = AuraIcons.Images,
                            containerSize = 84.dp,
                            iconSize = 36.dp,
                            cornerRadius = 24.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No Media Files",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Import photos, documents, or voice memos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.id }) { att ->
                        AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (att.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = File(att.localPath),
                                        contentDescription = att.fileName,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    AuraIconContainer(
                                        imageVector = if (att.mimeType.startsWith("audio/")) AuraIcons.AudioLines else AuraIcons.FileText,
                                        containerSize = 60.dp,
                                        iconSize = AuraIconSize.Card,
                                        cornerRadius = 14.dp,
                                        recessed = true
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${att.mimeType} · ${(att.fileSizeBytes / 1024.0).let { String.format(Locale.US, "%.1f KB", it) }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(att.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (att.mimeType.startsWith("audio/")) {
                                        val isPlaying = currentPlayingPath == att.localPath && mediaPlayer?.isPlaying == true
                                        AuraNeuIconButton(
                                            onClick = { playAudio(att.localPath) },
                                            size = 38.dp,
                                            cornerRadius = 11.dp,
                                            isActive = isPlaying,
                                            tint = MaterialTheme.colorScheme.primary
                                        ) {
                                            AuraIcon(
                                                if (isPlaying) AuraIcons.Square else AuraIcons.Play,
                                                contentDescription = "Play Audio",
                                                size = AuraIconSize.Button
                                            )
                                        }
                                    }
                                    AuraNeuIconButton(
                                        onClick = { shareFile(att) },
                                        size = 38.dp,
                                        cornerRadius = 11.dp
                                    ) {
                                        AuraIcon(AuraIcons.Share2, contentDescription = "Share File", size = AuraIconSize.Button)
                                    }
                                    AuraNeuIconButton(
                                        onClick = { onDeleteAttachment(att.id) },
                                        size = 38.dp,
                                        cornerRadius = 11.dp,
                                        tint = MaterialTheme.colorScheme.error
                                    ) {
                                        AuraIcon(AuraIcons.Trash2, contentDescription = "Delete File", size = AuraIconSize.Button)
                                    }
                                }
                            }
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
