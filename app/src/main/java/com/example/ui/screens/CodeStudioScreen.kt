package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NoteEntity
import com.example.data.local.NoteType
import com.example.domain.CodeExecutionEngine
import com.example.domain.ExecutionResult
import com.example.ui.components.QuickSymbolBar
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.components.WebSandboxView
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.CodeLineNumber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStudioScreen(
    note: NoteEntity,
    onSaveNote: (NoteEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var title by remember { mutableStateOf(note.title) }
    var codeValue by remember { mutableStateOf(TextFieldValue(note.content, TextRange(note.content.length))) }
    var selectedLanguage by remember { mutableStateOf(note.language) }
    var isRunning by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf<ExecutionResult?>(null) }
    val webConsoleLogs = remember { mutableStateListOf<String>() }

    // Active Tab in Code Studio: 0 = Editor, 1 = Output Console, 2 = Web Preview
    var selectedTab by remember { mutableStateOf(0) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

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

    // Auto-save debouncer
    LaunchedEffect(title, codeValue.text, selectedLanguage) {
        val updated = note.copy(
            title = title,
            content = codeValue.text,
            language = selectedLanguage,
            noteType = NoteType.CODE.name,
            updatedAt = System.currentTimeMillis()
        )
        onSaveNote(updated)
    }

    fun runCode() {
        isRunning = true
        webConsoleLogs.clear()
        coroutineScope.launch {
            val res = CodeExecutionEngine.executeCode(selectedLanguage, codeValue.text)
            executionResult = res
            isRunning = false
            // If HTML/JS, user can view both Console and Web Preview
            if (selectedLanguage.lowercase() in listOf("html", "javascript", "js", "htm")) {
                selectedTab = 2 // Switch to Web preview
            } else {
                selectedTab = 1 // Switch to Console Output
            }
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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Code Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        // Language Dropdown Selector
                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { showLanguageMenu = true },
                                label = {
                                    Text(
                                        selectedLanguage.uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )

                            DropdownMenu(
                                expanded = showLanguageMenu,
                                onDismissRequest = { showLanguageMenu = false }
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
                                                    Text(" (Local Engine)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Templates button
                    IconButton(onClick = { showTemplateMenu = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Code Templates", tint = MaterialTheme.colorScheme.secondary)
                    }

                    // Run Button
                    FilledIconButton(
                        onClick = { runCode() },
                        enabled = !isRunning,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run Code")
                        }
                    }

                    // Templates dropdown menu
                    DropdownMenu(
                        expanded = showTemplateMenu,
                        onDismissRequest = { showTemplateMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Python: Analytics & Math") },
                            onClick = {
                                showTemplateMenu = false
                                applyTemplate("""
# Python Math & Analytics
scores = [12, 45, 68, 90, 104]
print("Dataset:", scores)
print("Count:", len(scores))
print("Sum:", sum(scores))
mean = sum(scores) / len(scores)
print("Mean average:", mean)
                                """.trimIndent())
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("HTML/JS: Neon Counter App") },
                            onClick = {
                                showTemplateMenu = false
                                applyTemplate("""
<!DOCTYPE html>
<html>
<body style="background:#090D16; color:#38BDF8; font-family:sans-serif; text-align:center; padding:30px;">
  <h2>AuraNotes Web Sandbox</h2>
  <h1 id="count">0</h1>
  <button onclick="inc()" style="background:#6366F1; color:white; border:none; padding:12px 24px; border-radius:10px; font-weight:bold;">Click Me</button>
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
                                """.trimIndent())
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SQL: Create & Query Database") },
                            onClick = {
                                showTemplateMenu = false
                                applyTemplate("""
CREATE TABLE developers (id INTEGER PRIMARY KEY, name TEXT, language TEXT, aura_points INTEGER);
INSERT INTO developers VALUES (1, 'Alice', 'Kotlin', 980);
INSERT INTO developers VALUES (2, 'Bob', 'Python', 870);
INSERT INTO developers VALUES (3, 'Charlie', 'Rust', 950);
SELECT * FROM developers WHERE aura_points > 900;
                                """.trimIndent())
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Developer Quick Symbol Bar
                if (selectedTab == 0) {
                    QuickSymbolBar(onInsertSymbol = { insertSymbol(it) })
                }

                // Bottom Tabs Bar: Editor, Output, Preview
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Code, contentDescription = null) },
                        label = { Text("Editor") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        label = {
                            Text(if (executionResult?.status == "ERROR") "Output (Error)" else "Console")
                        }
                    )
                    if (selectedLanguage.lowercase() in listOf("html", "javascript", "js", "htm")) {
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Language, contentDescription = null) },
                            label = { Text("Web Preview") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: Code Editor with line numbers & syntax highlighting
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title header
                        TextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("File Name or Title (e.g. main.py)") },
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Editor Canvas (Split: Line Numbers + Text Editor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(CodeBackground)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Line Numbers
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
                                    .background(CodeBackground.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                            )

                            VerticalDivider(color = CodeLineNumber.copy(alpha = 0.3f), modifier = Modifier.fillMaxHeight())

                            // Code Text Field with Syntax Highlighting
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)) {
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
                                    visualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
                                        androidx.compose.ui.text.input.TransformedText(
                                            SyntaxHighlighter.highlight(text.text, selectedLanguage),
                                            androidx.compose.ui.text.input.OffsetMapping.Identity
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 350.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: Console / Output Panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                                    Icon(Icons.Default.ClearAll, contentDescription = "Clear", tint = Color.LightGray)
                                }
                                IconButton(onClick = {
                                    val text = (executionResult?.stdout ?: "") + "\n" + (executionResult?.stderr ?: "")
                                    clipboardManager.setText(AnnotatedString(text))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray)
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
                                Text(
                                    "Press the Run (▶) button above to execute code.\nPython, SQL, HTML/JS, and more run completely offline on your device.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
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
                                    webConsoleLogs.forEach { log: String ->
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
                    // TAB 2: Interactive Web Sandbox Live Preview
                    WebSandboxView(
                        htmlContent = codeValue.text,
                        modifier = Modifier.fillMaxSize(),
                        onConsoleLog = { logMsg ->
                            webConsoleLogs.add(logMsg)
                        }
                    )
                }
            }
        }
    }
}
