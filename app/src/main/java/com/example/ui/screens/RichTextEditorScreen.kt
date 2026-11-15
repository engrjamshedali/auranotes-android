package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.AttachmentEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.NoteEntity
import com.example.ui.components.AudioRecorderDialog
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuButton
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraNeuTextField
import com.example.ui.components.neuInset
import com.example.ui.components.neuRaised
import com.example.ui.theme.LocalAuraNeumorphicPalette
import kotlinx.coroutines.delay
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
    val neu = LocalAuraNeumorphicPalette.current

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

    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isSavingLocal by remember { mutableStateOf(false) }
    var hasInitialized by remember { mutableStateOf(false) }

    fun buildUpdatedNote(): NoteEntity = note.copy(
        title = title,
        content = contentValue.text,
        categoryId = selectedCategoryId,
        tags = tags,
        isPinned = isPinned,
        isFavorite = isFavorite,
        updatedAt = System.currentTimeMillis()
    )

    DisposableEffect(Unit) {
        onDispose {
            onSaveNote(buildUpdatedNote())
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(title, contentValue.text, selectedCategoryId, tags, isPinned, isFavorite) {
        if (!hasInitialized) {
            hasInitialized = true
            return@LaunchedEffect
        }
        isSavingLocal = true
        delay(380L)
        onSaveNote(buildUpdatedNote())
        isSavingLocal = false
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            onAddAttachmentUri(it, "image_${System.currentTimeMillis()}.jpg", mime)
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
                    setOnCompletionListener { currentPlayingPath = null }
                    start()
                }
                currentPlayingPath = path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuraNeuIconButton(
                        onClick = {
                            onSaveNote(buildUpdatedNote())
                            onBack()
                        },
                        size = 42.dp,
                        cornerRadius = 13.dp
                    ) {
                        AuraIcon(AuraIcons.ArrowLeft, contentDescription = "Back", size = AuraIconSize.Toolbar)
                    }

                    val cat = categories.find { it.id == selectedCategoryId }
                    Box(
                        modifier = Modifier
                            .neuRaised(
                                cornerRadius = 12.dp,
                                backgroundColor = neu.surface,
                                shadowOffset = 3.dp,
                                blurRadius = 6.dp
                            )
                            .clickable { showCategoryDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuraIcon(AuraIcons.Folder, contentDescription = "Category", size = AuraIconSize.Small, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                cat?.name ?: "Note",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    AuraNeuIconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty(),
                        size = 38.dp,
                        cornerRadius = 11.dp
                    ) {
                        AuraIcon(
                            AuraIcons.Undo2,
                            contentDescription = "Undo",
                            size = AuraIconSize.Button,
                            tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    AuraNeuIconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty(),
                        size = 38.dp,
                        cornerRadius = 11.dp
                    ) {
                        AuraIcon(
                            AuraIcons.Redo2,
                            contentDescription = "Redo",
                            size = AuraIconSize.Button,
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    AuraNeuIconButton(
                        onClick = { isPinned = !isPinned },
                        size = 38.dp,
                        cornerRadius = 11.dp,
                        isActive = isPinned
                    ) {
                        AuraIcon(AuraIcons.Pin, contentDescription = "Pin", size = AuraIconSize.Button)
                    }
                    AuraNeuIconButton(
                        onClick = { isFavorite = !isFavorite },
                        size = 38.dp,
                        cornerRadius = 11.dp,
                        isActive = isFavorite,
                        tint = if (isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        AuraIcon(AuraIcons.Heart, contentDescription = "Favorite", size = AuraIconSize.Button)
                    }
                }
            }
        },
        bottomBar = {
            // Floating Neumorphic Formatting Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .neuRaised(
                        cornerRadius = 24.dp,
                        backgroundColor = neu.surface,
                        shadowOffset = 6.dp,
                        blurRadius = 12.dp
                    )
                    .padding(vertical = 6.dp)
            ) {
                AnimatedVisibility(visible = showFindReplace) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuraNeuTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            placeholder = "Find...",
                            modifier = Modifier.weight(1f)
                        )
                        AuraNeuTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = "Replace...",
                            modifier = Modifier.weight(1f)
                        )
                        AuraNeuButton(
                            onClick = {
                                if (findText.isNotEmpty()) {
                                    undoStack.add(contentValue.text)
                                    val replaced = contentValue.text.replace(findText, replaceText)
                                    contentValue = TextFieldValue(replaced, TextRange(replaced.length))
                                }
                            },
                            isAccent = true,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("All", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        AuraIcon(AuraIcons.Heading1, contentDescription = "Heading 1", size = AuraIconSize.Toolbar, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { insertMarkdown("## ", "") }) {
                        AuraIcon(AuraIcons.Heading2, contentDescription = "Heading 2", size = AuraIconSize.Toolbar, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { insertMarkdown("**", "**") }) {
                        AuraIcon(AuraIcons.Bold, contentDescription = "Bold", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("*", "*") }) {
                        AuraIcon(AuraIcons.Italic, contentDescription = "Italic", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("~~", "~~") }) {
                        AuraIcon(AuraIcons.Strikethrough, contentDescription = "Strikethrough", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("- [ ] ", "") }) {
                        AuraIcon(AuraIcons.ListChecks, contentDescription = "Task Checkbox", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("- ", "") }) {
                        AuraIcon(AuraIcons.ListView, contentDescription = "Bullet List", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("1. ", "") }) {
                        AuraIcon(AuraIcons.ListOrdered, contentDescription = "Numbered List", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("> ", "") }) {
                        AuraIcon(AuraIcons.Quote, contentDescription = "Quote", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("`", "`") }) {
                        AuraIcon(AuraIcons.Code2, contentDescription = "Inline Code", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("\n```\n", "\n```\n") }) {
                        AuraIcon(AuraIcons.FileCode2, contentDescription = "Code Block", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = {
                        insertMarkdown("\n| Header 1 | Header 2 |\n|---|---|\n| Cell 1 | Cell 2 |\n")
                    }) {
                        AuraIcon(AuraIcons.Table2, contentDescription = "Table", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("\n---\n") }) {
                        AuraIcon(AuraIcons.Minus, contentDescription = "Divider", size = AuraIconSize.Toolbar)
                    }
                    IconButton(onClick = { insertMarkdown("[", "](https://)") }) {
                        AuraIcon(AuraIcons.Link2, contentDescription = "Link", size = AuraIconSize.Toolbar)
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                    IconButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        AuraIcon(AuraIcons.ImagePlus, contentDescription = "Add Photo", size = AuraIconSize.Toolbar, tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { showAudioRecorder = true }) {
                        AuraIcon(AuraIcons.Mic, contentDescription = "Record Voice Note", size = AuraIconSize.Toolbar, tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = { docPicker.launch(arrayOf("*/*")) }) {
                        AuraIcon(AuraIcons.Paperclip, contentDescription = "Attach Document", size = AuraIconSize.Toolbar, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showFindReplace = !showFindReplace }) {
                        AuraIcon(
                            AuraIcons.SearchCode,
                            contentDescription = "Find & Replace",
                            size = AuraIconSize.Toolbar,
                            tint = if (showFindReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Word count & Saved pill
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isSavingLocal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.6.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Saving locally...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            AuraIcon(
                                AuraIcons.CheckCircle2,
                                contentDescription = null,
                                tint = neu.success,
                                size = 14.dp
                            )
                            Text(
                                text = "Saved locally",
                                style = MaterialTheme.typography.labelSmall,
                                color = neu.success
                            )
                        }
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
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

            // Recessed Tags Field
            AuraNeuTextField(
                value = tags,
                onValueChange = { tags = it },
                placeholder = "Tags (comma separated, e.g. project, idea)",
                leadingIcon = {
                    AuraIcon(AuraIcons.Tag, contentDescription = null, size = AuraIconSize.Input, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )

            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Attachments (${attachments.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(attachments, key = { it.id }) { att ->
                        AuraGlassCard(
                            modifier = Modifier
                                .width(180.dp)
                                .height(132.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AuraIcon(
                                        when {
                                            att.mimeType.startsWith("image/") -> AuraIcons.Image
                                            att.mimeType.startsWith("audio/") -> AuraIcons.AudioLines
                                            else -> AuraIcons.FileText
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = AuraIconSize.Button
                                    )

                                    IconButton(
                                        onClick = { onDeleteAttachment(att.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        AuraIcon(AuraIcons.X, contentDescription = "Delete", size = AuraIconSize.Small)
                                    }
                                }

                                if (att.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = File(att.localPath),
                                        contentDescription = att.fileName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else if (att.mimeType.startsWith("audio/")) {
                                    val isThisPlaying = currentPlayingPath == att.localPath && mediaPlayer?.isPlaying == true
                                    AuraNeuButton(
                                        onClick = { playAudio(att.localPath) },
                                        isAccent = isThisPlaying,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AuraIcon(
                                            if (isThisPlaying) AuraIcons.Pause else AuraIcons.Play,
                                            contentDescription = null,
                                            size = AuraIconSize.Small,
                                            tint = if (isThisPlaying) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isThisPlaying) "Playing" else "Play Voice",
                                            fontSize = 12.sp,
                                            color = if (isThisPlaying) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
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

            Spacer(Modifier.height(14.dp))

            // Recessed Writing Surface Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 360.dp)
                    .neuInset(
                        cornerRadius = 20.dp,
                        backgroundColor = neu.surfaceRecessed,
                        shadowOffset = 3.5.dp,
                        blurRadius = 7.dp
                    )
                    .padding(6.dp)
            ) {
                TextField(
                    value = contentValue,
                    onValueChange = {
                        undoStack.add(contentValue.text)
                        redoStack.clear()
                        contentValue = it
                    },
                    placeholder = {
                        Text(
                            "Write in Markdown, checklists, tables, or attach media...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
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
                        .defaultMinSize(minHeight = 340.dp)
                )
            }
        }
    }

    // Neumorphic Category Selector Dialog
    if (showCategoryDialog) {
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .neuRaised(
                        cornerRadius = 26.dp,
                        backgroundColor = neu.surface,
                        shadowOffset = 8.dp,
                        blurRadius = 16.dp
                    )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Select Space",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    categories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.id
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) {
                                        Modifier.neuInset(
                                            cornerRadius = 14.dp,
                                            backgroundColor = neu.surfaceRecessed,
                                            shadowOffset = 2.5.dp,
                                            blurRadius = 5.dp,
                                            focusBorderColor = catColor.copy(alpha = 0.4f)
                                        )
                                    } else {
                                        Modifier.neuRaised(
                                            cornerRadius = 14.dp,
                                            backgroundColor = neu.surface,
                                            shadowOffset = 3.dp,
                                            blurRadius = 6.dp
                                        )
                                    }
                                )
                                .clickable {
                                    selectedCategoryId = cat.id
                                    showCategoryDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCategoryDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

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
