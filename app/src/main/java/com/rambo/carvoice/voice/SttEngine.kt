package com.rambo.carvoice.voice

interface SttEngine {

    fun startListening()

    fun stopListening()

    fun isReady(): Boolean

    fun release()
}
