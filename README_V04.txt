EP Voice Launcher V0.4 – ENTWICKLUNGSSTAND (nicht getestet)

Änderung: SpeechRecognizer ersetzt durch OpenWakeWord-Android (ONNX, AudioRecord).
Modell: eingebautes HEY_JARVIS; andere Aktivierungswörter noch nicht integriert.
Build benötigt Gradle/Android SDK und Netzwerkzugang zu JitPack für com.github.msnilsen:openwakeword-android:0.1.0.
Der Library-Build und die Laufzeitkompatibilität wurden hier NICHT überprüft.
Falls Gradle die Bibliothek nicht auflösen kann, muss das Repository als lokales Modul eingebunden werden.
Start nur durch Benutzer aus sichtbarer Activity; Mikrofon-Foreground-Service kann unter Android 16 beschränkt werden.
Nach Erkennung erfolgt aus Android-Sicherheitsgründen eine antippbare Benachrichtigung statt automatischem Hintergrundstart.
Keine garantierte 24h-Verfügbarkeit, keine verifizierte Akkuersparnis.
Die Anzeige 'Letzte Erkennung' zeigt nur Treffer, keine freie Transkription.
