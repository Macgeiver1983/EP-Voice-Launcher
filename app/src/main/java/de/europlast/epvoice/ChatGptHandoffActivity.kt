package de.europlast.epvoice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/** Hands microphone to ChatGPT, then tries a direct voice activity and a scoped voice link. */
class ChatGptHandoffActivity : Activity() {
    private var bound = false
    private val trace = StringBuilder()
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val startVoice = Runnable { if (resumed && !isFinishing) launchVoice() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!resumed || isFinishing) return
            (service as WakeService.HandoffBinder).releaseMicrophone()
            handler.postDelayed(startVoice, 400L)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            if (!isFinishing) showFailure("Mikrofonübergabe unterbrochen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.getLaunchIntentForPackage("com.openai.chatgpt") == null)
            showFailure("ChatGPT-App nicht gefunden.")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (isFinishing) return
        bound = bindService(Intent(this, WakeService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) showFailure("Mikrofonübergabe fehlgeschlagen.")
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(startVoice)
        if (bound) { unbindService(connection); bound = false }
        if (!isFinishing) finish()
        super.onPause()
    }

    private fun launchVoice() {
        val component = ComponentName("com.openai.chatgpt", "com.openai.voice.assistant.AssistantActivity")
        note("ACTION_ASSIST disabled: device test confirmed no active voice conversation")
        try {
            val info = packageManager.getActivityInfo(component, 0)
            note("Direktziel: exported=${info.exported}, permission=${info.permission ?: "keine"}")
            if (info.exported && info.permission == null) {
                startActivity(Intent().setComponent(component))
                note("Direkter ChatGPT-Sprachstart angefordert; Erfolg nicht bestätigt")
                finish()
                return
            }
        } catch (e: Exception) {
            note("Direktstart fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message ?: "ohne Meldung"}")
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.com/?mode=voice"))
                .setPackage("com.openai.chatgpt"))
            note("Sprachlink gestartet; tatsächlicher Sprachmodus nicht bestätigt")
            finish()
        } catch (e: ActivityNotFoundException) {
            note("Sprachlink: ActivityNotFoundException")
            fallback()
        } catch (e: SecurityException) {
            note("Sprachlink: SecurityException: ${e.message ?: "ohne Meldung"}")
            fallback()
        }
    }

    private fun fallback() {
        val intent = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (intent == null) { showFailure("ChatGPT nicht verfügbar."); return }
        try {
            startActivity(intent)
            note("Fallback: nur ChatGPT-Hauptfenster geöffnet")
            finish()
        } catch (_: Exception) {
            showFailure("ChatGPT konnte nicht gestartet werden.")
        }
    }

    private fun note(message: String) {
        trace.append(message).append("\\n")
        record(message)
    }

    private fun record(message: String) {
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("voice_trace", trace.toString()).apply()
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("handoff", message).apply()
    }
    private fun showFailure(message: String) {
        record(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
