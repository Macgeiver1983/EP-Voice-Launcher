# VoiceInteractionService – Machbarkeitsprüfung

Status: Experiment, keine funktionsfähige Implementierung behauptet.

## Ausgangslage
- Quellbranch: dev/keyguard-handoff-test
- Die bestehende Wakeword-Erkennung (Hey EP / Hey Jarvis) und der bisherige ChatGPT-Start bleiben unverändert.
- Der Accessibility-Service verlangt für den automatischen Sprachstart einen interaktiven, entsperrten Bildschirm.
- Der WakeService startet eine Activity aus dem Hintergrund; Android kann diesen Start blockieren.
- Ein VoiceInteractionService ist derzeit nicht im AndroidManifest registriert.

## Zu prüfende Hypothesen
1. Kann ein eigener, vom Benutzer als Standardassistent ausgewählter VoiceInteractionService auf dem Samsung-Gerät unter Android 16 bei ausgeschaltetem Bildschirm aktiv bleiben?
2. Erhält der Dienst kontinuierlich Audiodaten für die Offline-Wakewords, ohne dass ein regulärer Mikrofon-Vordergrunddienst neu aus dem Hintergrund gestartet werden muss?
3. Welche Systemaktionen sind nach der Erkennung auf dem gesperrten Bildschirm tatsächlich erlaubt?
4. Gibt es eine dokumentierte, exportierte ChatGPT-Schnittstelle zum Start des aktiven Sprachmodus? Ohne diese darf ein erfolgreicher Start der ChatGPT-Hauptoberfläche nicht als erfolgreicher Sprachstart gewertet werden.

## Testkriterien
- Tests getrennt für Bildschirm an/aus und Gerät entsperrt/vertrauenswürdig gesperrt/authentifizierungspflichtig.
- Audio-Frames, AudioRecordingConfiguration.isClientSilenced, Service-Lebenszyklus und Android-Fehler protokollieren.
- ChatGPT-Sprachstart nur als erfolgreich werten, wenn der Nutzer tatsächlich ein aktives Sprachgespräch führen kann.
- Keine Umgehung von PIN, Biometrie, Play Protect oder Android-Sicherheitsbeschränkungen.
- Bestehende Wakeword-Modelle und deren Schwellwerte nicht verändern.

## Einschränkung
Der Standardassistent kann nicht gleichzeitig EP Voice und ChatGPT sein. Ein Wechsel der Standardassistent-App erfolgt nur nach ausdrücklicher Bestätigung auf dem Testgerät. Der Versuch eines VoiceInteractionService beweist nicht die Möglichkeit, eine interne ChatGPT-Voice-Activity zu öffnen.

## Nächster Implementierungsschritt
Isolierten Diagnose-VoiceInteractionService mit Manifest-Metadaten, minimaler Session und Lifecycle-Protokollierung erstellen. Erst nach statischer Prüfung und erfolgreichem CI-Build am Gerät testen.
