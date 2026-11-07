# Work – sichere Fortsetzung und APK-Build

Dieses Archiv enthält V0.4.3 mit experimentellem Modell, **ohne private Original-Sprachdateien**.

1. GitHub `Macgeiver1983/EP-Voice-Launcher` am V0.4.2-Commit `01bc676dad5ec304a9d57f63221f8bec9d75b0dc` belassen.
2. Separate Branch `dev/v0.4.3-dual-wakeword` von diesem Commit erstellen.
3. Nur die hier enthaltenen Dateien in diesen Branch übernehmen, **niemals** die WAVs/M4As aus dem privaten Paket.
4. GitHub Actions Workflow `android-pruefung.yml` für den Branch ausführen.
5. Bei Buildfehlern Gradle-Logs prüfen und am Branch korrigieren; nicht `main` verändern.
6. APK als GitHub Actions Artifact `ep-voice-android` bereitstellen.
7. Android-Gerätetest durchführen; aus einem grünen Build folgt noch keine zuverlässige Akustik.

Neuer Kandidat: `hey_ep.onnx`, Score 0.95, vier positive 80-ms-Fenster. Jarvis bleibt Schwelle 0.5 / ein Fenster.
Die synthetischen Fehlalarme sind 4/66; weitere Sprachtests sind Pflicht. Technischer Bericht: `docs/PRUEFBERICHT_V043_CONFUSABLE_2026-11-07.md`.
