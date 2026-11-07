package de.europlast.epvoice

import android.app.*
import android.content.*
import android.os.*
import com.openwakeword.OpenWakeWord

/** Development integration. Requires device-side build and test. */
class WakeService : Service() {
    private var detector: OpenWakeWord? = null
    private val channel = "ep_voice"
    private var lastTrigger = 0L
    private var failed = false
    private var handedOff = false
    private fun debug(key: String, value: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString(key, value).apply() }
    private fun status(s: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("state",s).apply() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"EP Voice Dauererkennung",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (detector != null) return START_NOT_STICKY
        failed = false
        handedOff = false
        debug("engine", "Initialisierung läuft")
        debug("events", "0")
        debug("events_jarvis", "0")
        debug("events_ep", "0")
        debug("error", "Keine")
        debug("phrase", "(noch nichts erkannt)")
        debug("score_jarvis", "–")
        debug("score_ep", "–")
        debug("peak_ep", "–")
        debug("frames_ep", "–")
        debug("handoff", "Bereit")
        debug("model_error", "")
        val stop = PendingIntent.getService(this,1,Intent(this,WakeService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("EP Voice – Offline-Wakewords").setContentText("Lokale Erkennung aktiv")
            .setOngoing(true).addAction(Notification.Action.Builder(null,"Stoppen",stop).build()).build()
        startForeground(1001,n)
        try {
            debug("engine", "Modell wird geladen")
            detector = OpenWakeWord.Builder(this).setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .addModelAsset("openwakeword/hey_ep.onnx", "Hey EP", modelThreshold = 0.95f, optional = true, consecutiveFrames = 4)
                .setThreshold(0.5f).setDebounceMs(3000L).build()
            detector?.onError = { error ->
                failed = true
                debug("engine", "FEHLER im Audio-/Modell-Thread")
                debug("error", "${error.javaClass.simpleName}: ${error.message}; ${error.cause?.message ?: ""}")
                status("Erkennung fehlgeschlagen – siehe Fehleranzeige")
                stopSelf()
            }
            detector?.onOptionalModelError = { label, detail ->
                debug("model_error", "$label nicht verfügbar: $detail")
            }
            detector?.onModelProgress = { scores ->
                fun formatScore(label: String): String = when {
                    label !in scores -> "inaktiv"
                    else -> scores[label]?.latest?.let { "%.3f".format(it) } ?: "–"
                }
                val ep = scores["Hey EP"]
                getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
                    .putString("score_jarvis", formatScore("Hey Jarvis"))
                    .putString("score_ep", formatScore("Hey EP"))
                    .putString("peak_ep", ep?.peak?.let { "%.3f".format(it) } ?: "–")
                    .putString("frames_ep", ep?.longestConfirmation?.toString() ?: "–")
                    .apply()
            }
            detector?.onAudioProgress = { frames, peak ->
                debug("engine", "Audio aktiv: $frames Blöcke; Pegel %.3f".format(peak))
            }
            debug("engine", "Modellobjekt erstellt; Audio-Start läuft")
            detector?.startNamed { wakeWord, score ->
                val prefs = getSharedPreferences("ep_debug", MODE_PRIVATE)
                val count = (prefs.getString("events", "0")?.toIntOrNull() ?: 0) + 1
                val modelCounter = if (wakeWord == "Hey EP") "events_ep" else "events_jarvis"
                val modelCount = (prefs.getString(modelCounter, "0")?.toIntOrNull() ?: 0) + 1
                prefs.edit().putString("events", count.toString())
                    .putString(modelCounter, modelCount.toString())
                    .putString("last_score", score.toString())
                    .putString("last_model", wakeWord).apply()
                debug("phrase", "$wakeWord (Score: $score)")
                val now = SystemClock.elapsedRealtime()
                if (now-lastTrigger > 5000) {
                    lastTrigger = now
                    if (MainActivity.tryOpenChatGpt()) {
                        status("$wakeWord erkannt – ChatGPT wird geöffnet")
                        getSystemService(NotificationManager::class.java).cancel(1002)
                    } else {
                        status("$wakeWord erkannt – Benachrichtigung öffnen")
                        showLaunchNotification(wakeWord)
                    }
                }
            }
            debug("engine", "start() zurückgekehrt; Audiofluss nicht unabhängig bestätigt")
            status("Engine gestartet; Audiofluss noch nicht bestätigt")
        } catch (e: Exception) {
            failed = true
            debug("engine", "FEHLER")
            debug("error", "${e.javaClass.simpleName}: ${e.message}")
            status("Modellfehler – siehe Fehleranzeige")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }
    private fun showLaunchNotification(wakeWord: String) {
        if (packageManager.getLaunchIntentForPackage("com.openai.chatgpt") == null) {
            status("ChatGPT nicht installiert oder nicht sichtbar")
            return
        }
        val handoff = Intent(this, ChatGptHandoffActivity::class.java)
        val pending = PendingIntent.getActivity(this, 2, handoff,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$wakeWord erkannt").setContentText("Tippen für ChatGPT und Mikrofonfreigabe")
            .setContentIntent(pending).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(1002,notification)
    }
    inner class HandoffBinder : Binder() {
        fun releaseMicrophone() {
            handedOff = true
            releaseDetector()
            debug("engine", "Mikrofon freigegeben")
            status("Erkennung pausiert – zum Fortsetzen STARTEN")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun releaseDetector() {
        detector?.release()
        detector = null
    }

    override fun onDestroy() {
        releaseDetector()
        if (!failed && !handedOff) {
            debug("engine", "Mikrofon inaktiv")
            status("Erkennung gestoppt")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = HandoffBinder()
}
