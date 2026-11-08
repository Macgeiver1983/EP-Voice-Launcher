# V0.4.7 – Prüfung der Play-Protect-Installationssperre

Die auf dem Samsung gemeldete Warnung entspricht Googles dokumentierter
Sperre für aus dem Internet installierte Apps mit sensiblen Fähigkeiten,
darunter Bedienungshilfedienste. Die alte V0.4.7 hat einen solchen Dienst
erstmals eingebunden. Die Signaturprüfung und die Play-Protect-Einstufung
sind getrennte Prüfungen.
[Google Developer Guidance](https://developers.google.com/android/play-protect/warning-dev-guidance#app_blocked_to_protect_your_device)

Der GitHub-Actions-Lauf lädt die ursprünglich veröffentlichte APK,
verifiziert ihren SHA-256-Wert und prüft ihre vollständige APK-Signatur
mit Android SDK apksigner. Der neue QA-Build ist nicht debuggable.
Er verwendet einen einmaligen CI-Testschlüssel und APK-Signatur v2/v3.
Der private Schlüssel wird im Runner gelöscht. Diese Signatur ist weder
eine Play-Freigabe noch ein dauerhafter Veröffentlichungsschlüssel.
Wegen des anderen Zertifikats kann sie eine bereits installierte
Debug-APK derselben Paket-ID nicht als Update ersetzen.

## Berechtigungen und Verhalten

- Unbenutzte Wake-Lock-Berechtigung und Paketabfrage für Androids
  Spracherkennungsdienst entfernt. Android-App-Backup deaktiviert.
- Bedienungshilfe bleibt ausdrücklich freiwillig. Vor dem Öffnen der
  Systemeinstellungen zeigt EP Voice an, was der Dienst ausliest und tut.
- Der Dienst bleibt korrekt mit der systemgeschützten Bindeberechtigung
  deklariert. Der Filter ist auf ChatGPT begrenzt. Der Dienst ist als
  allgemeine Automatisierung gekennzeichnet (`isAccessibilityTool=false`).
- Die Fähigkeit, den Sprachknopf als Bedienelement zu lesen, bleibt
  erforderlich. Der Startablauf und beide Offline-Modelle bleiben erhalten.

## Noch erforderliche Prüfungen

1. Installation auf dem Samsung mit eingeschaltetem Play Protect.
   Bei erneutem Block Test beenden und genaue Warnung sowie
   Installationsquelle dokumentieren. Die Accessibility-Fähigkeit kann
   den dokumentierten Block trotz erfolgreicher Signaturprüfung auslösen.
2. Bei zulässiger Installation prüfen, dass der Dienst vor der Freigabe
   inaktiv ist und nach Abschalten keine ausstehenden Klicks ausführt.
   Zugriff auf andere Apps und Klicks in Systemdialogen ausschließen.
3. Auf dem Samsung beide Wake-Words, Mikrofonfreigabe, den einmaligen
   ChatGPT-Sprachstart, Sperrbildschirm und Fehlauslösung testen.
4. Bei anhaltender Sperre ist Googles offizielle Überprüfung der
   Play-Protect-Maßnahme der nächste Weg. Eine Verteilung über Google
   Play wäre separat gegen dessen Richtlinie zur Accessibility API zu
   prüfen. Dazu gehören die Erklärung in der Play Console und die
   Offenlegung der Verwendung. Eine Signaturprüfung ersetzt das nicht.
