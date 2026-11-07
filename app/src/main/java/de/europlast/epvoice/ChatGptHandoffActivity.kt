package de.europlast.epvoice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast

/** Opened by a user tap. The service releases the microphone before we launch ChatGPT. */
class ChatGptHandoffActivity : Activity() {
    private var launchIntent: Intent? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val launch = launchIntent ?: return
            if (isFinishing) return
            (service as WakeService.HandoffBinder).releaseMicrophone()
            launchChatGpt(launch)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            showFailure("Mikrofonübergabe unterbrochen. Bitte erneut versuchen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (launchIntent == null) {
            showFailure("ChatGPT nicht gefunden")
            return
        }
        // Binding alone creates no audio capture. This also handles a tap after the service stopped.
        bound = bindService(Intent(this, WakeService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) showFailure("Mikrofonübergabe fehlgeschlagen. Bitte erneut versuchen.")
    }

    override fun onDestroy() {
        launchIntent = null
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    private fun launchChatGpt(launch: Intent) {
        try {
            startActivity(launch)
            finish()
        } catch (_: ActivityNotFoundException) {
            showFailure("ChatGPT konnte nicht geöffnet werden")
        } catch (_: SecurityException) {
            showFailure("Android hat das Öffnen von ChatGPT blockiert")
        }
    }

    private fun showFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
