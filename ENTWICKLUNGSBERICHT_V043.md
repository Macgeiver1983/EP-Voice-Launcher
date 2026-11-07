# V0.4.3 – lokaler Entwicklungscheckpoint

Basis: GitHub main `01bc676dad5ec304a9d57f63221f8bec9d75b0dc` (V0.4.2). Die ursprünglichen Dateien sind im Archiv enthalten.

**Fertig im Quellcode (nicht auf Android gebaut/getestet):**
- Zehn reale WAV-Dateien aus dem bereitgestellten Trainingsdaten-ZIP übernommen, inklusive Herkunftsmanifest und QA-Messwerten.
- `OpenWakeWord.kt`: optionale zweite ONNX-Wakeword-Session, gemeinsame Mikrofonaufnahme, Mel- und Embedding-Berechnung, getrennte Detektionssperren und benannte Ereignisse.
- `WakeService.kt`: beide Modelle konfiguriert, zuletzt erkannter Name in Ereignisspeicher und ChatGPT-Benachrichtigung.
- Fehlende `hey_ep.onnx` wird explizit als optional protokolliert; Jarvis soll weiter funktionieren.

**Noch offen:**
- Geeignete, ausreichend große positive und negative Datenbasis; echte Trainings- und Testpipeline; trainiertes `hey_ep.onnx`.
- Android-Build und Smartphone-Praxistests. Kein verifizierter V0.4.3-APK-Build.
- GitHub-Schreibzugriff: Connector verweigert Branch-Erstellung mit HTTP 403 (`Resource not accessible by integration`), obwohl Leserechte bestehen. Diesen ZIP-Checkpoint in Work öffnen und erst danach committen.

**Wichtig:** Diese Entwicklung ist KEINE fertige Dual-Wakeword-Version. Ohne echtes `hey_ep.onnx` wird weiterhin nur Jarvis erkannt.
