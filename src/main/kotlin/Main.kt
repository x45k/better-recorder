package dev.x45k.betterrecorder

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import javax.sound.sampled.*
import java.awt.FileDialog
import java.awt.Frame
import kotlin.math.abs

@Composable
@Preview
fun App() {
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf("wav") }
    var amplitudeData by remember { mutableStateOf(listOf<Float>()) }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                if (!isRecording) {
                    audioRecorder = AudioRecorder { amplitude ->
                        amplitudeData = (amplitudeData + amplitude).takeLast(50)
                    }.apply { startRecording() }
                } else {
                    audioRecorder?.stopRecording()
                    showFormatDialog = true
                    amplitudeData = listOf()
                }
                isRecording = !isRecording
            }) {
                Text(if (isRecording) "Stop Recording" else "Start Recording")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isRecording) {
                AudioWaveform(amplitudeData)
            }
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Choose File Format") },
            text = {
                Column {
                    Button(onClick = {
                        selectedFormat = "wav"
                        showFormatDialog = false
                        saveAudioFile(selectedFormat)
                    }) { Text("WAV") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        selectedFormat = "mp3"
                        showFormatDialog = false
                        saveAudioFile(selectedFormat)
                    }) { Text("MP3") }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun AudioWaveform(amplitudes: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(50.dp)) {
        val barWidth = size.width / amplitudes.size
        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = amplitude * size.height
            drawLine(
                color = Color.Blue,
                start = androidx.compose.ui.geometry.Offset(index * barWidth, size.height / 2 - barHeight / 2),
                end = androidx.compose.ui.geometry.Offset(index * barWidth, size.height / 2 + barHeight / 2),
                strokeWidth = barWidth / 2
            )
        }
    }
}

fun saveAudioFile(format: String) {
    val fileDialog = FileDialog(Frame(), "Save Recording", FileDialog.SAVE).apply {
        file = "recording.$format"
        isVisible = true
    }
    fileDialog.file?.let { filename ->
        val selectedFile = File(fileDialog.directory, filename)
        File("temp_recording.wav").renameTo(selectedFile)
    }
}

class AudioRecorder(private val onAmplitudeUpdate: (Float) -> Unit) {
    private val format = AudioFormat(44100.0f, 16, 1, true, true)
    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private val tempFile = File("temp_recording.wav")

    fun startRecording() {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        line = AudioSystem.getLine(info) as TargetDataLine
        line?.open(format)
        line?.start()

        recordingThread = Thread {
            val buffer = ByteArray(1024)
            val audioStream = AudioInputStream(line)
            while (line?.isOpen == true) {
                val bytesRead = audioStream.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val amplitude = abs(buffer.maxOrNull()?.toFloat() ?: 0f) / 128f
                    onAmplitudeUpdate(amplitude)
                }
            }
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, tempFile)
        }.apply { start() }
    }

    fun stopRecording() {
        line?.stop()
        line?.close()
        recordingThread?.join()
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Better Recorder") {
        App()
    }
}
