package de.europlast.epvoice

import android.os.Bundle
import android.service.voice.VoiceInteractionService

/** System-owned assistant entry point; no third-party activity launch. */
class EpAssistantService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(3) // Never collect foreground app structure/screenshots.
        active = this
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
            .putString("assistant", "Systemassistent bereit").apply()
    }

    override fun onShutdown() {
        if (active === this) active = null
        super.onShutdown()
    }

    override fun onShowSessionFailed(args: Bundle) {
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
            .putString("assistant", "System konnte Sitzung nicht öffnen").apply()
    }

    companion object {
        @Volatile private var active: EpAssistantService? = null
        fun requestVoiceSession(wakeWord: String): Boolean {
            val service = active ?: return false
            return try {
                service.showSession(Bundle().apply { putString("wake_word", wakeWord) }, 0)
                true
            } catch (e: Exception) {
                service.getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
                    .putString("assistant", "Sitzungsanforderung fehlgeschlagen: ${e.javaClass.simpleName}").apply()
                false
            }
        }
    }
}
