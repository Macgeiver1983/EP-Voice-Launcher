package de.europlast.epvoice

import android.app.Activity
import android.app.KeyguardManager
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
    private var launchIntent: Intent? = null
    private var bound = false
    private var resumed = false
    private var handoffStarted = false
    private var launchPending = false
    private val handler = Handler(Looper.getMainLooper())
    private val launchAfterRelease = Runnable {
        val launch = launchIntent
        if (launch != null && resumed && !isFinishing && launchPending) {
            launchPending = false
            launchChatGpt(launch)
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (launchIntent == null || !resumed || isFinishing) return
            val binder = service as WakeService.HandoffBinder
            binder.releaseMicrophone()
            recordHandoff("Mikrofon freigegeben; direkter Sprachstart folgt")
            launchPending = true
            handler.postDelayed(launchAfterRelease, ChatGptVoiceAccessibilityService.MICROPHONE_SETTLE_MS)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            showFailure("Mikrofonübergabe unterbrochen. Bitte erneut versuchen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        launchIntent = packageManager.getLaunchIntentForPackage(ChatGptVoiceAccessibilityService.CHATGPT_PACKAGE)
        if (launchIntent == null) showFailure("ChatGPT-App nicht gefunden.")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (launchPending) {
            handler.postDelayed(launchAfterRelease, ChatGptVoiceAccessibilityService.MICROPHONE_SETTLE_MS)
            return
        }
        if (launchIntent == null || isFinishing || handoffStarted) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val keyguardShowing = keyguard.isKeyguardLocked
        val deviceLocked = keyguard.isDeviceLocked
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
            .putString("keyguard_diagnostic",
                "handoff: keyguardShowing=$keyguardShowing, deviceLocked=$deviceLocked")
            .apply()
        recordHandoff("Sperrbildschirm-Test: sichtbar=$keyguardShowing, Authentifizierung nötig=$deviceLocked")
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    recordHandoff("Sperrbildschirm automatisch geschlossen")
                    startHandoff()
                }
                override fun onDismissCancelled() {
                    recordHandoff("Sperrbildschirm-Schließen abgebrochen")
                    finish()
                }
                override fun onDismissError() {
                    recordHandoff("Sperrbildschirm-Schließen von Android abgelehnt")
                    finish()
                }
            })
        } else {
            startHandoff()
        }
    }

    private fun startHandoff() {
        if (!resumed || isFinishing || handoffStarted) return
        handoffStarted = true
        // Erst nach bestätigtem Dismissal an ChatGPT übergeben.
        bound = bindService(Intent(this, WakeService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) showFailure("Mikrofonübergabe fehlgeschlagen. Bitte erneut versuchen.")
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(launchAfterRelease)
        // A temporary pause during keyguard dismissal must not abort the handoff.
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(launchAfterRelease)
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun launchChatGpt(launch: Intent) {
        try {
            val voice = ComponentName("com.openai.chatgpt", "com.openai.voice.assistant.AssistantActivity")
            val info = try {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(voice, 0)
            } catch (e: Exception) {
                recordHandoff("Voice-Activity nicht gefunden: ${e.javaClass.simpleName}; Hauptansicht als Fallback")
                null
            }
            if (info == null || !info.exported ||
                (info.permission != null && checkSelfPermission(info.permission) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                if (info != null) recordHandoff("Voice-Activity nicht für EP Voice freigegeben; Hauptansicht als Fallback")
                startActivity(launch)
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).setComponent(voice).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    recordHandoff("Direkte Voice-Activity angefordert; aktive Sprachsitzung nicht bestätigt")
                } catch (e: Exception) {
                    recordHandoff("Direkter Voice-Aufruf gescheitert: ${e.javaClass.simpleName}; Hauptansicht als Fallback")
                    startActivity(launch)
                }
            }
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
