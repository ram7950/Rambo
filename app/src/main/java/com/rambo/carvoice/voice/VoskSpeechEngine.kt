package com.rambo.carvoice.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread

class VoskSpeechEngine(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStatus: (String) -> Unit
) : SttEngine {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var listening = false
    private var ready = false
    private var resultDelivered = false

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        thread {
            try {
                onStatus("LOADING STT")

                val modelPath = copyModelFromAssets()

                model = Model(modelPath)
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

                ready = true
                onStatus("STT READY")

            } catch (e: Exception) {
                ready = false
                onStatus("STT ERROR: ${e.message}")
            }
        }
    }

    private fun copyModelFromAssets(): String {
        val targetDir = context.filesDir.resolve("vosk-model-en-us")

        if (!targetDir.exists()) {
            targetDir.mkdirs()
            copyAssetFolder("model-en-us", targetDir)
        }

        return targetDir.absolutePath
    }

    private fun copyAssetFolder(assetPath: String, targetDir: java.io.File) {
        val files = context.assets.list(assetPath) ?: emptyArray()

        if (files.isEmpty()) {
            val targetFile = java.io.File(targetDir, assetPath.substringAfterLast("/"))

            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return
        }

        for (file in files) {
            val childAsset = "$assetPath/$file"
            val childTarget = java.io.File(targetDir, file)

            if (context.assets.list(childAsset)?.isNotEmpty() == true) {
                childTarget.mkdirs()
                copyAssetFolder(childAsset, childTarget)
            } else {
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun startListening() {
        if (!ready) {
            onStatus("STT NOT READY")
            return
        }

        if (listening) {
            return
        }

        try {
            resultDelivered = false

            val minBuffer =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            val bufferSize = maxOf(minBuffer, BUFFER_SIZE)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            listening = true
            onStatus("LISTENING")

            recordingThread = thread {
                val buffer = ShortArray(bufferSize / 2)

                while (listening) {
                    val count =
                        audioRecord?.read(
                            buffer,
                            0,
                            buffer.size
                        ) ?: 0

                    if (count > 0) {
                        val accepted =
                            recognizer?.acceptWaveForm(
                                buffer,
                                count
                            ) ?: false

                        if (accepted && !resultDelivered) {
                            val text = extractText(
                                recognizer?.result
                            )

                            if (text.isNotBlank()) {
                                resultDelivered = true

                                listening = false

                                try {
                                    audioRecord?.stop()
                                } catch (_: Exception) {
                                }

                                onStatus("HEARD: $text")
                                onStatus("HEARD: $text")
                                onResult(text)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            listening = false
            onStatus("MIC ERROR: ${e.message}")
        }
    }

    override fun stopListening() {
        if (!listening) {
            return
        }

        listening = false
        resultDelivered = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        recordingThread = null

        val finalText =
            extractText(
                recognizer?.finalResult
            )

        if (finalText.isNotBlank()) {
            onResult(finalText)
        }

        onStatus("STT READY")
    }

    private fun extractText(json: String?): String {
        if (json.isNullOrBlank()) {
            return ""
        }

        return try {
            JSONObject(json)
                .optString("text", "")
                .trim()
        } catch (_: Exception) {
            ""
        }
    }

    override fun isReady(): Boolean {
        return ready
    }

    override fun release() {
        listening = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        audioRecord?.release()
        audioRecord = null

        recognizer?.close()
        recognizer = null

        model?.close()
        model = null

        ready = false
    }
}
