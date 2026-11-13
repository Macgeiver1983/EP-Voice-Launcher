package de.europlast.epvoice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/** Diagnostic assistant entry point. Does not attempt to bypass the keyguard. */
class EpAssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("EPVoiceDiagnostic", "ACTION_ASSIST entry invoked: " + intent?.action)
        setContentView(TextView(this).apply {
            text = "EP Voice: Android hat ACTION_ASSIST gestartet.\nDies ist nur ein Funktionstest, nicht ChatGPT."
            textSize = 20f
            setPadding(36, 36, 36, 36)
        })
    }
}
