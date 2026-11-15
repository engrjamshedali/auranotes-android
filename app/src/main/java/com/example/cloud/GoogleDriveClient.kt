package com.example.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class DriveApiException(
    message: String,
    val isAuthError: Boolean = false,
    val isTransient: Boolean = false,
    val isQuotaExceeded: Boolean = false,
    val isNetworkError: Boolean = false
) : Exception(message) {
    class Unauthorized(message: String) : DriveApiException(message, isAuthError = true, isTransient = false)
    class QuotaOrPermission(message: String, quota: Boolean) : DriveApiException(message, isAuthError = !quota, isTransient = false, isQuotaExceeded = quota)
    class TransientServer(message: String) : DriveApiException(message, isTransient = true)
    class Network(message: String) : DriveApiException(message, isTransient = true, isNetworkError = true)
    class Other(message: String) : DriveApiException(message)
}

interface DriveRemoteStorage {
    suspend fun listBackups(accessToken: String, userId: String? = null): List<CloudBackupFileInfo>
    suspend fun uploadOrUpdateLatest(
        accessToken: String,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult
    ): CloudBackupFileInfo
    suspend fun uploadHistorySnapshot(
        accessToken: String,
        fileName: String,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult
    ): CloudBackupFileInfo
    suspend fun downloadBackup(accessToken: String, fileId: String): String
    suspend fun deleteBackup(accessToken: String, fileId: String)
}

class GoogleDriveClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
) : DriveRemoteStorage {

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun listBackups(accessToken: String, userId: String?): List<CloudBackupFileInfo> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("trashed = false and 'appDataFolder' in parents")
                if (!userId.isNullOrBlank()) {
                    val escaped = userId.replace("'", "\\'")
                    append(" and appProperties has { key='userId' and value='$escaped' }")
                }
            }
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val fields = java.net.URLEncoder.encode(
                "files(id,name,modifiedTime,size,appProperties)",
                "UTF-8"
            )
            val url = "$DRIVE_FILES_URL?spaces=appDataFolder&q=$encodedQuery&orderBy=modifiedTime%20desc&pageSize=30&fields=$fields"

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val body = executeWithClassification(req)
            val root = JSONObject(body)
            val files = root.optJSONArray("files") ?: JSONArray()
            buildList {
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    add(parseFileObject(f))
                }
            }
        }

    override suspend fun uploadOrUpdateLatest(
        accessToken: String,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult
    ): CloudBackupFileInfo = withContext(Dispatchers.IO) {
        val existing = listBackups(accessToken, userId)
            .firstOrNull { it.name == CloudBackupSerializer.LATEST_BACKUP_FILENAME || it.kind == "latest" }

        val metadataJson = buildMetadataJson(
            fileName = CloudBackupSerializer.LATEST_BACKUP_FILENAME,
            userId = userId,
            userEmail = userEmail,
            payload = payload,
            kind = "latest",
            includeParent = existing == null
        )

        if (existing != null) {
            // Update existing file in place to prevent duplicate backup-latest.json files
            val url = "$DRIVE_UPLOAD_URL/${existing.fileId}?uploadType=multipart&fields=id,name,modifiedTime,size,appProperties"
            val req = buildMultipartRequest(url, "PATCH", accessToken, metadataJson, payload.jsonString)
            parseFileObject(JSONObject(executeWithClassification(req)))
        } else {
            val url = "$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name,modifiedTime,size,appProperties"
            val req = buildMultipartRequest(url, "POST", accessToken, metadataJson, payload.jsonString)
            parseFileObject(JSONObject(executeWithClassification(req)))
        }
    }

    override suspend fun uploadHistorySnapshot(
        accessToken: String,
        fileName: String,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult
    ): CloudBackupFileInfo = withContext(Dispatchers.IO) {
        val metadataJson = buildMetadataJson(
            fileName = fileName,
            userId = userId,
            userEmail = userEmail,
            payload = payload,
            kind = "history",
            includeParent = true
        )
        val url = "$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name,modifiedTime,size,appProperties"
        val req = buildMultipartRequest(url, "POST", accessToken, metadataJson, payload.jsonString)
        parseFileObject(JSONObject(executeWithClassification(req)))
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val url = "$DRIVE_FILES_URL/$fileId?alt=media"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            executeWithClassification(req)
        }

    override suspend fun deleteBackup(accessToken: String, fileId: String): Unit =
        withContext(Dispatchers.IO) {
            val url = "$DRIVE_FILES_URL/$fileId"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            executeWithClassification(req)
        }

    private fun buildMetadataJson(
        fileName: String,
        userId: String,
        userEmail: String,
        payload: CloudBackupPayloadResult,
        kind: String,
        includeParent: Boolean
    ): String {
        val appProps = JSONObject().apply {
            put("auraBackup", "true")
            put("userId", userId)
            put("userEmail", userEmail)
            put("schemaVersion", CloudBackupSerializer.CURRENT_SCHEMA_VERSION.toString())
            put("appVersion", CloudBackupSerializer.APP_VERSION)
            put("checksum", payload.contentChecksumSha256)
            put("noteCount", payload.noteCount.toString())
            put("kind", kind)
        }
        return JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
            if (includeParent) {
                put("parents", JSONArray().put("appDataFolder"))
            }
            put("appProperties", appProps)
        }.toString()
    }

    private fun buildMultipartRequest(
        url: String,
        method: String,
        accessToken: String,
        metadataJson: String,
        contentJson: String
    ): Request {
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(contentJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .method(method, body)
            .build()
    }

    private fun parseFileObject(f: JSONObject): CloudBackupFileInfo {
        val props = f.optJSONObject("appProperties")
        return CloudBackupFileInfo(
            fileId = f.optString("id"),
            name = f.optString("name"),
            modifiedTimeMillis = CloudBackupSerializer.parseRfc3339Millis(f.optString("modifiedTime")),
            sizeBytes = f.optString("size", "0").toLongOrNull() ?: f.optLong("size", 0L),
            userId = props?.optString("userId", "") ?: "",
            userEmail = props?.optString("userEmail", "") ?: "",
            schemaVersion = props?.optString("schemaVersion", "2")?.toIntOrNull() ?: 2,
            checksumSha256 = props?.optString("checksum", "") ?: "",
            noteCount = props?.optString("noteCount", "0")?.toIntOrNull() ?: 0,
            kind = props?.optString("kind", "history") ?: "history",
            appVersion = props?.optString("appVersion", "2.0") ?: "2.0"
        )
    }

    private fun executeWithClassification(request: Request): String {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw DriveApiException.Network("No internet connection.")
        } catch (e: SocketTimeoutException) {
            throw DriveApiException.Network("Google Drive request timed out.")
        } catch (e: IOException) {
            throw DriveApiException.Network("Network error: ${e.localizedMessage ?: "Connection interrupted"}")
        }

        response.use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (resp.isSuccessful) return rawBody

            val apiMessage = extractGoogleApiErrorMessage(rawBody) ?: "HTTP ${resp.code}"
            when (resp.code) {
                401 -> throw DriveApiException.Unauthorized("Google Drive session expired: $apiMessage")
                403 -> {
                    val lower = rawBody.lowercase()
                    val isQuota = lower.contains("quota") || lower.contains("storagequotaexceeded")
                    val isApiDisabled = lower.contains("accessnotconfigured") || lower.contains("has not been used")
                    val msg = when {
                        isQuota -> "Google Drive storage quota exceeded."
                        isApiDisabled -> "Google Drive API is not enabled in Google Cloud Console for this project."
                        else -> "Google Drive permission denied or revoked: $apiMessage"
                    }
                    throw DriveApiException.QuotaOrPermission(msg, quota = isQuota || isApiDisabled)
                }
                429, in 500..599 -> throw DriveApiException.TransientServer("Google Drive temporarily unavailable (${resp.code}). Will retry automatically.")
                else -> throw DriveApiException.Other("Google Drive error (${resp.code}): $apiMessage")
            }
        }
    }

    private fun extractGoogleApiErrorMessage(rawBody: String): String? {
        return try {
            val err = JSONObject(rawBody).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
