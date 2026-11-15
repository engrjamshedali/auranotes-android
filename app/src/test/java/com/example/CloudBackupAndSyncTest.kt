package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import com.example.auth.CloudAuthProvider
import com.example.auth.DriveAuthorizationOutcome
import com.example.auth.GoogleUserProfile
import com.example.cloud.CloudBackupFileInfo
import com.example.cloud.CloudBackupPayloadResult
import com.example.cloud.CloudBackupSerializer
import com.example.cloud.CloudNoteResolutionChoice
import com.example.cloud.CloudRestoreMode
import com.example.cloud.CloudSyncCoordinator
import com.example.cloud.CloudSyncOutcome
import com.example.cloud.DriveApiException
import com.example.cloud.DriveRemoteStorage
import com.example.cloud.NighttimeBackupScheduler
import com.example.data.local.AuraDatabase
import com.example.data.local.AuraUserPreferences
import com.example.data.local.CategoryEntity
import com.example.data.local.CloudSyncStatus
import com.example.data.local.NoteEntity
import com.example.data.local.PreferencesRepository
import com.example.domain.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudBackupAndSyncTest {

    private lateinit var context: Context
    private lateinit var db: AuraDatabase
    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = PreferencesRepository(context)
        backupManager = BackupManager(
            context = context,
            noteDao = db.noteDao(),
            categoryDao = db.categoryDao(),
            attachmentDao = db.attachmentDao(),
            backupDao = db.backupDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setNetworkState(unmeteredWifi: Boolean, connected: Boolean = true) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)
        if (!connected) {
            shadowCm.setDefaultNetworkActive(false)
            shadowCm.setActiveNetworkInfo(null)
            return
        }
        shadowCm.setDefaultNetworkActive(true)
        val network: Network = cm.activeNetwork ?: ShadowNetwork.newInstance(1)
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps = shadowOf(caps)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (unmeteredWifi) {
            shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        shadowCm.setNetworkCapabilities(network, caps)
    }

    @Test
    fun `serializer builds valid schema v2 payload and verifies SHA256 integrity`() {
        val note = NoteEntity(
            id = "n1",
            title = "Architecture",
            content = "Offline-first Room vault",
            updatedAt = 1000L
        )
        val cat = CategoryEntity(id = "cat_work", name = "Work", iconName = "Folder", colorHex = "#3B82F6")
        val prefs = AuraUserPreferences(themeMode = "DARK", accentIndex = 2)

        val payload = CloudBackupSerializer.buildBackupJson(
            userId = "user_123",
            userEmail = "user@example.com",
            notes = listOf(note),
            categories = listOf(cat),
            attachments = emptyList(),
            preferences = prefs,
            timestampMillis = 2000L
        )

        val preview = CloudBackupSerializer.parseAndAnalyzeBackup(
            rawJson = payload.jsonString,
            fileInfo = null,
            localNotes = emptyList(),
            expectedUserId = "user_123"
        )

        assertTrue(preview.isValid)
        assertTrue(preview.checksumValid)
        assertEquals(2, preview.schemaVersion)
        assertEquals(1, preview.notes.size)
        assertEquals("Architecture", preview.notes.first().title)
        assertEquals("DARK", preview.settings?.themeMode)
    }

    @Test
    fun `serializer rejects tampered JSON and wrong user account`() {
        val note = NoteEntity(id = "n1", title = "Secret", content = "Original", updatedAt = 1000L)
        val payload = CloudBackupSerializer.buildBackupJson(
            userId = "user_alpha",
            userEmail = "alpha@example.com",
            notes = listOf(note),
            categories = emptyList(),
            attachments = emptyList(),
            preferences = AuraUserPreferences(),
            timestampMillis = 2000L
        )

        // Wrong user account rejected
        val crossUserPreview = CloudBackupSerializer.parseAndAnalyzeBackup(
            rawJson = payload.jsonString,
            fileInfo = null,
            localNotes = emptyList(),
            expectedUserId = "user_beta"
        )
        assertFalse(crossUserPreview.isValid)
        assertTrue(crossUserPreview.error?.contains("different Google account") == true)

        // Corrupted payload rejected via SHA-256 mismatch
        val tamperedObj = JSONObject(payload.jsonString)
        tamperedObj.getJSONObject("userData").getJSONArray("notes").getJSONObject(0).put("content", "Hacked")
        val tamperedPreview = CloudBackupSerializer.parseAndAnalyzeBackup(
            rawJson = tamperedObj.toString(),
            fileInfo = null,
            localNotes = emptyList(),
            expectedUserId = "user_alpha"
        )
        assertFalse(tamperedPreview.isValid)
        assertTrue(tamperedPreview.error?.contains("SHA-256") == true)
    }

    @Test
    fun `cloud sync uploads once skips unchanged data and recovers from 401 token expiry`() = runBlocking {
        setNetworkState(unmeteredWifi = true, connected = true)
        db.noteDao().insertNote(NoteEntity(id = "n1", title = "Note One", content = "Body", updatedAt = 5000L))
        prefsRepo.setDriveBackupConfig(true, "tester@example.com")

        val fakeAuth = FakeCloudAuthProvider(
            user = GoogleUserProfile("u_1", "tester@example.com", "Tester", null),
            tokens = ArrayDeque(listOf("expired_token", "fresh_token"))
        )
        val fakeDrive = FakeDriveRemoteStorage(rejectFirstUnauthorizedToken = "expired_token")
        val coordinator = CloudSyncCoordinator(
            context = context,
            authManager = fakeAuth,
            driveClient = fakeDrive,
            noteDao = db.noteDao(),
            categoryDao = db.categoryDao(),
            attachmentDao = db.attachmentDao(),
            backupDao = db.backupDao(),
            backupManager = backupManager,
            preferencesRepository = prefsRepo
        )

        // 1. First upload triggers 401 on expired_token -> refreshes -> succeeds with fresh_token
        val firstOutcome = coordinator.performCloudBackup(
            force = false,
            createHistorySnapshot = true,
            interactiveAllowed = false
        )
        assertTrue(firstOutcome is CloudSyncOutcome.Success)
        assertFalse((firstOutcome as CloudSyncOutcome.Success).skippedUnchanged)
        assertEquals(2, fakeDrive.storedFiles.size) // backup-latest.json + history snapshot
        assertEquals(CloudSyncStatus.SYNCED.name, prefsRepo.getSnapshot().cloudSyncStatus)

        // 2. Second non-forced sync with identical content skips redundant upload
        val secondOutcome = coordinator.performCloudBackup(
            force = false,
            createHistorySnapshot = false,
            interactiveAllowed = false
        )
        assertTrue(secondOutcome is CloudSyncOutcome.Success)
        assertTrue((secondOutcome as CloudSyncOutcome.Success).skippedUnchanged)
        assertEquals(1, fakeDrive.latestPatchOrCreateCount)
    }

    @Test
    fun `cloud sync queues on metered wifi-only or server 503 and fails fast on quota`() = runBlocking {
        db.noteDao().insertNote(NoteEntity(id = "n1", title = "Queued", content = "Wait", updatedAt = 1000L))
        prefsRepo.setDriveBackupConfig(true, "tester@example.com")
        prefsRepo.setWifiOnlyBackup(true)

        val fakeAuth = FakeCloudAuthProvider(
            user = GoogleUserProfile("u_1", "tester@example.com", "Tester", null),
            tokens = ArrayDeque(listOf("valid_token", "valid_token", "valid_token"))
        )
        val fakeDrive = FakeDriveRemoteStorage()
        val coordinator = CloudSyncCoordinator(
            context, fakeAuth, fakeDrive,
            db.noteDao(), db.categoryDao(), db.attachmentDao(), db.backupDao(),
            backupManager, prefsRepo
        )

        // Metered cellular when wifiOnly=true -> WAITING_FOR_WIFI
        setNetworkState(unmeteredWifi = false, connected = true)
        val wifiWait = coordinator.performCloudBackup(force = false, createHistorySnapshot = false, interactiveAllowed = false)
        assertTrue(wifiWait is CloudSyncOutcome.WaitingForCondition)
        assertEquals(CloudSyncStatus.WAITING_FOR_WIFI, (wifiWait as CloudSyncOutcome.WaitingForCondition).status)

        // Switch to Wi-Fi, simulate transient 503 -> RetryLater
        setNetworkState(unmeteredWifi = true, connected = true)
        fakeDrive.nextError = DriveApiException.TransientServer("503 Unavailable")
        val transientOut = coordinator.performCloudBackup(force = false, createHistorySnapshot = false, interactiveAllowed = false)
        assertTrue(transientOut is CloudSyncOutcome.RetryLater)

        // Simulate 403 Quota Exceeded -> Failed (no blind retry)
        fakeDrive.nextError = DriveApiException.QuotaOrPermission("Storage quota exceeded", quota = true)
        val quotaOut = coordinator.performCloudBackup(force = false, createHistorySnapshot = false, interactiveAllowed = false)
        assertTrue(quotaOut is CloudSyncOutcome.Failed)
    }

    @Test
    fun `safe cloud restore creates safety snapshot and preserves conflicts cleanly`() = runBlocking {
        setNetworkState(unmeteredWifi = true, connected = true)
        // Local state: note n1 newer locally, note n2 older locally
        val localN1 = NoteEntity(id = "n1", title = "N1", content = "Local Newer Content", updatedAt = 9000L)
        val localN2 = NoteEntity(id = "n2", title = "N2", content = "Local Older Content", updatedAt = 1000L)
        db.noteDao().insertNote(localN1)
        db.noteDao().insertNote(localN2)
        prefsRepo.setDriveBackupConfig(true, "tester@example.com")

        // Cloud payload: n1 older in cloud, n2 newer in cloud, n3 brand new from cloud
        val cloudN1 = NoteEntity(id = "n1", title = "N1", content = "Cloud Older Content", updatedAt = 3000L)
        val cloudN2 = NoteEntity(id = "n2", title = "N2", content = "Cloud Newer Content", updatedAt = 8000L)
        val cloudN3 = NoteEntity(id = "n3", title = "N3", content = "Brand New Cloud Note", updatedAt = 8500L)
        val cloudPayload = CloudBackupSerializer.buildBackupJson(
            userId = "u_1",
            userEmail = "tester@example.com",
            notes = listOf(cloudN1, cloudN2, cloudN3),
            categories = emptyList(),
            attachments = emptyList(),
            preferences = AuraUserPreferences(themeMode = "DARK", accentIndex = 3),
            timestampMillis = 9500L
        )
        val fakeFileInfo = CloudBackupFileInfo(
            fileId = "file_cloud_1",
            name = "backup-latest.json",
            modifiedTimeMillis = 9500L,
            sizeBytes = cloudPayload.jsonString.length.toLong(),
            userId = "u_1"
        )
        val fakeDrive = FakeDriveRemoteStorage().apply {
            storedFiles[fakeFileInfo.fileId] = fakeFileInfo to cloudPayload.jsonString
        }
        val fakeAuth = FakeCloudAuthProvider(
            user = GoogleUserProfile("u_1", "tester@example.com", "Tester", null),
            tokens = ArrayDeque(listOf("tok1", "tok2"))
        )
        val coordinator = CloudSyncCoordinator(
            context, fakeAuth, fakeDrive,
            db.noteDao(), db.categoryDao(), db.attachmentDao(), db.backupDao(),
            backupManager, prefsRepo
        )

        val preview = coordinator.previewCloudRestore(fakeFileInfo, context)
        assertTrue(preview.isValid)
        assertEquals(1, preview.newFromCloudCount)
        assertEquals(2, preview.conflicts.size)
        val n1Conflict = preview.conflicts.first { it.noteId == "n1" }
        val n2Conflict = preview.conflicts.first { it.noteId == "n2" }
        assertFalse(n1Conflict.cloudIsNewer)
        assertEquals(CloudNoteResolutionChoice.KEEP_BOTH, n1Conflict.defaultChoice)
        assertTrue(n2Conflict.cloudIsNewer)
        assertEquals(CloudNoteResolutionChoice.USE_CLOUD, n2Conflict.defaultChoice)

        // Execute SMART_MERGE with defaults -> n3 added, n2 uses cloud, n1 keeps both, and safety snapshot created
        val report = coordinator.executeCloudRestore(
            preview = preview,
            mode = CloudRestoreMode.SMART_MERGE,
            restoreSettings = true
        )
        assertTrue(report.success)
        assertTrue(report.safetyBackupName?.endsWith(".auranotes") == true)
        assertTrue(db.backupDao().getAllBackups().first().any { it.backupType == "SAFETY_PRE_RESTORE" })
        assertEquals(2, report.conflictsResolved)

        val finalNotes = db.noteDao().getAllNotesSync()
        assertEquals(4, finalNotes.size) // n1 local, n1 cloud copy, n2 cloud, n3 cloud
        assertEquals("Cloud Newer Content", db.noteDao().getNoteById("n2")?.content)
        assertNotEquals("Cloud Older Content", db.noteDao().getNoteById("n1")?.content)
        assertEquals("DARK", prefsRepo.getSnapshot().themeMode)
        assertEquals(3, prefsRepo.getSnapshot().accentIndex)
    }

    @Test
    fun `nighttime scheduler computes window delays and respects constraints`() {
        // 14:30 -> next midnight (00:00) is 9.5 hours away
        val daytimeCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delayMs = NighttimeBackupScheduler.computeInitialDelayMillis(
            startHour = 0,
            endHour = 5,
            nowMillis = daytimeCal.timeInMillis
        )
        assertEquals(TimeUnit.MINUTES.toMillis(9 * 60 + 30L), delayMs)

        // 02:15 AM -> inside 12 AM - 5 AM window -> 1 minute delay
        val nightCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 11, 2, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val insideDelayMs = NighttimeBackupScheduler.computeInitialDelayMillis(
            startHour = 0,
            endHour = 5,
            nowMillis = nightCal.timeInMillis
        )
        assertEquals(TimeUnit.MINUTES.toMillis(1), insideDelayMs)

        val constraints = NighttimeBackupScheduler.buildNighttimeConstraints(
            AuraUserPreferences(
                wifiOnlyBackup = true,
                requireChargingForNightBackup = true,
                requireIdleForNightBackup = true
            )
        )
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
        assertTrue(constraints.requiresDeviceIdle())
        assertTrue(constraints.requiresBatteryNotLow())
    }

    private class FakeCloudAuthProvider(
        private val user: GoogleUserProfile?,
        private val tokens: ArrayDeque<String>
    ) : CloudAuthProvider {
        override fun currentUser(): GoogleUserProfile? = user
        override suspend fun authorizeDriveAppData(
            context: Context,
            interactiveAllowed: Boolean
        ): DriveAuthorizationOutcome {
            val next = if (tokens.isNotEmpty()) tokens.removeFirst() else "fallback_token"
            return DriveAuthorizationOutcome.Authorized(next)
        }
    }

    private class FakeDriveRemoteStorage(
        private val rejectFirstUnauthorizedToken: String? = null
    ) : DriveRemoteStorage {
        val storedFiles = LinkedHashMap<String, Pair<CloudBackupFileInfo, String>>()
        var latestPatchOrCreateCount = 0
        var nextError: DriveApiException? = null
        private var seq = 1

        private fun checkFailure(token: String) {
            nextError?.let { err ->
                nextError = null
                throw err
            }
            if (rejectFirstUnauthorizedToken != null && token == rejectFirstUnauthorizedToken) {
                throw DriveApiException.Unauthorized("401 Token Expired")
            }
        }

        override suspend fun listBackups(accessToken: String, userId: String?): List<CloudBackupFileInfo> {
            checkFailure(accessToken)
            return storedFiles.values.map { it.first }.filter { userId == null || it.userId == userId }
        }

        override suspend fun uploadOrUpdateLatest(
            accessToken: String,
            userId: String,
            userEmail: String,
            payload: CloudBackupPayloadResult
        ): CloudBackupFileInfo {
            checkFailure(accessToken)
            latestPatchOrCreateCount++
            val existing = storedFiles.values.firstOrNull { it.first.kind == "latest" && it.first.userId == userId }
            val fileId = existing?.first?.fileId ?: "latest_${seq++}"
            val info = CloudBackupFileInfo(
                fileId = fileId,
                name = CloudBackupSerializer.LATEST_BACKUP_FILENAME,
                modifiedTimeMillis = System.currentTimeMillis(),
                sizeBytes = payload.jsonString.length.toLong(),
                userId = userId,
                userEmail = userEmail,
                checksumSha256 = payload.contentChecksumSha256,
                noteCount = payload.noteCount,
                kind = "latest"
            )
            storedFiles[fileId] = info to payload.jsonString
            return info
        }

        override suspend fun uploadHistorySnapshot(
            accessToken: String,
            fileName: String,
            userId: String,
            userEmail: String,
            payload: CloudBackupPayloadResult
        ): CloudBackupFileInfo {
            checkFailure(accessToken)
            val fileId = "hist_${seq++}"
            val info = CloudBackupFileInfo(
                fileId = fileId,
                name = fileName,
                modifiedTimeMillis = System.currentTimeMillis(),
                sizeBytes = payload.jsonString.length.toLong(),
                userId = userId,
                userEmail = userEmail,
                checksumSha256 = payload.contentChecksumSha256,
                noteCount = payload.noteCount,
                kind = "history"
            )
            storedFiles[fileId] = info to payload.jsonString
            return info
        }

        override suspend fun downloadBackup(accessToken: String, fileId: String): String {
            checkFailure(accessToken)
            return storedFiles[fileId]?.second ?: throw DriveApiException.Other("404 Not Found")
        }

        override suspend fun deleteBackup(accessToken: String, fileId: String) {
            checkFailure(accessToken)
            storedFiles.remove(fileId)
        }
    }
}
