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
        // Public deep link test. An accepted intent is NOT proof of an active voice session.
        val deepLink = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://chat.com/?mode=voice"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(ChatGptVoiceAccessibilityService.CHATGPT_PACKAGE)
        try {
            if (deepLink.resolveActivity(packageManager) != null) {
                startActivity(deepLink)
                recordHandoff("Voice-Deep-Link an ChatGPT übergeben; Sprachmodus noch nicht bestätigt")
            } else {
                recordHandoff("ChatGPT akzeptiert keinen passenden Voice-Deep-Link; Hauptansicht als Fallback")
                startActivity(launch)
            }
            finish()
        } catch (e: Exception) {
            recordHandoff("Voice-Deep-Link fehlgeschlagen: ${e.javaClass.simpleName}; Hauptansicht als Fallback")
            try {
                startActivity(launch)
                finish()
            } catch (fallback: Exception) {
                showFailure("ChatGPT konnte nicht geöffnet werden: ${fallback.javaClass.simpleName}")
            }
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
