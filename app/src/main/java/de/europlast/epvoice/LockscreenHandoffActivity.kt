package de.europlast.epvoice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Visible, transparent lockscreen handoff. Does not unlock the device.
 * Android may still deny background launches; no security restrictions are bypassed.
 */
class LockscreenHandoffActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var dispatched = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
    }
    override fun onResume() {
        super.onResume()
        handler.postDelayed({
            if (dispatched || isFinishing) return@postDelayed
            dispatched = true
            try {
                startActivity(Intent(this, ChatGptHandoffActivity::class.java))
                getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
                    .putString("background_handoff", "Sperrbildschirm-Übergabe angefordert; tatsächlicher Sprachstart nicht bestätigt")
                    .apply()
            } catch (e: Exception) {
                getSharedPreferences("ep_debug", MODE_PRIVATE).edit()
                    .putString("background_handoff", "Sperrbildschirm-Übergabe fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}")
                    .apply()
            }
            finish()
        }, 250)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
