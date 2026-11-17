package de.europlast.epvoice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.service.voice.VoiceInteractionService

/** Android-selected assistant; owns the session shown after a wakeword. */
class EpVoiceInteractionService : VoiceInteractionService() {
    companion object {
        const val ACTION_WAKEWORD_SESSION = "de.europlast.epvoice.action.WAKEWORD_SESSION"
        const val EXTRA_WAKEWORD = "wakeword"
    }

    override fun onReady() {
        super.onReady()
        Log.i("EPVoiceDiagnostic", "VoiceInteractionService ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WAKEWORD_SESSION) {
            val phrase = intent.getStringExtra(EXTRA_WAKEWORD) ?: "unknown"
            Log.i("EPVoiceDiagnostic", "Wakeword requested assistant session: $phrase")
            try {
                showSession(Bundle().apply {
                    putString("source", "wakeword")
                    putString("wakeword", phrase)
                }, 0)
            } catch (e: RuntimeException) {
                Log.e("EPVoiceDiagnostic", "Assistant session request failed", e)
                getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
                    .putString("assistant_session", "Fehler: ${e.javaClass.simpleName}: ${e.message}")
                    .apply()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.i("EPVoiceDiagnostic", "System invoked assistant from keyguard")
        showSession(Bundle().apply { putString("source", "keyguard") }, 0)
    }
}
