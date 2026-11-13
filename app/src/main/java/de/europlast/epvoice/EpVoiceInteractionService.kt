package de.europlast.epvoice

import android.service.voice.VoiceInteractionService
import android.os.Bundle
import android.util.Log

/** Diagnostic only. Requires explicit user selection as Android's assistant. */
class EpVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i("EPVoiceDiagnostic", "VoiceInteractionService ready")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.i("EPVoiceDiagnostic", "System invoked assistant from keyguard")
        showSession(Bundle().apply { putString("source", "keyguard") }, 0)
    }
}
