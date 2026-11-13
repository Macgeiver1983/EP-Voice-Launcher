package de.europlast.epvoice

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.view.ViewGroup

/** Displays a local diagnostic session; does not invoke ChatGPT. */
class EpVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        EpVoiceSession(this)
}

private class EpVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreateContentView(): android.view.View {
        Log.i("EPVoiceDiagnostic", "VoiceInteractionSession content created")
        return TextView(context).apply {
            text = "EP Voice: Android-Assistentensitzung gestartet.\nChatGPT wurde nicht geöffnet."
            textSize = 20f
            setPadding(36, 36, 36, 36)
            layoutParams = ViewGroup.LayoutParams(-1, -2)
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i("EPVoiceDiagnostic", "Session shown, source=" + args?.getString("source", "unknown"))
    }
}
