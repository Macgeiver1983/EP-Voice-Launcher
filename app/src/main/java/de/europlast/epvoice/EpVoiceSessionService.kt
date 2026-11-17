package de.europlast.epvoice

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
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
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var screenWakeLock: PowerManager.WakeLock? = null
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
        val source = args?.getString("source", "unknown") ?: "unknown"
        Log.i("EPVoiceDiagnostic", "Session shown, source=$source")
        if (source == "wakeword") {
            window.window?.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            // Short-lived diagnostic wake request; does not unlock the device.
            try {
                @Suppress("DEPRECATION")
                val flags = PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
                screenWakeLock = powerManager.newWakeLock(flags, "EPVoice:WakewordDisplay").apply {
                    acquire(2_000L)
                }
                Log.i("EPVoiceDiagnostic", "Wakeword display wake requested")
            } catch (e: RuntimeException) {
                Log.e("EPVoiceDiagnostic", "Display wake request failed", e)
            }
        }
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, 8_000L)
    }

    override fun onHide() {
        releaseScreenWakeLock()
        handler.removeCallbacks(autoClose)
        super.onHide()
    }

    override fun onBackPressed() {
        Log.i("EPVoiceDiagnostic", "Back pressed; closing diagnostic session")
        finish()
    }

    private fun releaseScreenWakeLock() {
        screenWakeLock?.let { if (it.isHeld) it.release() }
        screenWakeLock = null
    }

    override fun onDestroy() {
        releaseScreenWakeLock()
        handler.removeCallbacks(autoClose)
        super.onDestroy()
    }
}
