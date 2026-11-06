# EP Voice Launcher V0.1 – Android-Testprojekt

## Status
Quellcodeprojekt, **keine fertige APK**. Noch nicht auf einem Samsung-Gerät kompiliert oder getestet.

## Bauen mit Android Studio
1. ZIP entpacken und Ordner `EPVoiceLauncher` in Android Studio öffnen.
2. Android Studio mit Internetzugang die Gradle-Plugin-Abhängigkeiten und das Android SDK 35 installieren lassen.
3. JDK 17 verwenden. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. Die erzeugte `app-debug.apk` per USB auf das Samsung-Handy kopieren und installieren (bei Nachfrage Installation aus dieser Quelle erlauben).
5. App öffnen, Mikrofon- und Benachrichtigungsberechtigung erteilen, auf STARTEN tippen.
6. ChatGPT-App muss installiert sein. In der App zunächst den Knopf „ChatGPT testweise öffnen“ prüfen.
7. „Hey ChatGPT“ sagen. Nach Erkennung erscheint eine Benachrichtigung; diese antippen, um ChatGPT zu öffnen.

## Einschränkungen
- Android SpeechRecognizer ist **nicht** als zuverlässige ununterbrochene Wake-Word-Engine gedacht. Sitzungen enden, werden neu gestartet und können insbesondere bei gesperrtem Display scheitern.
- Wenn keine Offline-Erkennung vorhanden ist, kann der Android-Erkennungsdienst Sprache über das Netzwerk verarbeiten. Der Quellcode selbst überträgt keine Audiodaten an einen eigenen Server.
- Android blockiert gewöhnlich das automatische Öffnen einer Activity aus einem Hintergrunddienst. Darum ist in dieser Version ein Tipp auf die Benachrichtigung nötig. Der manuelle Testknopf öffnet ChatGPT direkt.
- ChatGPT-Sprachmodus wird nicht automatisch gestartet; dafür ist keine zugesicherte öffentliche Intent-Schnittstelle hinterlegt.
- Samsung-Energiesparen kann die Erkennung beenden. Bei Bedarf unter Einstellungen > Apps > EP Voice Launcher > Akku die Nutzung auf „Nicht eingeschränkt“ setzen (Bezeichnungen variieren).
- Der Dienst wird nicht automatisch beim Booten gestartet. Die Erkennung wird nach Dienststopp oder Systemabbruch nicht automatisch wiederhergestellt.
- Dauerhafte Mikrofonüberwachung kann den Akku deutlich belasten.

## Nächste Version
Dedizierte lokale Wake-Word-Engine statt SpeechRecognizer und expliziter Start eines Sprachdialogs über unterstützte API.
