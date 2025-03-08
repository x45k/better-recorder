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
                    saveAudioFile()
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

fun saveAudioFile() {
    val fileDialog = FileDialog(Frame(), "Save Recording", FileDialog.SAVE).apply {
        file = "recording.wav"
        isVisible = true
    }
    fileDialog.file?.let { filename ->
        val selectedFile = File(fileDialog.directory, filename)
        if (File("temp_recording.wav").exists()) {
            File("temp_recording.wav").copyTo(selectedFile, overwrite = true)
            File("temp_recording.wav").delete()
        }
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
            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)
            val audioInputStream = AudioInputStream(line)
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempFile)

            while (line?.isRunning == true) {
                val bytesRead = line?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    val amplitude = calculateAmplitude(buffer, bytesRead)
                    onAmplitudeUpdate(amplitude)
                }
            }

            audioInputStream.close()
        }.apply { start() }
    }

    fun stopRecording() {
        line?.stop()
        line?.close()
        recordingThread?.join()
    }

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        var max = 0
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)
            max = maxOf(max, abs(sample))
        }
        return max / 32768.0f
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Better Recorder") {
        App()
    }
}
