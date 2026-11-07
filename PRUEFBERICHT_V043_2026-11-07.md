# EP Voice Launcher V0.4.3 – technischer Prüfbericht

**Datum:** 07.11.2026. **Status:** V0.4.3-DEV mit echt trainiertem, aber **nicht ausreichend praxistauglich validiertem** Hey-EP-Modell. **Keine APK erstellt.**

## Unveränderte V0.4.2

GitHub `main` ist nach letzter Prüfung weiterhin `01bc676dad5ec304a9d57f63221f8bec9d75b0dc` (06.11.2026). Die drei Originalassets (Mel, Embedding, Jarvis) stimmen bytegenau mit den SHA-256-Referenzen überein. Der bestehende Jarvis-Classifier wurde weder neu trainiert noch überschrieben.

## V0.4.3 Quellcode

Gemeinsame Mikrofonaufnahme, gemeinsamer Mel- und Embedding-Frontend-Pfad. Separate Klassifikationssessions für `hey_jarvis_v0.1.onnx` und `hey_ep.onnx`. Schwellen: Jarvis 0,50, EP 0,65 (Versuchswerte). Globales Debouncing und separate Ereignislisten. Bei nicht ladbarem optionalem EP-Modell bleibt Jarvis aktiv. Modellinput `[1,16,96]`, Output `[1,1]` validiert. Die ChatGPT-Benachrichtigung bleibt wie in V0.4.2.

## Echtes Modelltraining

Zehn Originalaufnahmen bei 16 kHz/Mono/16 bit (drei reale Sprecher laut Nutzer; einzelne Sprecher-IDs unbekannt). Zusätzlich 403 generierte/augmentierte Dateien: 188 positive (80 Real-Augmentierungen + 108 TTS), 215 negative (147 Grundsatz + 68 schwierige TTS-Gegenbeispiele). Training mit 55 Epochen und sämtlichen 80-ms-Fenstern der Negativdateien; exportierter neuer Klassifikator `hey_ep.onnx` aus trainierten PyTorch-Gewichten, **kein umbenanntes Jarvis-Modell**.

ONNX wurde mit dem tatsächlich vorhandenen nativen ONNX Runtime 1.21 CPU-Backend geladen und numerisch gegen PyTorch geprüft. Python-Paket `onnx` ist hier nicht installiert; die separate `onnx.checker`-Prüfung steht im GitHub-CI-Workflow bereit. Datei `app/src/main/assets/openwakeword/hey_ep.onnx` ist vorhanden.

## Testergebnisse und entscheidende Grenzen

- Internes synthetisches Holdout: **60/60** positive Zeitfenster erkannt, **0/722** negative Zeitfenster falsch positiv.
- Sanity-Test auf den 10 **bereits im Training verwendeten** Originalaufnahmen: **10/10** erkannt.
- Synthetische Negativaufnahmen aus trainierten Phrasen: **0/21** bzw. **0/68** Fehltrigger bei erneuter vollständiger Timelinesimulation.
- **Wesentlich anspruchsvollerer zusätzlicher Test** mit neuen Formulierungen und zwei zuvor nicht verwendeten eSpeak-Stimmvarianten: **8/8** positive Aktivierungsbeispiele erkannt, jedoch **18/66** negativ beschriftete Phrasen falsch ausgelöst. Das ist für den Dauerbetrieb **nicht akzeptabel**.
- **Nicht getestet:** echte unabhängige Testsprecher, normaler TV-/Alltagsbetrieb mit stundenlanger Fehlalarmmessung, Belastung/Batterie, Sperrbildschirm, Samsung-App, Android-APK.

**Entscheidung:** ONNX technisch valide, **Qualitätsfreigabe verweigert**. Die neue Android-Integration ist eine Entwicklungs-/Testversion; für unüberwachte Nutzung ist sie nicht freigegeben. Höhere Schwellen allein helfen nicht zuverlässig, da mehrere falsch positive synthetische Beispiele Score nahe 1,0 erreichen.

## Status der Implementierung

- `python scripts/check_v043.py --skip-audio --require-model`: PASS unter nativer ORT (mit bekannten ONNX-Frontend-Assets und echtem EP-Modell).
- `python -m py_compile` der Python-Skripte: PASS.
- eSpeak-Generator und Torch-Trainingsskript: lokal ausgeführt (zunächst 311, später 403 synthetische/augmentierte Samples).
- Android SDK und Gradle fehlen lokal; **keine APK** und keine Android-Lint-/Instrumentierungstests ausgeführt.
- GitHub-Connector meldet das richtige Konto, aber keine aktive App-Installation. Branch-/Commit-Schreibaktionen `403 Resource not accessible by integration`; keine Remote-Änderung.
- Lokale Git-Branch `dev/v0.4.3-dual-wakeword` und `git format-patch` sind als Übernahmeoption vorbereitet; lokale Commits sind nicht GitHub-Commits.

## Datenschutz

Das Repository ist **öffentlich**. Die zehn persönlichen Sprachaufnahmen und sämtliche synthetisch abgeleiteten WAVs dürfen daher nicht versehentlich ins öffentliche GitHub übernommen werden. Das private Gesamt-ZIP enthält diese Daten; das GitHub-Quellcode-ZIP schließt WAV-Dateien aus, enthält aber das experimentelle Modell. Vor einer öffentlichen Veröffentlichung des trainierten Modells mögliche Datenschutzfragen prüfen.

## Für die technische Fertigstellung

1. Zusätzliche echte Aufnahmen von anderen Sprechern und längere negative TV-/Gesprächsaufnahmen gewinnen (mit Einwilligung) und Sprecher-Gruppen eindeutig trennen.
2. Hard-Negative-Training gegen unabhängige, neue Stimmen wiederholen; Verwechslungsphrasen und **Fehlalarme pro Stunde** messen.
3. GitHub-App-Schreibberechtigung für das Repository herstellen, privaten Trainingsdatensatz nicht öffentlich committen. CI-Build auf dem DEV-Branch starten.
4. APK auf Samsung testen: Jarvis-Regression, Hey-EP-Sensitivität, Normalgespräche, Hintergrund/Sperrbildschirm, Batterieverbrauch, ChatGPT-Aktion.

**Kein erfolgreicher Android-Build oder Smartphone-Test wird vorgetäuscht.**
