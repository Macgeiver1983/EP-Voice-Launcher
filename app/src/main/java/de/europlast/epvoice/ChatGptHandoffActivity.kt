package de.europlast.epvoice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/** Releases the microphone before starting the requested ChatGPT activity. */
class ChatGptHandoffActivity : Activity() {
    companion object {
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val EXTRA_VOICE = "voice"
        private const val VOICE_ACTIVITY = "com.openai.voice.assistant.AssistantActivity"
        private const val MICROPHONE_SETTLE_MS = 400L

        @JvmStatic
        fun voiceIntent(context: Context): Intent =
            Intent(context, ChatGptHandoffActivity::class.java).putExtra(EXTRA_VOICE, true)
    }

    private var launchIntent: Intent? = null
    private var bound = false
    private var voice = false
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val launchAfterRelease = Runnable {
        val launch = launchIntent
        if (launch != null && resumed && !isFinishing) launchChatGpt(launch)
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val launch = launchIntent ?: return
            if (!resumed || isFinishing) return
            (service as WakeService.HandoffBinder).releaseMicrophone()
            if (voice) {
                handler.postDelayed(launchAfterRelease, MICROPHONE_SETTLE_MS)
            } else {
                launchChatGpt(launch)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            showFailure("Mikrofonübergabe unterbrochen. Bitte erneut versuchen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = intent.getBooleanExtra(EXTRA_VOICE, false)
        launchIntent = if (voice) findVoiceIntent() else packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
        if (launchIntent == null) {
            val installed = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE) != null
            showFailure(if (installed && voice) "Direkter Sprachmodus in dieser ChatGPT-Version nicht verfügbar."
                else "ChatGPT-App nicht gefunden.")
        }
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

    private fun findVoiceIntent(): Intent? {
        val component = ComponentName(CHATGPT_PACKAGE, VOICE_ACTIVITY)
        // Experimental component. Check whether the installed ChatGPT exposes it to this app.
        val info = try {
            @Suppress("DEPRECATION")
            packageManager.getActivityInfo(component, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled) return null
        val permission = info.permission
        if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return null
        return Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun launchChatGpt(launch: Intent) {
        try {
            startActivity(launch)
            recordHandoff(if (voice) "Sprachmodus angefordert" else "App-Start angefordert")
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
