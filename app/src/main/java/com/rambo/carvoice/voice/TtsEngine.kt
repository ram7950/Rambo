package com.rambo.carvoice.voice

interface TtsEngine {

    fun speak(text: String)

    fun stop()

    fun isReady(): Boolean

    fun release()
}
