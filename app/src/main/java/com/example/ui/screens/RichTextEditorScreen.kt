package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AttachmentEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.ui.components.AudioRecorderDialog
import com.example.ui.components.AuraGlassCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTextEditorScreen(
    note: NoteEntity,
    categories: List<CategoryEntity>,
    attachments: List<AttachmentEntity>,
    onSaveNote: (NoteEntity) -> Unit,
    onBack: () -> Unit,
    onAddAttachmentUri: (Uri, String?, String?) -> Unit,
    onAddAudioFile: (File, Long) -> Unit,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var contentValue by remember { mutableStateOf(TextFieldValue(note.content, TextRange(note.content.length))) }
    var selectedCategoryId by remember { mutableStateOf(note.categoryId) }
    var tags by remember { mutableStateOf(note.tags) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    var isFavorite by remember { mutableStateOf(note.isFavorite) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    // Undo / Redo history stacks
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    // Audio player state
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

    // Auto-save debouncer
    LaunchedEffect(title, contentValue.text, selectedCategoryId, tags, isPinned, isFavorite) {
        val updated = note.copy(
            title = title,
            content = contentValue.text,
            categoryId = selectedCategoryId,
            tags = tags,
            isPinned = isPinned,
            isFavorite = isFavorite,
            updatedAt = System.currentTimeMillis()
        )
        onSaveNote(updated)
    }

    // Android Photo Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            onAddAttachmentUri(it, "image_${System.currentTimeMillis()}.jpg", mime)
        }
    }

    // Android Document Picker
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

    // Helper to format or insert markdown snippets
    fun insertMarkdown(prefix: String, suffix: String = "") {
        val selection = contentValue.selection
        val text = contentValue.text
        undoStack.add(text)
        redoStack.clear()

        val selectedText = if (selection.start != selection.end) {
            text.substring(selection.start, selection.end)
        } else ""

        val newText = text.substring(0, selection.start) +
                prefix + selectedText + suffix +
                text.substring(selection.end)

        val newCursorPos = selection.start + prefix.length + selectedText.length + suffix.length
        contentValue = TextFieldValue(newText, TextRange(newCursorPos))
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeLast()
            redoStack.add(contentValue.text)
            contentValue = TextFieldValue(prev, TextRange(prev.length))
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.add(contentValue.text)
            contentValue = TextFieldValue(next, TextRange(next.length))
        }
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
                    setOnCompletionListener {
                        currentPlayingPath = null
                    }
                    start()
                }
                currentPlayingPath = path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val cat = categories.find { it.id == selectedCategoryId }
                    Text(
                        cat?.name ?: "Editor",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showCategoryDialog = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Category")
                    }
                }
            )
        },
        bottomBar = {
            // Rich-Text Toolbar & Attachment trigger bar
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // Find and replace bar (if toggled)
                    AnimatedVisibility(visible = showFindReplace) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = findText,
                                onValueChange = { findText = it },
                                placeholder = { Text("Find...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                placeholder = { Text("Replace...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (findText.isNotEmpty()) {
                                        undoStack.add(contentValue.text)
                                        val replaced = contentValue.text.replace(findText, replaceText)
                                        contentValue = TextFieldValue(replaced, TextRange(replaced.length))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("All", fontSize = 12.sp)
                            }
                        }
                    }

                    // Formatting Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { insertMarkdown("# ", "") }) {
                            Text("H1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { insertMarkdown("## ", "") }) {
                            Text("H2", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { insertMarkdown("**", "**") }) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                        }
                        IconButton(onClick = { insertMarkdown("*", "*") }) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                        }
                        IconButton(onClick = { insertMarkdown("~~", "~~") }) {
                            Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough")
                        }
                        IconButton(onClick = { insertMarkdown("- [ ] ", "") }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Task Checkbox")
                        }
                        IconButton(onClick = { insertMarkdown("- ", "") }) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List")
                        }
                        IconButton(onClick = { insertMarkdown("1. ", "") }) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List")
                        }
                        IconButton(onClick = { insertMarkdown("> ", "") }) {
                            Icon(Icons.Default.FormatQuote, contentDescription = "Quote")
                        }
                        IconButton(onClick = { insertMarkdown("`", "`") }) {
                            Icon(Icons.Default.Code, contentDescription = "Inline Code")
                        }
                        IconButton(onClick = { insertMarkdown("\n```\n", "\n```\n") }) {
                            Text("```", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            insertMarkdown("\n| Header 1 | Header 2 |\n|---|---|\n| Cell 1 | Cell 2 |\n")
                        }) {
                            Icon(Icons.Default.TableChart, contentDescription = "Table")
                        }
                        IconButton(onClick = { insertMarkdown("\n---\n") }) {
                            Icon(Icons.Default.HorizontalRule, contentDescription = "Divider")
                        }
                        IconButton(onClick = { insertMarkdown("[", "](https://)") }) {
                            Icon(Icons.Default.Link, contentDescription = "Link")
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                        // Media and attachment actions
                        IconButton(onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { showAudioRecorder = true }) {
                            Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = MaterialTheme.colorScheme.tertiary)
                        }
                        IconButton(onClick = {
                            docPicker.launch(arrayOf("*/*"))
                        }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showFindReplace = !showFindReplace }) {
                            Icon(Icons.Default.FindReplace, contentDescription = "Find & Replace")
                        }
                    }

                    // Word count & Status row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val words = remember(contentValue.text) {
                            contentValue.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        }
                        val chars = contentValue.text.length
                        Text(
                            text = "$words words · $chars characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Auto-saved offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Note Title
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        "Title",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Tags input row
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                placeholder = { Text("Tags (comma separated e.g. project, research, idea)") },
                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Attachments Carousel
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Attachments (${attachments.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(attachments, key = { it.id }) { att ->
                        AuraGlassCard(
                            modifier = Modifier
                                .width(180.dp)
                                .height(130.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when {
                                            att.mimeType.startsWith("image/") -> Icons.Default.Image
                                            att.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    IconButton(
                                        onClick = { onDeleteAttachment(att.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (att.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = File(att.localPath),
                                        contentDescription = att.fileName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(55.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else if (att.mimeType.startsWith("audio/")) {
                                    val isThisPlaying = currentPlayingPath == att.localPath && mediaPlayer?.isPlaying == true
                                    FilledTonalButton(
                                        onClick = { playAudio(att.localPath) },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isThisPlaying) "Playing" else "Play Voice")
                                    }
                                } else {
                                    Text(
                                        text = "${att.fileSizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = att.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main Content Area
            TextField(
                value = contentValue,
                onValueChange = {
                    undoStack.add(contentValue.text)
                    redoStack.clear()
                    contentValue = it
                },
                placeholder = {
                    Text(
                        "Start typing your note...\n\nSupports full Markdown formatting, tables, task lists, code blocks, and media attachments.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
            )
        }
    }

    // Category Selector Dialog
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedCategoryId == cat.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategoryId = cat.id
                                    showCategoryDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val catColor = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Voice Recorder Modal
    if (showAudioRecorder) {
        AudioRecorderDialog(
            onDismiss = { showAudioRecorder = false },
            onAudioRecorded = { file, durationMs ->
                showAudioRecorder = false
                onAddAudioFile(file, durationMs)
            }
        )
    }
}
