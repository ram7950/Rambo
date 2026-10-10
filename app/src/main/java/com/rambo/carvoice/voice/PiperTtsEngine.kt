package com.rambo.carvoice.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean

class PiperTtsEngine(
    private val context: Context,
    private val onEvent: (event: String, utteranceId: String?) -> Unit
) : TtsEngine {

    private data class Request(val text: String, val id: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val queue = LinkedBlockingDeque<Request>()
    private val released = AtomicBoolean(false)
    private val lock = Any()

    @Volatile private var ready = false
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var currentId: String? = null

    init {
        executor.execute {
            try {
                val modelDir = File(context.filesDir, "rambo-piper")
                copyAssets("tts", modelDir)

                val model = File(modelDir, "en_US-amy-low.onnx")
                val tokens = File(modelDir, "tokens.txt")
                val espeak = File(modelDir, "espeak-ng-data")

                check(model.isFile && model.length() > 0L) {
                    "Piper model missing"
                }
                check(tokens.isFile && espeak.isDirectory) {
                    "Piper voice data missing"
                }

                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            tokens = tokens.absolutePath,
                            dataDir = espeak.absolutePath
                        ),
                        numThreads = 1,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1
                )

                engine = OfflineTts(config = config)
                ready = true
                onEvent("ready", null)
                processQueue()
            } catch (e: Exception) {
                ready = false
                onEvent("error", "Piper initialization failed: ${e.message}")
            }
        }
    }

    private fun copyAssets(assetPath: String, target: File) {
        target.mkdirs()
        val entries = context.assets.list(assetPath)
            ?: throw IllegalStateException("Missing assets: $assetPath")

        for (name in entries) {
            val childPath = "$assetPath/$name"
            val childFile = File(target, name)
            val children = context.assets.list(childPath)

            if (!children.isNullOrEmpty()) {
                copyAssets(childPath, childFile)
            } else {
                if (!childFile.isFile || childFile.length() == 0L) {
                    context.assets.open(childPath).use { input ->
                        childFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    override fun speak(text: String) {
        speak(text, "piper_${System.nanoTime()}", false)
    }

    fun speak(text: String, utteranceId: String, flushQueue: Boolean = false) {
        if (text.isBlank() || released.get()) return

        synchronized(lock) {
            if (flushQueue) {
                queue.clear()
                audioTrack?.let {
                    try {
                        it.pause()
                        it.flush()
                        it.stop()
                    } catch (_: Exception) {
                    }
                }
            }
            queue.offerLast(Request(text, utteranceId))
        }
    }

    private fun processQueue() {
        while (!released.get()) {
            val request = try {
                queue.takeFirst()
            } catch (_: InterruptedException) {
                if (released.get()) break else continue
            }

            currentId = request.id
            try {
                val tts = engine ?: error("Piper engine unavailable")
                onEvent("start", request.id)

                val generated = tts.generate(request.text, speed = 0.92f)
                val samples = generated.samples
                check(samples.isNotEmpty()) { "Piper generated empty audio" }

                val pcm = ShortArray(samples.size) { i ->
                    (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }

                val minBuffer = AudioTrack.getMinBufferSize(
                    generated.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0) { "Unsupported audio sample rate" }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(generated.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                var offset = 0
                while (offset < pcm.size && !released.get()) {
                    val count = track.write(
                        pcm,
                        offset,
                        minOf(2048, pcm.size - offset)
                    )
                    if (count <= 0) break
                    offset += count
                }

                if (!released.get() && offset >= pcm.size) {
                    while (
                        !released.get() &&
                        track.playbackHeadPosition.toLong() < pcm.size.toLong()
                    ) {
                        Thread.sleep(20)
                    }
                }

                try {
                    track.stop()
                } catch (_: Exception) {
                }
                track.release()
                audioTrack = null

                if (!released.get()) onEvent("done", request.id)
            } catch (e: Exception) {
                try {
                    audioTrack?.release()
                } catch (_: Exception) {
                }
                audioTrack = null
                if (!released.get()) {
                    onEvent("utterance_error", "${request.id}: ${e.message}")
                }
            } finally {
                currentId = null
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            queue.clear()
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
            } catch (_: Exception) {
            }
        }
    }

    override fun isReady(): Boolean = ready && !released.get()

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        ready = false
        queue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        try {
            engine?.release()
        } catch (_: Exception) {
        }
        engine = null
        executor.shutdownNow()
    }
}
