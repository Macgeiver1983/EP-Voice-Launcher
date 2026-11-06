# EP Voice Launcher 0.4.2 – technischer Prüfstand

Fortsetzung des vorhandenen V0.4.1-Projekts. Ein erfolgreicher Build ersetzt keinen Gerätetest.

## Bauen und Installieren

GitHub Actions baut mit Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, JDK 17 und SDK 35. Unter Actions das Artefakt `ep-voice-android` herunterladen; darin liegt `app/build/outputs/apk/debug/app-debug.apk`. Lokal: Gradle 8.9 installieren und `gradle :app:assembleDebug :app:lintDebug` ausführen. Ein Gradle-Wrapper ist im gelieferten Originalarchiv nicht enthalten.

Auf dem Samsung die App öffnen, Mikrofon und Benachrichtigungen erlauben, STARTEN drücken. Engine-Anzeige muss steigende Audio-Blöcke und einen Pegel zeigen. Dann „Hey Jarvis“ testen. Nach einem Treffer die Benachrichtigung antippen. Der Sprachmodus von ChatGPT wird noch nicht automatisch geöffnet.

## Änderungen gegenüber dem Original

- Original vollständig hochgeladen; Git-Historie und Sicherungsbranch bleiben erhalten.
- SDK-Setup korrigiert: explizite Pakete statt des entfernten Pakets `tools`.
- Build und Modellprüfung getrennt, begrenzte Laufzeiten, Logs als Artefakte.
- Gleiche OpenWakeWord-Engine aus Version 0.1.0 lokal übernommen; Lizenz in LICENSE-openwakeword.txt. Kein zusätzliches JitPack-AAR im App-Modul.
- Mel-Ausgabe verarbeitet jetzt alle acht Zeitabschnitte je 80-ms-Block. Zuvor wurde nur der letzte verarbeitet.
- Audio-Lesefehler und asynchrone Modellfehler werden gemeldet; Teilblöcke korrekt zusammengesetzt; Zählerüberlauf nach langem Betrieb vermieden.
- Echte Audio-Blockanzahl und Pegel sichtbar. Bildschirm scrollbar; Berechtigungen für Mikrofon und Benachrichtigungen vor Start prüfen.
- Kein automatischer Neustart des Mikrofon-Diensts nach Systemabbruch; bewusst erneut aus sichtbarer App starten.
- Tatsächlich verwendete Modelle liegen vollständig in den Assets. Modellprüfung lädt exakt gepinnte Originale herunter, prüft SHA-256 und ONNX sowie die APK-Inhalte. Der bisherige, unverbundene SpeechKit-Download entfällt.

## Grenzen und noch nötige Gerätetests

Keine Messung von Trefferquote, Fehlalarmen, CPU-Last, Stromverbrauch oder Samsung-Sperrbildschirmbetrieb. Der synthetische ONNX-Test belegt nur Modellstruktur und Inferenzaufrufe. Es gibt keine echte positive/negative Sprach-Testreihe und kein Instrumentationstest auf einem Android-Gerät.

Der Dienst startet aus der sichtbaren App als Mikrofon-Vordergrunddienst. Android beschränkt Hintergrundstarts und automatisches Öffnen von Apps. Nach dem Treffer wird deshalb eine antippbare Benachrichtigung verwendet. Bisher wird nur der Launcher-Intent von `com.openai.chatgpt` geöffnet; es besteht keine implementierte Schnittstelle zum direkten Start des ChatGPT-Sprachmodus. Die Mikrofonübergabe an ChatGPT ist auf dem Gerät zu testen. Keine zugesicherte 24/7-Verfügbarkeit und keine nachgewiesene Akkuersparnis.

Historische Dateien README_DE.md, README_V04.txt und GITHUB_START_HIER.md beschreiben frühere Stände. Diese README beschreibt den aktuellen Stand.

## Quellen

- https://github.com/msnilsen/openwakeword-android/tree/aa7bf538e12bbb363aff47de453616b12d6df3d1
- https://developer.android.com/build/releases/agp-8-7-0-release-notes
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/guide/components/activities/background-starts
