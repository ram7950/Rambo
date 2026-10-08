package com.rambo.carvoice.voice

class VoiceEngineManager(
    private val sttEngine: SttEngine?,
    private val ttsEngine: TtsEngine?
) {

    fun startListening() {
        sttEngine?.startListening()
    }

    fun stopListening() {
        sttEngine?.stopListening()
    }

    fun speak(text: String) {
        ttsEngine?.speak(text)
    }

    fun isSttReady(): Boolean {
        return sttEngine?.isReady() == true
    }

    fun isTtsReady(): Boolean {
        return ttsEngine?.isReady() == true
    }

    fun release() {
        sttEngine?.release()
        ttsEngine?.release()
    }
}
