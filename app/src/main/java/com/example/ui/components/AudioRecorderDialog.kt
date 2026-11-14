package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AudioRecorderDialog(
    onDismiss: () -> Unit,
    onAudioRecorded: (File, Long) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startRecordingInternal() {
        try {
            errorMessage = null
            val tempFile = File(context.cacheDir, "temp_rec_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(tempFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recordedFile = tempFile
            isRecording = true
        } catch (e: SecurityException) {
            Log.e("AudioRecorderDialog", "Microphone permission denied", e)
            errorMessage = "Microphone permission is required to record voice notes."
        } catch (e: Exception) {
            Log.e("AudioRecorderDialog", "Failed to start MediaRecorder", e)
            errorMessage = "Unable to access microphone on this device: ${e.localizedMessage ?: "Audio hardware unavailable"}"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingInternal()
        } else {
            errorMessage = "Microphone permission was denied. Grant audio access in Settings to record voice notes."
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            durationSeconds = 0
            while (isRecording) {
                delay(1000)
                durationSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
            } catch (_: Exception) {}
            try {
                recorder?.release()
            } catch (_: Exception) {}
            try {
                player?.stop()
            } catch (_: Exception) {}
            try {
                player?.release()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = {
        if (!isRecording) {
            recordedFile?.delete()
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when {
                        isRecording -> "Recording Voice Note..."
                        recordedFile != null -> "Voice Note Ready"
                        else -> "Record Voice Note"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Timer display
                Text(
                    text = String.format(Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                errorMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Main action button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording && recordedFile == null) {
                        FilledIconButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    startRecordingInternal()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Start Recording",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (isRecording) {
                        FilledIconButton(
                            onClick = {
                                try {
                                    recorder?.stop()
                                } catch (e: Exception) {
                                    Log.w("AudioRecorderDialog", "MediaRecorder stop warning", e)
                                } finally {
                                    try {
                                        recorder?.release()
                                    } catch (_: Exception) {}
                                    recorder = null
                                    isRecording = false
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop Recording",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (recordedFile != null) {
                        // Play preview
                        FilledIconButton(
                            onClick = {
                                try {
                                    if (isPlaying) {
                                        player?.stop()
                                        player?.release()
                                        player = null
                                        isPlaying = false
                                    } else {
                                        val file = recordedFile
                                        if (file != null && file.exists()) {
                                            val mp = MediaPlayer().apply {
                                                setDataSource(file.absolutePath)
                                                prepare()
                                                setOnCompletionListener {
                                                    isPlaying = false
                                                }
                                                start()
                                            }
                                            player = mp
                                            isPlaying = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("AudioRecorderDialog", "Audio preview playback failed", e)
                                    errorMessage = "Playback failed: ${e.localizedMessage ?: "Audio error"}"
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Preview Playback"
                            )
                        }
                    }
                }

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            recordedFile?.delete()
                            onDismiss()
                        },
                        enabled = !isRecording
                    ) {
                        Text("Cancel")
                    }
                    if (recordedFile != null && !isRecording) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            recordedFile?.let { file ->
                                onAudioRecorded(file, durationSeconds.coerceAtLeast(1) * 1000L)
                            }
                        }) {
                            Text("Attach Voice Note")
                        }
                    }
                }
            }
        }
    }
}
