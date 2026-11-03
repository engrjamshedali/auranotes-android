package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        NoteEntity::class,
        CategoryEntity::class,
        AttachmentEntity::class,
        BackupEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "auranotes.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        seedInitialData(database)
                    }
                }
            }

            private suspend fun seedInitialData(database: AuraDatabase) {
                val categoryDao = database.categoryDao()
                val noteDao = database.noteDao()

                val defaultCategories = listOf(
                    CategoryEntity("cat_study", "Study", "School", "#6366F1", 0, true),
                    CategoryEntity("cat_prog", "Programming", "Code", "#38BDF8", 1, true),
                    CategoryEntity("cat_proj", "Projects", "FolderSpecial", "#8B5CF6", 2, true),
                    CategoryEntity("cat_personal", "Personal", "Person", "#10B981", 3, true),
                    CategoryEntity("cat_research", "Research", "Science", "#F59E0B", 4, true),
                    CategoryEntity("cat_work", "Work", "Work", "#EC4899", 5, true),
                    CategoryEntity("cat_ideas", "Ideas", "Lightbulb", "#F97316", 6, true),
                    CategoryEntity("cat_tasks", "Tasks", "CheckCircle", "#14B8A6", 7, true),
                    CategoryEntity("cat_media", "Media", "PermMedia", "#06B6D4", 8, true)
                )
                categoryDao.insertCategories(defaultCategories)

                val now = System.currentTimeMillis()

                val welcomeNote = NoteEntity(
                    id = "note_welcome_001",
                    title = "✨ Welcome to AuraNotes",
                    content = """
# Welcome to AuraNotes
*The Premium Offline Notes, Media Vault & Universal Code IDE*

AuraNotes combines the simplicity of **Google Keep**, the structured depth of **Notion & Obsidian**, and the power of a developer **Code Studio** into one offline-first Android application.

### Key Capabilities
- ✍️ **Universal Notes**: Plain text, rich markdown, interactive checklists, code files, and media attachments.
- 💻 **Universal Code Studio**: Real execution for Python, interactive sandboxed HTML/JS/CSS live previews, SQLite queries, and 25+ language modes.
- 🛡️ **Encrypted Media Vault**: Store images, audio voice recordings, documents, and attachments in your device's private storage.
- 📦 **Portable .auranotes Backups**: Zero-cloud dependency. Export and restore all notes, rich text, categories, and binary attachments to any Android phone effortlessly.
- 🎨 **Aura Glassmorphism UI**: High-fidelity dark and light themes, customizable radiant accent palettes, and responsive layouts for phones and tablets.
                    """.trimIndent(),
                    noteType = NoteType.RICH_TEXT.name,
                    categoryId = "cat_personal",
                    tags = "welcome, guide, auranotes",
                    isPinned = true,
                    isFavorite = true,
                    colorIndex = 0,
                    createdAt = now - 100000,
                    updatedAt = now - 100000
                )

                val pythonNote = NoteEntity(
                    id = "note_code_python_001",
                    title = "🐍 Python Analytics & Math Engine",
                    content = """
# Real Offline Python Scripting in AuraNotes
def calculate_aura_metrics():
    print("=== AuraNotes Analytics Engine ===")
    languages = ["Python", "JavaScript", "HTML/CSS", "SQL", "Rust", "Kotlin"]
    print("Supported Languages count:", len(languages))
    
    # Calculate geometric sequence
    scores = [x**2 for x in range(1, 8)]
    print("Generated data matrix:", scores)
    
    total = sum(scores)
    average = total / len(scores)
    print("Total Sum:", total)
    print("Mean Performance Score:", average)
    
    status = "Active & Offline"
    print("Aura Runtime Status:", status)
    return total

result = calculate_aura_metrics()
print("Execution Completed with result:", result)
                    """.trimIndent(),
                    noteType = NoteType.CODE.name,
                    categoryId = "cat_prog",
                    language = "python",
                    tags = "python, algorithm, demo",
                    isPinned = true,
                    isFavorite = true,
                    colorIndex = 1,
                    createdAt = now - 60000,
                    updatedAt = now - 60000
                )

                val webNote = NoteEntity(
                    id = "note_code_web_001",
                    title = "⚡ Interactive Neon Canvas (HTML/JS)",
                    content = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body {
      margin: 0;
      background: #090D16;
      color: #F8FAFC;
      font-family: system-ui, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      text-align: center;
      padding: 16px;
    }
    .badge {
      background: rgba(99, 102, 241, 0.2);
      border: 1px solid #6366F1;
      color: #818CF8;
      padding: 6px 14px;
      border-radius: 20px;
      font-size: 13px;
      margin-bottom: 12px;
      letter-spacing: 1px;
    }
    h1 {
      margin: 0 0 10px 0;
      font-size: 24px;
      background: linear-gradient(135deg, #38BDF8, #818CF8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    button {
      background: #6366F1;
      color: white;
      border: none;
      padding: 10px 24px;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
      transition: transform 0.1s;
    }
    button:active {
      transform: scale(0.96);
    }
    #counter {
      font-size: 32px;
      font-weight: bold;
      color: #38BDF8;
      margin: 16px 0;
    }
  </style>
</head>
<body>
  <div class="badge">AURANOTES WEB STUDIO</div>
  <h1>Live Sandboxed Execution</h1>
  <p>Run, test and preview interactive Web apps completely offline.</p>
  <div id="counter">0 clicks</div>
  <button onclick="increment()">Pulse Aura</button>

  <script>
    let count = 0;
    function increment() {
      count++;
      document.getElementById('counter').innerText = count + (count === 1 ? ' click' : ' clicks');
      console.log('Button clicked! Current count: ' + count);
    }
    console.log('AuraNotes Web Sandbox initialized successfully.');
  </script>
</body>
</html>
                    """.trimIndent(),
                    noteType = NoteType.CODE.name,
                    categoryId = "cat_prog",
                    language = "html",
                    tags = "html, javascript, web",
                    isPinned = false,
                    isFavorite = true,
                    colorIndex = 2,
                    createdAt = now - 30000,
                    updatedAt = now - 30000
                )

                val taskNote = NoteEntity(
                    id = "note_task_001",
                    title = "📋 Weekly Sprint & Media Vault Check",
                    content = """
- [x] Configure AuraNotes offline database & schema
- [x] Implement Universal Code Studio with Python & Web engines
- [ ] Test Portable .auranotes ZIP backup & restore
- [ ] Record voice notes in Media Vault
- [ ] Organize study notes by tags and categories
                    """.trimIndent(),
                    noteType = NoteType.CHECKLIST.name,
                    categoryId = "cat_tasks",
                    tags = "tasks, sprint",
                    isPinned = false,
                    isFavorite = false,
                    colorIndex = 3,
                    createdAt = now - 15000,
                    updatedAt = now - 15000
                )

                noteDao.insertNotes(listOf(welcomeNote, pythonNote, webNote, taskNote))
            }
        }
    }
}
