package de.europlast.epvoice

import android.speech.RecognitionService
import android.content.Intent

/** Required registration for system assistant role; not a speech-to-text engine. */
class EpRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onStopListening(listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
}
