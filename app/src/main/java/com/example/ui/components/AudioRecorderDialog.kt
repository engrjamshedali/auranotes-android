package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.theme.LocalAuraAccessibility
import com.example.ui.theme.LocalAuraNeumorphicPalette
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AudioRecorderDialog(
    onDismiss: () -> Unit,
    onAudioRecorded: (File, Long) -> Unit
) {
    val context = LocalContext.current
    val neu = LocalAuraNeumorphicPalette.current
    val a11y = LocalAuraAccessibility.current

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
            errorMessage = "Unable to access microphone: ${e.localizedMessage ?: "Audio hardware unavailable"}"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingInternal()
        } else {
            errorMessage = "Microphone permission denied. Grant audio access in Settings."
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
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            try { player?.stop() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording && !a11y.reducedMotion) 1.16f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_scale"
    )

    Dialog(onDismissRequest = {
        if (!isRecording) {
            recordedFile?.delete()
            onDismiss()
        }
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .neuRaised(
                    cornerRadius = 28.dp,
                    backgroundColor = neu.surface,
                    shadowOffset = 8.dp,
                    blurRadius = 16.dp
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                    Text(
                        text = when {
                            isRecording -> "Recording"
                            recordedFile != null -> "Voice Memo Ready"
                            else -> "Voice Memo"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Recessed Neumorphic Timer Cavity
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .neuInset(
                            cornerRadius = 18.dp,
                            backgroundColor = neu.surfaceRecessed,
                            shadowOffset = 3.5.dp,
                            blurRadius = 7.dp
                        )
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace),
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

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

                // Embossed Circular Control Well
                if (!isRecording && recordedFile == null) {
                    AuraNeuIconButton(
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
                        size = 72.dp,
                        cornerRadius = 36.dp,
                        isAccent = true
                    ) {
                        AuraIcon(
                            AuraIcons.Mic,
                            contentDescription = "Start Recording",
                            size = 28.dp,
                            tint = Color.White
                        )
                    }
                } else if (isRecording) {
                    AuraNeuIconButton(
                        onClick = {
                            try {
                                recorder?.stop()
                            } catch (e: Exception) {
                                Log.w("AudioRecorderDialog", "MediaRecorder stop warning", e)
                            } finally {
                                try { recorder?.release() } catch (_: Exception) {}
                                recorder = null
                                isRecording = false
                            }
                        },
                        size = 72.dp,
                        cornerRadius = 36.dp,
                        tint = MaterialTheme.colorScheme.error
                    ) {
                        AuraIcon(
                            AuraIcons.Square,
                            contentDescription = "Stop Recording",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (recordedFile != null) {
                    AuraNeuIconButton(
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
                                            setOnCompletionListener { isPlaying = false }
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
                        size = 64.dp,
                        cornerRadius = 32.dp,
                        isActive = isPlaying
                    ) {
                        AuraIcon(
                            if (isPlaying) AuraIcons.Square else AuraIcons.Play,
                            contentDescription = "Preview Playback",
                            size = AuraIconSize.Card,
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                        Spacer(Modifier.width(10.dp))
                        AuraNeuButton(
                            onClick = {
                                recordedFile?.let { file ->
                                    onAudioRecorded(file, durationSeconds.coerceAtLeast(1) * 1000L)
                                }
                            },
                            isAccent = true
                        ) {
                            Text("Attach Voice Note", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
