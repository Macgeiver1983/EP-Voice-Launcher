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
    private fun debug(key: String, value: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString(key, value).apply() }
    private fun status(s: String) { getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("state",s).apply() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"EP Voice Dauererkennung",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (detector != null) return START_STICKY
        failed = false
        debug("engine", "Initialisierung läuft")
        debug("events", "0")
        debug("error", "Keine")
        val stop = PendingIntent.getService(this,1,Intent(this,WakeService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("EP Voice – Hey Jarvis").setContentText("Lokale Erkennung aktiv")
            .setOngoing(true).addAction(Notification.Action.Builder(null,"Stoppen",stop).build()).build()
        startForeground(1001,n)
        try {
            debug("engine", "Modell wird geladen")
            detector = OpenWakeWord.Builder(this).setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.5f).setDebounceMs(3000L).build()
            debug("engine", "Modellobjekt erstellt; Audio-Start läuft")
            detector?.start { score ->
                val prefs = getSharedPreferences("ep_debug", MODE_PRIVATE)
                val count = (prefs.getString("events", "0")?.toIntOrNull() ?: 0) + 1
                prefs.edit().putString("events", count.toString()).putString("last_score", score.toString()).apply()
                val now = SystemClock.elapsedRealtime()
                if (now-lastTrigger > 5000) {
                    lastTrigger = now
                    getSharedPreferences("ep_debug",MODE_PRIVATE).edit()
                        .putString("phrase","Hey Jarvis (Score: $score)").apply()
                    status("Hey Jarvis erkannt – Benachrichtigung öffnen")
                    showLaunchNotification()
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
        return START_STICKY
    }
    private fun showLaunchNotification() {
        val launch = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (launch == null) { status("ChatGPT nicht installiert oder nicht sichtbar"); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this,2,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Jarvis erkannt").setContentText("Tippen, um ChatGPT zu öffnen")
            .setContentIntent(pending).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(1002,notification)
    }
    override fun onDestroy() {
        detector?.stop(); detector?.release(); detector = null
        if (!failed) status("Erkennung gestoppt")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        @JvmStatic fun openChatGPT(context: Context) {
            val i = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i) }
            else android.widget.Toast.makeText(context,"ChatGPT nicht gefunden",android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
