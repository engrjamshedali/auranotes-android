package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aura_preferences")

data class AuraUserPreferences(
    val themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    val accentIndex: Int = 0, // 0=Indigo/Violet, 1=Ocean Cyan, 2=Emerald, 3=Crimson Rose, 4=Solar Amber
    val editorFontSize: Int = 14,
    val backupFrequency: String = "WEEKLY",
    val backupRetentionCount: Int = 5,
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
    val highContrast: Boolean = false,
    val defaultNoteType: String = "RICH_TEXT"
)

class PreferencesRepository(private val context: Context) {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyAccent = intPreferencesKey("accent_index")
    private val keyFontSize = intPreferencesKey("editor_font_size")
    private val keyBackupFreq = stringPreferencesKey("backup_freq")
    private val keyBackupRetention = intPreferencesKey("backup_retention")
    private val keyReducedMotion = booleanPreferencesKey("reduced_motion")
    private val keyReducedTransparency = booleanPreferencesKey("reduced_transparency")
    private val keyHighContrast = booleanPreferencesKey("high_contrast")
    private val keyDefaultNoteType = stringPreferencesKey("default_note_type")

    val preferencesFlow: Flow<AuraUserPreferences> = context.dataStore.data.map { prefs ->
        AuraUserPreferences(
            themeMode = prefs[keyTheme] ?: "DARK",
            accentIndex = prefs[keyAccent] ?: 0,
            editorFontSize = prefs[keyFontSize] ?: 14,
            backupFrequency = prefs[keyBackupFreq] ?: "WEEKLY",
            backupRetentionCount = prefs[keyBackupRetention] ?: 5,
            reducedMotion = prefs[keyReducedMotion] ?: false,
            reducedTransparency = prefs[keyReducedTransparency] ?: false,
            highContrast = prefs[keyHighContrast] ?: false,
            defaultNoteType = prefs[keyDefaultNoteType] ?: "RICH_TEXT"
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[keyTheme] = mode }
    }

    suspend fun setAccentIndex(index: Int) {
        context.dataStore.edit { it[keyAccent] = index }
    }

    suspend fun setEditorFontSize(size: Int) {
        context.dataStore.edit { it[keyFontSize] = size }
    }

    suspend fun setBackupFrequency(freq: String) {
        context.dataStore.edit { it[keyBackupFreq] = freq }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit { it[keyReducedMotion] = enabled }
    }

    suspend fun setReducedTransparency(enabled: Boolean) {
        context.dataStore.edit { it[keyReducedTransparency] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[keyHighContrast] = enabled }
    }

    suspend fun setDefaultNoteType(type: String) {
        context.dataStore.edit { it[keyDefaultNoteType] = type }
    }
}
