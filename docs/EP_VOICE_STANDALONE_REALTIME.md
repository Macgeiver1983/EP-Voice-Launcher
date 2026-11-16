# EP Voice – Eigenständiger Android-Assistent (Entwicklungsauftrag)

Stand: 2026-11-16. Ausgangspunkt: test/voice-handoff-audio-resume. Keine Änderungen an bestehenden Wakeword-Modellen oder Schwellenwerten.

## Verbindliches Ziel
Bei schwarzem, gesperrtem Samsung Android 16 soll "Hey EP" oder "Hey Jarvis" eine eigene EP-Voice-Sprachsitzung starten, ohne Entsperren oder Berühren. Die bisherige Weiterleitung zur offiziellen ChatGPT-App wird durch eine eigene Realtime-Anbindung ersetzt. Funktion auf dem Zielgerät ist noch NICHT bestätigt.

## Android-Architektur
- VoiceInteractionService als vom Benutzer ausgewählter Standardassistent, mit korrekter voice_interaction_service-Metadaten-XML und Session-Service.
- VoiceInteractionSession für systemseitigen Start und Audio-Sitzung. Keine fremde ChatGPT-Activity, keine Accessibility-Automation.
- Vorhandene OpenWakeWord-Modelle, Audio-Engine, Offline-Wakeword und Rückkehr zur Erkennung nach Sitzungsende erhalten.
- Audiozustandsmaschine: LISTENING -> TRIGGERED -> CONNECTING -> CONVERSING -> STOPPING -> LISTENING. Timeouts und Fehlermeldungen mit Zeitstempel; keine endlosen Mikrofonpausen.
- Sperrbildschirm: keine privaten Inhalte auf der Oberfläche; sensitive Aktionen nur nach Geräteentsperrung. Samsung-Gerätetest für onReady/showSession/onShowSessionFailed und Mikrofonkontinuität erforderlich.

## OpenAI-Realtime
- Realtime API mit bidirektionalem Audio über WebRTC (alternativ dokumentierte WebSocket-Verbindung, wenn Android-Implementierung begründet).
- Ephemeres Client-Token über einen eigenen authentifizierten Backend-Endpunkt; Standard-API-Schlüssel niemals in APK, Quellcode, Log oder GitHub Actions.
- Audio-Ausgabe, Unterbrechung/Barge-in, Netzwerkabbruch, Wiedereinwahl, Abbruch und Kostenlimit behandeln.
- Keine Verbindung ohne vorherige Nutzerkonfiguration und Einwilligung. ChatGPT Plus ist nicht gleich API-Guthaben.

## Vorhandene ChatGPT-Informationen
- Kein Zugriff auf private ChatGPT-Memory über die normale Realtime API voraussetzen.
- Optionaler, vom Nutzer ausgelöster ChatGPT-Datenexport und selektiver lokaler Import relevanter Informationen. Importvorschau, Ausschluss privater Daten, Verschlüsselung, Löschung und Quellkennzeichnung.
- Keine automatische Synchronisation oder Übernahme aller ChatGPT-Erinnerungen behaupten.
- Sensible Projektinformationen nur mit ausdrücklicher Freigabe an API-Kontext senden.

## Abnahmekriterien
1. Standardassistent auswählbar und systemseitig startbar.
2. Beide Wakewords offline unverändert.
3. Bildschirm schwarz und gesperrt: Auslösung -> eigene Realtime-Sprachunterhaltung -> Audioantwort ohne Berührung; reproduzierbar auf Zielgerät.
4. Mikrofon nach Ende wieder frei und Wakeword aktiv.
5. Keine Geheimnisse in APK/Repository; Backend-Token läuft ab; nachvollziehbare Fehler- und Kostenbegrenzung.
6. ChatGPT-Datenimport nur nach Einwilligung und nachprüfbar getrennt von Live-Sitzung.

## Referenzen
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- https://developer.android.com/reference/android/service/voice/VoiceInteractionSession
- https://developers.openai.com/api/docs/guides/realtime
- https://help.openai.com/en/articles/7260999-exporting-your-chatgpt-history-and-data
