package de.europlast.epvoice

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Diagnostic-only assistant session. Never launches ChatGPT or dismisses the keyguard. */
class EpVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EpVoiceSession(this)
}

private class EpVoiceSession(context: Context) : VoiceInteractionSession(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable {
        Log.i("EPVoiceDiagnostic", "Diagnostic session timed out; closing")
        finish()
    }

    override fun onCreateContentView(): View {
        Log.i("EPVoiceDiagnostic", "VoiceInteractionSession content created")
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            addView(TextView(context).apply {
                text = "EP Voice: Android-Assistentensitzung gestartet.\nChatGPT wurde nicht geöffnet.\nDie Diagnose schließt automatisch nach 8 Sekunden."
                textSize = 18f
            })
            addView(Button(context).apply {
                text = "Diagnose schließen"
                setOnClickListener { finish() }
            })
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i("EPVoiceDiagnostic", "Session shown, source=" + args?.getString("source", "unknown"))
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, 8_000L)
    }

    override fun onHide() {
        handler.removeCallbacks(autoClose)
        super.onHide()
    }

    override fun onBackPressed() {
        Log.i("EPVoiceDiagnostic", "Back pressed; closing diagnostic session")
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoClose)
        super.onDestroy()
    }
}
