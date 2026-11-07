package de.europlast.epvoice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
    private static WeakReference<MainActivity> resumedActivity = new WeakReference<>(null);
    private TextView status, recognized, recognitionState, engineState, eventCount, errorState, voiceState;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            android.content.SharedPreferences prefs=getSharedPreferences("ep_debug", MODE_PRIVATE);
            recognized.setText("Letzte Erkennung: " + prefs.getString("phrase", "(noch nichts erkannt)"));
            recognitionState.setText("Status: " + prefs.getString("state", "Bereit"));
            engineState.setText("Engine: " + prefs.getString("engine", "Nicht gestartet"));
            eventCount.setText("Hey Jarvis: " + prefs.getString("events_jarvis", "0")
                    + " Treffer | Score " + prefs.getString("score_jarvis", "–")
                    + "\nHey EP: " + prefs.getString("events_ep", "0")
                    + " Treffer | Score " + prefs.getString("score_ep", "–"));
            voiceState.setText("ChatGPT: " + prefs.getString("handoff", "Noch nicht getestet"));
            String modelError = prefs.getString("model_error", "");
            errorState.setText("Fehler: " + prefs.getString("error", "Keine")
                    + (modelError.isEmpty() ? "" : "\n" + modelError));
            uiHandler.postDelayed(this, 700);
        }
    };
    private final String[] permissions = Build.VERSION.SDK_INT >= 33 ?
            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS} :
            new String[]{Manifest.permission.RECORD_AUDIO};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 72, 48, 48); root.setBackgroundColor(Color.rgb(245,247,250));
        TextView heading = new TextView(this); heading.setText("EP Voice Launcher V0.4.5 Voice-Test"); heading.setTextSize(25); heading.setTextColor(Color.BLACK); root.addView(heading);
        TextView info = new TextView(this); info.setText("Hey Jarvis und Hey EP werden lokal erkannt.\n\nWenn diese App sichtbar ist, versuchen beide Wörter direkt den ChatGPT-Sprachmodus zu öffnen. Im Hintergrund bleibt die Benachrichtigung.\n\nBeim Öffnen von ChatGPT pausiert die Erkennung. Danach kannst du sie mit STARTEN fortsetzen."); info.setTextSize(17); info.setPadding(0,35,0,35); root.addView(info);
        status = new TextView(this); status.setTextSize(16); root.addView(status);
        recognized = new TextView(this); recognized.setTextSize(19); recognized.setPadding(0,30,0,15);root.addView(recognized);
        recognitionState = new TextView(this); recognitionState.setTextSize(16);root.addView(recognitionState);
        engineState = new TextView(this); engineState.setTextSize(16); root.addView(engineState);
        eventCount = new TextView(this); eventCount.setTextSize(16); root.addView(eventCount);
        errorState = new TextView(this); errorState.setTextSize(16); root.addView(errorState);
        voiceState = new TextView(this); voiceState.setTextSize(16); root.addView(voiceState);
        Button start = new Button(this); start.setText("Spracherkennung STARTEN"); root.addView(start);
        Button stop = new Button(this); stop.setText("Spracherkennung STOPPEN"); root.addView(stop);
        Button voice = new Button(this); voice.setText("ChatGPT-Sprachmodus testen"); root.addView(voice);
        Button open = new Button(this); open.setText("ChatGPT-App öffnen"); root.addView(open);
        start.setOnClickListener(v -> { if (!hasRequiredPermissions()) requestPermissions(permissions, 7); else startListening(); });
        stop.setOnClickListener(v -> { stopService(new Intent(this, WakeService.class)); status.setText("Dienst gestoppt"); });
        voice.setOnClickListener(v -> startActivity(ChatGptHandoffActivity.voiceIntent(this)));
        open.setOnClickListener(v -> startActivity(new Intent(this, ChatGptHandoffActivity.class)));
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll); status.setText("Bereit");
    }
    @Override protected void onResume() {
        super.onResume();
        resumedActivity = new WeakReference<>(this);
        uiHandler.post(refresh);
    }
    @Override protected void onPause() {
        if (resumedActivity.get() == this) resumedActivity.clear();
        uiHandler.removeCallbacks(refresh);
        super.onPause();
    }
    // Called on the main thread by the wake-word detector.
    static boolean tryOpenVoice() {
        MainActivity activity = resumedActivity.get();
        if (activity == null || activity.isFinishing() || !activity.hasWindowFocus()) return false;
        activity.startActivity(ChatGptHandoffActivity.voiceIntent(activity));
        return true;
    }
    private boolean hasRequiredPermissions() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
    private void startListening() { startForegroundService(new Intent(this, WakeService.class)); status.setText("Dienst gestartet – siehe Benachrichtigung"); }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] grants) {
        super.onRequestPermissionsResult(code,p,grants);
        if(code==7 && hasRequiredPermissions()) startListening();
        else status.setText("Mikrofon und Benachrichtigungen erforderlich. Bitte in den App-Einstellungen erlauben.");
    }
}
