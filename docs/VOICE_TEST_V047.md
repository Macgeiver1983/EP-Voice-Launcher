# EP Voice Launcher V0.4.7 – Sprachstart über Android-Bedienungshilfe

Die vorhandene Übergabe ist in `ChatGptHandoffActivity.kt` implementiert.
Sie gibt das Mikrofon vor dem Wechsel zu ChatGPT frei. Neu kann der
vom System aktivierte Bedienungshilfedienst den Start direkt aus einem
Wake-Word-Auftrag versuchen. Beide Aktivierungswörter und ihre Schwellen
entsprechen V0.4.6.

Der Dienst verarbeitet ausschließlich semantische Bedienelemente des
aktiven ChatGPT-Fensters. Pro Auftrag läuft die Suche höchstens zehn
Sekunden. Bei einem eindeutigen, sichtbaren und aktivierten Sprachknopf
wird höchstens ein Klick versucht. Ein erkennbar bereits laufender
Sprachmodus, ein App-Wechsel nach Sichtbarkeit, ein gesperrter Bildschirm
oder das Abschalten des Dienstes beendet den Versuch. Die App nutzt
keine Bildschirmkoordinaten und speichert nur den lokalen Ablaufstatus.
Systemdialoge bleiben der manuellen Bestätigung vorbehalten.

## Einrichtung auf dem Samsung

1. In älteren Testversionen die Erkennung stoppen. Die neue Test-APK
   installieren und EP Voice öffnen.
2. „Bedienungshilfe öffnen“ antippen. Unter „Installierte Apps“ den
   Eintrag „EP Voice – ChatGPT-Sprachstart“ auswählen und einschalten.
   Falls Android die Installation oder die Freigabe sperrt, den Test
   beenden. Hinweise dazu stehen in der [Sicherheitsprüfung](SECURITY_TEST_V047.md).
3. ChatGPT einmal regulär öffnen und Mikrofonfreigabe, eventuelle
   Ersteinrichtung des Sprachmodus und Anmeldung abschließen.
4. In EP Voice „Bedienungshilfe verbunden“ prüfen und Erkennung starten.
   Das Telefon für die ersten Tests eingeschaltet und entsperrt lassen.

## Gerätetests

- „ChatGPT-Sprachstart testen“ in EP Voice und danach in einem eigenen
  Durchlauf „Hey Jarvis“ und „Hey EP“. Erfolgsbedingung ist eine
  gesprochene ChatGPT-Antwort ohne Antippen des Sprachknopfs.
- Beide Wörter vom Samsung-Startbildschirm und mit einer anderen App
  im Vordergrund testen. Android oder Samsung können den App-Start aus
  dem Hintergrund blockieren. Der Status meldet einen Timeout, wenn
  ChatGPT nicht sichtbar wurde.
- Deutsch und Englisch, neuer und bestehender Chat, aktiver Sprachmodus
  und fehlende Mikrofonfreigabe in ChatGPT testen. Die genauen
  Bedienungshilfe-Beschriftungen hängen von der installierten Version ab.
- Wiederholte Worte, Hintergrundgespräche, Wechsel zu einer anderen
  App während der Suche, Ausschalten des Dienstes und Sperrbildschirm
  prüfen. Die Modelle können sich akustisch irren. Der Dienst begrenzt
  die Folge eines akzeptierten Treffers auf einen Klickversuch.
- Nach der Sprachübergabe in EP Voice die Erkennung erneut starten,
  wenn der nächste Durchlauf beginnen soll. Beim gesperrten oder
  ausgeschalteten Bildschirm steht die bisherige Benachrichtigung
  als Fallback zur Verfügung.

Der Build prüft APK und Modelle. Die tatsächliche Sprachübergabe und
die Beschriftungen des Sprachknopfs sind am Samsung zu prüfen.
