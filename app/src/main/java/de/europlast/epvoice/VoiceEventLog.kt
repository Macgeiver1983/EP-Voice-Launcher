package de.europlast.epvoice

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded, local-only diagnostic log. No microphone recordings are stored. */
object VoiceEventLog {
    private const val PREFS = "ep_voice_events"
    private const val KEY = "entries"
    @Synchronized fun record(context: Context, event: String, details: String = "") {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY).format(Date())
        val sanitized = details.replace('\n', ' ').replace('\r', ' ').take(220)
        val line = "$time | $event | $sanitized"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY, "") ?: ""
        val entries = (old.lines().filter { it.isNotBlank() } + line).takeLast(120)
        prefs.edit().putString(KEY, entries.joinToString("\n")).commit()
    }
    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
}
