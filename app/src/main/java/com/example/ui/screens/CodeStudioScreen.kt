package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.domain.CodeExecutionEngine
import com.example.domain.ExecutionResult
import com.example.ui.components.AuraAnimatedNavIcon
import com.example.ui.components.AuraIcon
import com.example.ui.components.AuraIconSize
import com.example.ui.components.AuraIcons
import com.example.ui.components.AuraNeuIconButton
import com.example.ui.components.AuraNeuTextField
import com.example.ui.components.QuickSymbolBar
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.components.WebSandboxView
import com.example.ui.components.appleSpringClickable
import com.example.ui.components.neuInset
import com.example.ui.components.neuRaised
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.CodeLineNumber
import com.example.ui.theme.LocalAuraNeumorphicPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CodeStudioScreen(
    note: NoteEntity,
    onSaveNote: (NoteEntity) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val neu = LocalAuraNeumorphicPalette.current

    var title by remember { mutableStateOf(note.title) }
    var codeValue by remember { mutableStateOf(TextFieldValue(note.content, TextRange(note.content.length))) }
    var selectedLanguage by remember { mutableStateOf(note.language) }
    var isRunning by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf<ExecutionResult?>(null) }
    val webConsoleLogs = remember { mutableStateListOf<String>() }

    var selectedTab by remember { mutableStateOf(0) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var isSavingLocal by remember { mutableStateOf(false) }
    var hasInitialized by remember { mutableStateOf(false) }

    fun buildUpdatedCodeNote(): NoteEntity = note.copy(
        title = title,
        content = codeValue.text,
        language = selectedLanguage,
        noteType = NoteType.CODE.name,
        updatedAt = System.currentTimeMillis()
    )

    val languages = listOf(
        "python" to "Python",
        "javascript" to "JavaScript",
        "html" to "HTML / CSS",
        "sql" to "SQL (SQLite)",
        "kotlin" to "Kotlin",
        "java" to "Java",
        "cpp" to "C / C++",
        "rust" to "Rust",
        "go" to "Go",
        "bash" to "Bash Shell",
        "swift" to "Swift",
        "dart" to "Dart",
        "json" to "JSON",
        "markdown" to "Markdown",
        "ruby" to "Ruby",
        "csharp" to "C#"
    )

    DisposableEffect(Unit) {
        onDispose {
            onSaveNote(buildUpdatedCodeNote())
        }
    }

    LaunchedEffect(title, codeValue.text, selectedLanguage) {
        if (!hasInitialized) {
            hasInitialized = true
            return@LaunchedEffect
        }
        isSavingLocal = true
        delay(380L)
        onSaveNote(buildUpdatedCodeNote())
        isSavingLocal = false
    }

    fun runCode() {
        isRunning = true
        webConsoleLogs.clear()
        coroutineScope.launch {
            val res = CodeExecutionEngine.executeCode(selectedLanguage, codeValue.text)
            executionResult = res
            isRunning = false
            selectedTab = if (selectedLanguage.lowercase() in listOf("html", "javascript", "js", "htm")) 2 else 1
        }
    }

    fun insertSymbol(symbol: String) {
        val sel = codeValue.selection
        val text = codeValue.text
        val newText = text.substring(0, sel.start) + symbol + text.substring(sel.end)
        codeValue = TextFieldValue(newText, TextRange(sel.start + symbol.length))
    }

    fun applyTemplate(templateCode: String) {
        codeValue = TextFieldValue(templateCode, TextRange(templateCode.length))
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
                            onSaveNote(buildUpdatedCodeNote())
                            onBack()
                        },
                        size = 42.dp,
                        cornerRadius = 13.dp
                    ) {
                        AuraIcon(AuraIcons.ArrowLeft, contentDescription = "Back", size = AuraIconSize.Toolbar)
                    }

                    Column {
                        Text(
                            text = "Code Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isSavingLocal) "Saving..." else "Saved locally",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSavingLocal) MaterialTheme.colorScheme.primary else neu.success
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .neuRaised(
                                    cornerRadius = 11.dp,
                                    backgroundColor = neu.surface,
                                    shadowOffset = 3.dp,
                                    blurRadius = 6.dp
                                )
                                .clickable { showLanguageMenu = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    selectedLanguage.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                AuraIcon(AuraIcons.ChevronDown, contentDescription = null, size = 14.dp, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            containerColor = neu.surfaceElevated
                        ) {
                            languages.forEach { (langKey, langLabel) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(langLabel)
                                            if (langKey in listOf("python", "html", "javascript", "sql")) {
                                                Text(" (Local)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = langKey
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        AuraNeuIconButton(
                            onClick = { showTemplateMenu = true },
                            size = 42.dp,
                            cornerRadius = 13.dp,
                            tint = MaterialTheme.colorScheme.secondary
                        ) {
                            AuraIcon(AuraIcons.Sparkles, contentDescription = "Code Templates", size = AuraIconSize.Toolbar)
                        }

                        DropdownMenu(
                            expanded = showTemplateMenu,
                            onDismissRequest = { showTemplateMenu = false },
                            containerColor = neu.surfaceElevated
                        ) {
                            DropdownMenuItem(
                                text = { Text("Python: Analytics & Math") },
                                onClick = {
                                    showTemplateMenu = false
                                    applyTemplate(
                                        """
# Python Math & Analytics
scores = [12, 45, 68, 90, 104]
print("Dataset:", scores)
print("Count:", len(scores))
print("Sum:", sum(scores))
mean = sum(scores) / len(scores)
print("Mean average:", mean)
                                        """.trimIndent()
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("HTML/JS: Interactive Counter") },
                                onClick = {
                                    showTemplateMenu = false
                                    applyTemplate(
                                        """
<!DOCTYPE html>
<html>
<body style="background:#090D16; color:#38BDF8; font-family:sans-serif; text-align:center; padding:30px;">
  <h2>AuraNotes Web Sandbox</h2>
  <h1 id="count">0</h1>
  <button onclick="inc()" style="background:#3B82F6; color:white; border:none; padding:12px 24px; border-radius:10px; font-weight:bold;">Click Me</button>
  <script>
    let c = 0;
    function inc() {
      c++;
      document.getElementById('count').innerText = c;
      console.log('Count incremented to ' + c);
    }
  </script>
</body>
</html>
                                        """.trimIndent()
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("SQL: Create & Query Table") },
                                onClick = {
                                    showTemplateMenu = false
                                    applyTemplate(
                                        """
CREATE TABLE developers (id INTEGER PRIMARY KEY, name TEXT, language TEXT, aura_points INTEGER);
INSERT INTO developers VALUES (1, 'Alice', 'Kotlin', 980);
INSERT INTO developers VALUES (2, 'Bob', 'Python', 870);
INSERT INTO developers VALUES (3, 'Charlie', 'Rust', 950);
SELECT * FROM developers WHERE aura_points > 900;
                                        """.trimIndent()
                                    )
                                }
                            )
                        }
                    }

                    AuraNeuIconButton(
                        onClick = { runCode() },
                        enabled = !isRunning,
                        size = 42.dp,
                        cornerRadius = 13.dp,
                        isAccent = true
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            AuraIcon(AuraIcons.Play, contentDescription = "Run Code", size = AuraIconSize.Button, tint = Color.White)
                        }
                    }
                }
            }
        },
        bottomBar = {
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
            ) {
                if (selectedTab == 0) {
                    QuickSymbolBar(
                        onInsertSymbol = { insertSymbol(it) },
                        modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    )
                }

                // Neumorphic Studio Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val studioTabs = buildList {
                        add(Triple(0, "Editor", AuraIcons.Code2))
                        add(Triple(1, if (executionResult?.status == "ERROR") "Output (Error)" else "Console", AuraIcons.Terminal))
                        if (selectedLanguage.lowercase() in listOf("html", "javascript", "js", "htm")) {
                            add(Triple(2, "Web Preview", AuraIcons.Globe))
                        }
                    }
                    studioTabs.forEach { (idx, label, icon) ->
                        val isSelected = selectedTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isSelected) {
                                        Modifier.neuInset(
                                            cornerRadius = 16.dp,
                                            backgroundColor = neu.surfaceRecessed,
                                            shadowOffset = 2.5.dp,
                                            blurRadius = 5.dp
                                        )
                                    } else {
                                        Modifier.clip(RoundedCornerShape(16.dp))
                                    }
                                )
                                .appleSpringClickable(
                                    pressedScale = 0.94f,
                                    enableHaptics = true,
                                    onClick = { selectedTab = idx }
                                )
                                .padding(vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AuraAnimatedNavIcon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    selected = isSelected,
                                    size = AuraIconSize.Button
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AuraNeuTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "File Name (e.g. main.py)",
                            leadingIcon = {
                                AuraIcon(AuraIcons.FileCode2, contentDescription = null, size = AuraIconSize.Input, tint = MaterialTheme.colorScheme.primary)
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Soft UI Framed Developer Canvas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .neuRaised(
                                    cornerRadius = 20.dp,
                                    backgroundColor = CodeBackground,
                                    shadowOffset = 5.dp,
                                    blurRadius = 10.dp
                                )
                                .background(CodeBackground)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val lineCount = remember(codeValue.text) { codeValue.text.lines().size.coerceAtLeast(1) }
                            val lineNumbersText = remember(lineCount) {
                                (1..lineCount).joinToString("\n") { it.toString().padStart(3) }
                            }

                            Text(
                                text = lineNumbersText,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = CodeLineNumber
                                ),
                                modifier = Modifier
                                    .background(CodeBackground.copy(alpha = 0.85f))
                                    .padding(horizontal = 10.dp, vertical = 14.dp)
                            )

                            VerticalDivider(color = CodeLineNumber.copy(alpha = 0.3f), modifier = Modifier.fillMaxHeight())

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                BasicTextField(
                                    value = codeValue,
                                    onValueChange = { codeValue = it },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = Color(0xFFF8FAFC)
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    visualTransformation = VisualTransformation { text ->
                                        TransformedText(
                                            SyntaxHighlighter.highlight(text.text, selectedLanguage),
                                            OffsetMapping.Identity
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 350.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .neuRaised(
                                cornerRadius = 20.dp,
                                backgroundColor = CodeBackground,
                                shadowOffset = 5.dp,
                                blurRadius = 10.dp
                            )
                            .background(CodeBackground)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Execution Output",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                executionResult?.let { res ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (res.status == "SUCCESS") Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            "${res.status} · ${res.executionTimeMs}ms",
                                            color = if (res.status == "SUCCESS") Color(0xFF34D399) else Color(0xFFF87171),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row {
                                IconButton(onClick = {
                                    executionResult = null
                                    webConsoleLogs.clear()
                                }) {
                                    AuraIcon(AuraIcons.Eraser, contentDescription = "Clear", size = AuraIconSize.Button, tint = Color.LightGray)
                                }
                                IconButton(onClick = {
                                    val text = (executionResult?.stdout ?: "") + "\n" + (executionResult?.stderr ?: "")
                                    clipboardManager.setText(AnnotatedString(text))
                                }) {
                                    AuraIcon(AuraIcons.Copy, contentDescription = "Copy", size = AuraIconSize.Button, tint = Color.LightGray)
                                }
                            }
                        }

                        HorizontalDivider(color = CodeLineNumber.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (executionResult == null && webConsoleLogs.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AuraIcon(
                                        AuraIcons.Terminal,
                                        contentDescription = null,
                                        tint = CodeLineNumber,
                                        size = AuraIconSize.EmptyState
                                    )
                                    Text(
                                        "Tap Run to execute code locally.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = CodeLineNumber
                                    )
                                }
                            } else {
                                executionResult?.let { res ->
                                    if (res.stdout.isNotEmpty()) {
                                        Text(
                                            text = res.stdout,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Color(0xFFA5D6FF)
                                        )
                                    }
                                    if (res.stderr.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = res.stderr,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Color(0xFFFF7B72)
                                        )
                                    }
                                }

                                if (webConsoleLogs.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("--- Web Console Logs ---", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                    webConsoleLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF7EE787)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .neuRaised(
                                cornerRadius = 20.dp,
                                backgroundColor = neu.surface,
                                shadowOffset = 5.dp,
                                blurRadius = 10.dp
                            )
                    ) {
                        WebSandboxView(
                            htmlContent = codeValue.text,
                            modifier = Modifier.fillMaxSize(),
                            onConsoleLog = { logMsg -> webConsoleLogs.add(logMsg) }
                        )
                    }
                }
            }
        }
    }
}
