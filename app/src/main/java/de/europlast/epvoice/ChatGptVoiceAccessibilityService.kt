package de.europlast.epvoice

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import java.util.Locale

class ChatGptVoiceAccessibilityService : AccessibilityService() {
    companion object {
        internal const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        internal const val MICROPHONE_SETTLE_MS = 400L
        private const val FIRST_SCAN_MS = 1_500L
        private const val SCAN_INTERVAL_MS = 350L
        private const val TIMEOUT_MS = 10_000L
        private const val MAX_NODES = 350
        private var connected = WeakReference<ChatGptVoiceAccessibilityService>(null)

        @JvmStatic
        fun isConnected(): Boolean = connected.get() != null

        @JvmStatic
        fun cancelPendingStart() {
            connected.get()?.onInterrupt()
        }

        // The wake detector and activity call this on the main thread in the same process.
        internal fun requestVoiceStart(releaseMicrophone: () -> Unit): Boolean =
            connected.get()?.begin(releaseMicrophone) ?: false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val openChatGpt = Runnable { openApp() }
    private val scanChatGpt = Runnable { scan() }
    private var launchIntent: Intent? = null
    private var deadline = 0L
    private var sawChatGpt = false
    private var observedChatGptSession = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences("ep_voice_control", MODE_PRIVATE)
        if (!prefs.getBoolean("awaiting_chatgpt", false) || prefs.getBoolean("manual_stop", false)) {
            observedChatGptSession = false
            return
        }
        if (packageName == CHATGPT_PACKAGE) {
            observedChatGptSession = true
        } else if (observedChatGptSession && packageName != this.packageName) {
            observedChatGptSession = false
            prefs.edit().putBoolean("awaiting_chatgpt", false).apply()
            try {
                startService(Intent(this, WakeService::class.java).setAction("RESUME"))
                record("ChatGPT verlassen – Wiederaufnahme angefordert.")
            } catch (e: Exception) {
                record("Wiederaufnahme blockiert: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        if (launchIntent != null) finishRequest("Sprachstart unterbrochen.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (connected.get() === this) connected.clear()
        onInterrupt()
    }

    private fun canUseScreen(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun begin(releaseMicrophone: () -> Unit): Boolean {
        if (launchIntent != null) return true
        if (!canUseScreen()) {
            record("Automatischer Sprachstart braucht einen entsperrten, eingeschalteten Bildschirm.")
            return false
        }
        val intent = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
        if (intent == null) {
            record("ChatGPT-App nicht gefunden.")
            return false
        }
        launchIntent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        sawChatGpt = false
        record("Sprachstart vorbereitet – Mikrofon wird freigegeben.")
        releaseMicrophone()
        handler.postDelayed(openChatGpt, MICROPHONE_SETTLE_MS)
        return true
    }

    private fun openApp() {
        val intent = launchIntent ?: return
        if (!canUseScreen()) {
            finishRequest("Sprachstart abgebrochen – Bildschirm gesperrt oder aus.")
            return
        }
        try {
            startActivity(intent)
            deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
            record("ChatGPT-Start angefordert – warte auf den Sprachknopf.")
            handler.postDelayed(scanChatGpt, FIRST_SCAN_MS)
        } catch (_: ActivityNotFoundException) {
            finishRequest("ChatGPT-App konnte nicht geöffnet werden.")
        } catch (_: SecurityException) {
            finishRequest("Android hat den ChatGPT-Start blockiert.")
        }
    }

    private fun scan() {
        if (launchIntent == null) return
        if (!canUseScreen()) {
            finishRequest("Sprachstart abgebrochen – Bildschirm gesperrt oder aus.")
            return
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            finishRequest(if (sawChatGpt) "Kein eindeutiger ChatGPT-Sprachknopf gefunden."
                else "ChatGPT wurde nicht sichtbar. Android könnte den Start blockiert haben.")
            return
        }

        val root = rootInActiveWindow
        try {
            if (root?.packageName?.toString() == CHATGPT_PACKAGE) {
                sawChatGpt = true
                if (inspectChatGpt(root)) return
            } else if (sawChatGpt && root != null) {
                finishRequest("ChatGPT ist nicht mehr im Vordergrund – Sprachstart abgebrochen.")
                return
            }
        } finally {
            recycle(root)
        }
        handler.postDelayed(scanChatGpt, SCAN_INTERVAL_MS)
    }

    private fun inspectChatGpt(root: AccessibilityNodeInfo): Boolean {
        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        nodes.add(root)
        var inspected = 0
        var candidate: AccessibilityNodeInfo? = null
        var ambiguous = false
        var truncated = false
        try {
            while (nodes.isNotEmpty() && inspected < MAX_NODES) {
                val node = nodes.removeFirst()
                inspected++
                try {
                    if (node.packageName?.toString() != CHATGPT_PACKAGE || !node.isVisibleToUser) continue
                    if (node.isEnabled && node.isClickable) {
                        when {
                            VoiceControlLabels.isEnd(node.contentDescription, node.text) -> {
                                finishRequest("ChatGPT-Sprachmodus ist bereits geöffnet.")
                                return true
                            }
                            VoiceControlLabels.isStart(node.contentDescription, node.text) -> {
                                if (candidate == null) {
                                    candidate = node
                                } else {
                                    ambiguous = true
                                }
                            }
                        }
                    }
                    val remaining = MAX_NODES - inspected - nodes.size
                    if (node.childCount > remaining) truncated = true
                    for (index in 0 until minOf(node.childCount, remaining)) {
                        node.getChild(index)?.let(nodes::addLast)
                    }
                } finally {
                    if (node !== root && node !== candidate) recycle(node)
                }
            }
            // Hitting the traversal limit means we cannot establish uniqueness.
            if (truncated || ambiguous || candidate == null) return false
            val button = candidate
            if (!canUseScreen() || !button.refresh() ||
                button.packageName?.toString() != CHATGPT_PACKAGE || !button.isVisibleToUser ||
                !button.isEnabled || !button.isClickable ||
                !VoiceControlLabels.isStart(button.contentDescription, button.text)) return false
            val activeRoot = rootInActiveWindow
            try {
                if (activeRoot?.packageName?.toString() != CHATGPT_PACKAGE ||
                    activeRoot.windowId != button.windowId) return false
            } finally {
                recycle(activeRoot)
            }
            // Clear the request before the action. Failure does not cause a second click.
            finishRequest("Eindeutiger Sprachknopf erkannt – ein Startversuch.")
            val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            record(if (clicked) "Sprachknopf einmal betätigt – Sprachgespräch am Gerät prüfen."
                else "ChatGPT hat den Klick nicht angenommen.")
            return true
        } finally {
            if (candidate !== root) recycle(candidate)
            nodes.forEach { if (it !== root) recycle(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo?) = node?.recycle()

    private fun finishRequest(message: String) {
        launchIntent = null
        handler.removeCallbacks(openChatGpt)
        handler.removeCallbacks(scanChatGpt)
        record(message)
    }

    private fun record(message: String) {
        getSharedPreferences("ep_debug", MODE_PRIVATE).edit().putString("handoff", message).apply()
    }
}

internal object VoiceControlLabels {
    private val start = setOf(
        "start voice mode", "start voice conversation", "start voice chat",
        "sprachmodus starten", "sprachgespräch starten", "sprachchat starten",
    )
    private val end = setOf(
        "end voice mode", "end voice conversation", "end voice chat", "exit voice mode",
        "sprachmodus beenden", "sprachgespräch beenden", "sprachchat beenden", "sprachmodus verlassen",
    )

    fun isStart(description: CharSequence?, text: CharSequence?): Boolean =
        label(description, text) in start

    fun isEnd(description: CharSequence?, text: CharSequence?): Boolean =
        label(description, text) in end

    private fun label(description: CharSequence?, text: CharSequence?): String =
        (description?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: text?.toString()?.trim().orEmpty())
            .lowercase(Locale.ROOT)
}
