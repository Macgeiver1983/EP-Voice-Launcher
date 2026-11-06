# EP Voice – GitHub-CI-Paket

Dieses Paket enthält den unveränderten Android-Quellcode der V0.4.1 sowie eine neu ergänzte GitHub-Actions-Pipeline. Die Pipeline lädt drei SpeechKit-ONNX-Modelle herunter, validiert deren Struktur und versucht, die bisherige Android-App zu bauen. **Sie integriert das SpeechKit-Modell noch nicht in die App und führt keinen Audio-Erkennungstest durch.** Ein grüner Build bedeutet daher NICHT, dass „Hey Jarvis“ funktioniert.

## Start
1. Ein privates GitHub-Repository erstellen.
2. Den entpackten Inhalt dieses Pakets in das Repository hochladen (einschließlich des versteckten Ordners `.github`).
3. Unter **Actions** den Workflow **EP Voice – Modellbeschaffung und Android-Build** öffnen und **Run workflow** auswählen.
4. Das Protokoll sowie das Artefakt `ep-voice-ci-ergebnis` prüfen. Bei Fehlern den Build-Log verwenden.

## Was noch offen bleibt
Die Modellpipeline muss mit der Android-Audioverarbeitung verbunden und anhand aufgezeichneter Testphrasen überprüft werden. Die beigefügte APK wäre nur der bisherige V0.4.1-Stand, keine korrigierte Wake-Word-Version. Keine neue Installation empfohlen.

Status bei Paketerstellung: Quellcode und Workflow lokal auf Vollständigkeit geprüft; GitHub-Workflow nicht ausgeführt, Modell-Download nicht erfolgreich durchgeführt, APK nicht kompiliert.
