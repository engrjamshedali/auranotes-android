package com.example.ui.components

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.io.File

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
                recorder?.release()
                player?.stop()
                player?.release()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = {
        if (!isRecording) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isRecording) "Recording Voice Note..." else if (recordedFile != null) "Voice Note Ready" else "Record Voice Note",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Timer display
                Text(
                    text = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                // Main action button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording && recordedFile == null) {
                        FilledIconButton(
                            onClick = {
                                try {
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
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Start Recording", modifier = Modifier.size(32.dp))
                        }
                    } else if (isRecording) {
                        FilledIconButton(
                            onClick = {
                                try {
                                    recorder?.stop()
                                    recorder?.release()
                                    recorder = null
                                    isRecording = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isRecording = false
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Recording", modifier = Modifier.size(32.dp))
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
                                        val mp = MediaPlayer().apply {
                                            setDataSource(recordedFile!!.absolutePath)
                                            prepare()
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                            start()
                                        }
                                        player = mp
                                        isPlaying = true
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Preview Playback")
                        }
                    }
                }

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isRecording) {
                        Text("Cancel")
                    }
                    if (recordedFile != null && !isRecording) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            recordedFile?.let { file ->
                                onAudioRecorded(file, durationSeconds * 1000L)
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
