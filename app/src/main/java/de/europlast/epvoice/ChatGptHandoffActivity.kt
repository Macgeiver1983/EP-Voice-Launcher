package de.europlast.epvoice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/** Releases the microphone before starting the requested ChatGPT activity. */
class ChatGptHandoffActivity : Activity() {
    companion object {
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val MICROPHONE_SETTLE_MS = 400L
    }

    private var launchIntent: Intent? = null
    private var bound = false
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val launchAfterRelease = Runnable {
        val launch = launchIntent
        if (launch != null && resumed && !isFinishing) launchChatGpt(launch)
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (launchIntent == null || !resumed || isFinishing) return
            (service as WakeService.HandoffBinder).releaseMicrophone()
            handler.postDelayed(launchAfterRelease, MICROPHONE_SETTLE_MS)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            showFailure("Mikrofonübergabe unterbrochen. Bitte erneut versuchen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
        if (launchIntent == null) showFailure("ChatGPT-App nicht gefunden.")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (launchIntent == null || isFinishing) return
        // Binding alone creates no audio capture, even with the recognizer stopped.
        bound = bindService(Intent(this, WakeService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) showFailure("Mikrofonübergabe fehlgeschlagen. Bitte erneut versuchen.")
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(launchAfterRelease)
        if (bound) {
            unbindService(connection)
            bound = false
        }
        if (!isFinishing) {
            recordHandoff("Übergabe unterbrochen")
            finish()
        }
        super.onPause()
    }

    private fun launchChatGpt(launch: Intent) {
        try {
            startActivity(launch)
            recordHandoff("ChatGPT geöffnet – Sprachstart über die ChatGPT-Einstellung")
            finish()
        } catch (_: ActivityNotFoundException) {
            showFailure("Das angeforderte ChatGPT-Ziel ist nicht verfügbar.")
        } catch (_: SecurityException) {
            showFailure("Android hat das Öffnen des ChatGPT-Ziels blockiert.")
        }
    }

    private fun recordHandoff(message: String) {
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("handoff", message).apply()
    }

    private fun showFailure(message: String) {
        recordHandoff(message)
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("state", "ChatGPT-Start fehlgeschlagen").apply()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
