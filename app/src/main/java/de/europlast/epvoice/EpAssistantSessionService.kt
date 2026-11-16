package de.europlast.epvoice

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.os.Bundle
import android.content.Context
import android.view.View
import android.widget.TextView

class EpAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EpAssistantSession(this)
}

class EpAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreateContentView(): View = TextView(context).apply {
        text = "EP Voice – Sprachverbindung noch nicht eingerichtet"
        textSize = 18f
        setPadding(32, 48, 32, 48)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        context.getSharedPreferences("ep_debug", Context.MODE_PRIVATE).edit()
            .putString("assistant", "Eigene Sitzung geöffnet: ${args?.getString("wake_word") ?: "Systemaufruf"}").apply()
        // Realtime audio transport will be connected here only after secure token provisioning.
    }
}
