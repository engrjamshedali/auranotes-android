package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.AttachmentEntity
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraScrollEdgeMask
import com.example.ui.components.appleSpringClickable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(
    attachments: List<AttachmentEntity>,
    onAddAttachmentUri: (Uri, String?, String?) -> Unit,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("ALL") } // ALL, IMAGES, AUDIO, DOCS
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

    val totalBytes = remember(attachments) {
        attachments.sumOf { it.fileSizeBytes }
    }

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
        // Vault Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Media Vault",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    "Private local storage · ${attachments.size} files · ${(totalBytes / (1024 * 1024.0)).let { String.format(Locale.US, "%.2f MB", it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .appleSpringClickable(
                            pressedScale = 0.92f,
                            enableHaptics = true,
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Import Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .appleSpringClickable(
                            pressedScale = 0.92f,
                            enableHaptics = true,
                            onClick = {
                                docPicker.launch(arrayOf("*/*"))
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Import Document",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Apple Segmented Filter Pills Row
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "ALL" to "All Files (${attachments.size})",
                "IMAGES" to "Photos (${attachments.count { it.mimeType.startsWith("image/") }})",
                "AUDIO" to "Voice Notes (${attachments.count { it.mimeType.startsWith("audio/") }})",
                "DOCS" to "Documents (${attachments.count { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("audio/") }})"
            )

            items(tabs, key = { it.first }) { (key, label) ->
                val isSelected = selectedTab == key
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .appleSpringClickable(
                            pressedScale = 0.94f,
                            enableHaptics = true,
                            onClick = { selectedTab = key }
                        )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Vault list with Scroll Edge Mask
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                        Icon(
                            Icons.Default.PermMedia,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            "Your Media Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Import photos, PDFs, or record voice notes in any document to keep them safe offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { att ->
                        AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail / Icon
                                if (att.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = File(att.localPath),
                                        contentDescription = att.fileName,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(60.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (att.mimeType.startsWith("audio/")) Icons.Default.Audiotrack else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                // File Info
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                // Actions
                                Row {
                                    if (att.mimeType.startsWith("audio/")) {
                                        val isPlaying = currentPlayingPath == att.localPath && mediaPlayer?.isPlaying == true
                                        IconButton(onClick = { playAudio(att.localPath) }) {
                                            Icon(
                                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = "Play Audio",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    IconButton(onClick = { shareFile(att) }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share File")
                                    }
                                    IconButton(onClick = { onDeleteAttachment(att.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete File",
                                            tint = MaterialTheme.colorScheme.error
                                        )
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
