package de.europlast.epvoice

import android.app.*
import android.content.*
import android.os.*
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.AudioManager.AudioRecordingCallback
import android.app.KeyguardManager
import android.os.PowerManager
import android.content.pm.PackageManager
import com.openwakeword.OpenWakeWord

/** Development integration. Requires device-side build and test. */
class WakeService : Service() {
    private var detector: OpenWakeWord? = null
    private var lastLogAt = 0L
    private var audioFrames = 0L
    private var lastAudioAt = 0L
    private var lastAudioPeak = 0f
    private var recordingCallbackRegistered = false
    private val audioCallback = object : AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val own = configs.filter { it.clientAudioSessionId != 0 && it.clientAudioSource == android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION }
            debug("audio_configs", "Aufnahmen gesamt=${configs.size}, VOICE_RECOGNITION=${own.size}, stumm=${own.count { it.isClientSilenced }}")
            snapshot("Audio-Konfiguration geändert")
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            snapshot("Bildschirmereignis: ${intent?.action?.substringAfterLast('.')}")
        }
    }
    private val diagnosticTick = object : Runnable {
        override fun run() {
            snapshot("Statusprüfung")
            handler.postDelayed(this, 3000L)
        }
    }
    private fun snapshot(event: String) {
        val power = getSystemService(PowerManager::class.java)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val age = if (lastAudioAt == 0L) "noch keine" else "${(SystemClock.elapsedRealtime() - lastAudioAt) / 1000}s"
        val message = "$event | interaktiv=${power.isInteractive}, gesperrt=$locked | AudioFrames=$audioFrames | letzte Audiodaten=$age | Pegel=%.3f | Übergabe=$awaitingVoice".format(lastAudioPeak)
        debug("screen_diagnostic", message)
        val now = SystemClock.elapsedRealtime()
        if (event != "Statusprüfung" || now - lastLogAt >= 3000L) {
            lastLogAt = now
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date())
            val prefs = getSharedPreferences("ep_debug", MODE_PRIVATE)
            val previous = prefs.getString("screen_history", "") ?: ""
            val line = "$timestamp | $message"
            prefs.edit().putString("screen_history", (previous.lineSequence().filter { it.isNotBlank() }.toList().takeLast(79) + line).joinToString("\n")).apply()
        }
    }
    private val channel = "ep_voice"
    private var lastTrigger = 0L
    private var failed = false
    private var handedOff = false
    private val handler = Handler(Looper.getMainLooper())
    private var awaitingVoice = false
    private var voiceSeen = false
    private var handoffAt = 0L
    private var quietSince = 0L
    private val voiceMonitor = object : Runnable {
        override fun run() {
            if (!awaitingVoice) return
            val elapsed = SystemClock.elapsedRealtime() - handoffAt
            val audio = getSystemService(AudioManager::class.java)
            val communication = audio.mode == AudioManager.MODE_IN_COMMUNICATION || audio.mode == AudioManager.MODE_IN_CALL
            if (communication) {
                voiceSeen = true
                quietSince = 0L
                debug("handoff", "Kommunikationsmodus erkannt (kein unabhängiger Nachweis der ChatGPT-Aufnahme)")
            } else if (voiceSeen) {
                if (quietSince == 0L) quietSince = SystemClock.elapsedRealtime()
                if (SystemClock.elapsedRealtime() - quietSince >= 2500L) { resumeAfterHandoff("Kommunikationsmodus beendet"); return }
            }
            if (!voiceSeen && elapsed >= 12000L) { resumeAfterHandoff("Sprachmodus nicht bestätigt – Timeout"); return }
            if (elapsed >= 600000L) { resumeAfterHandoff("Überwachung nach 10 Minuten beendet"); return }
            handler.postDelayed(this, 500L)
        }
    }
    private fun debug(key: String, value: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString(key, value).apply() }
    private fun status(s: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("state",s).apply() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"EP Voice Dauererkennung",NotificationManager.IMPORTANCE_LOW))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        try {
            getSystemService(AudioManager::class.java).registerAudioRecordingCallback(audioCallback, handler)
            recordingCallbackRegistered = true
        } catch (e: Exception) { debug("audio_configs", "Callback nicht verfügbar: ${e.javaClass.simpleName}") }
        handler.post(diagnosticTick)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            ChatGptVoiceAccessibilityService.cancelPendingStart()
            stopSelf()
            return START_NOT_STICKY
        }
        if (detector != null || awaitingVoice) return START_NOT_STICKY
        failed = false
        handedOff = false
        debug("screen_history", "")
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
                audioFrames = frames
                lastAudioAt = SystemClock.elapsedRealtime()
                lastAudioPeak = peak
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
                    startVoiceHandoff(wakeWord)
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
    private fun startVoiceHandoff(wakeWord: String) {
        if (awaitingVoice) return
        awaitingVoice = true
        voiceSeen = false
        quietSince = 0L
        handoffAt = SystemClock.elapsedRealtime()
        status("$wakeWord erkannt – Mikrofonübergabe")
        // Keep the already running microphone foreground service; stop only AudioRecord.
        detector?.stop()
        debug("engine", "Mikrofon pausiert; Dienst bleibt aktiv")
        handler.postDelayed({
            if (!awaitingVoice) return@postDelayed
            val direct = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName("com.openai.chatgpt", "com.openai.voice.assistant.AssistantActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val directAllowed = try {
                val info = packageManager.getActivityInfo(direct.component!!, 0)
                info.exported && info.permission == null
            } catch (_: Exception) { false }
            val attempts = mutableListOf<Intent>()
            if (directAllowed) attempts.add(direct)
            attempts.add(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://chat.com/?mode=voice")).apply {
                setPackage("com.openai.chatgpt")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            var dispatched = false
            for (intent in attempts) {
                try {
                    startActivity(intent)
                    debug("handoff", if (intent.component != null) "Voice-Activity angefordert" else "Voice-Deep-Link angefordert")
                    dispatched = true
                    break
                } catch (e: Exception) {
                    debug("handoff", "Startversuch fehlgeschlagen: ${e.javaClass.simpleName}")
                }
            }
            if (!dispatched) {
                showLaunchNotification(wakeWord)
                resumeAfterHandoff("Direkter Start blockiert – Benachrichtigung angeboten")
            } else {
                handler.post(voiceMonitor)
            }
        }, 400L)
    }

    private fun resumeAfterHandoff(reason: String) {
        awaitingVoice = false
        handler.removeCallbacks(voiceMonitor)
        debug("handoff", reason)
        try {
            detector?.startNamed { wakeWord, score ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastTrigger > 5000L) {
                    lastTrigger = now
                    debug("phrase", "$wakeWord (Score: $score)")
                    startVoiceHandoff(wakeWord)
                }
            }
            status("Wakeword-Erkennung erneut gestartet")
        } catch (e: Exception) {
            debug("error", "Wiederaufnahme fehlgeschlagen: ${e.message}")
            status("Wiederaufnahme fehlgeschlagen")
        }
    }
    private fun showLaunchNotification(wakeWord: String) {
        if (packageManager.getLaunchIntentForPackage(ChatGptVoiceAccessibilityService.CHATGPT_PACKAGE) == null) {
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
        fun releaseMicrophone() = this@WakeService.releaseMicrophone()
    }

    private fun releaseMicrophone() {
        handedOff = true
        releaseDetector()
        debug("engine", "Mikrofon freigegeben")
        status("Erkennung pausiert – zum Fortsetzen STARTEN")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseDetector() {
        detector?.release()
        detector = null
    }

    override fun onDestroy() {
        awaitingVoice = false
        handler.removeCallbacks(voiceMonitor)
        handler.removeCallbacks(diagnosticTick)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        if (recordingCallbackRegistered) getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(audioCallback)
        releaseDetector()
        if (!failed && !handedOff) {
            debug("engine", "Mikrofon inaktiv")
            status("Erkennung gestoppt")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = HandoffBinder()
}
