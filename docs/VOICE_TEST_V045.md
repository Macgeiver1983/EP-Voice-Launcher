# EP Voice Launcher V0.4.5 Voice-Test

Wenn EP Voice sichtbar ist, fordern „Hey EP“ und „Hey Jarvis“ dieselbe
ChatGPT-Voice-Activity an. EP Voice gibt zuerst das Mikrofon frei und
wartet 400 ms vor dem Startversuch. „ChatGPT-Sprachmodus testen“ nutzt
denselben Einstieg ohne Wake-Word. „ChatGPT-App öffnen“ startet die
reguläre App. Im Hintergrund gilt weiterhin die V0.4.4-Benachrichtigung.

Die Voice-Activity ist eine experimentelle Integration. In einem
[Gerätetest auf einem Galaxy Fold 6](https://github.com/Pepsi1978/proggs/blob/6ae7074958a0f6d7787a377729025ecb974da501/VoiceKey/NEXT-SESSION.md#1-der-durchbruch-am-echten-gerät-verifiziert--das-wichtigste-asset)
erschien der Voice-Modus beim Start der Activity. Dort war ChatGPT als
Systemassistent eingestellt. OpenAI dokumentiert den Activity-Namen
nicht als öffentliche Schnittstelle. Die App prüft die Verfügbarkeit auf
dem Gerät und zeigt bei einem fehlenden Einstieg einen Fehler. Die
Diagnose „Sprachmodus angefordert“ bestätigt den Startversuch. Der
Sprechtest auf dem Gerät bestätigt erst das tatsächliche Sprachgespräch.

## Samsung-Test

1. Falls eine frühere EP-Voice-Testversion lauscht, dort die Erkennung stoppen.
2. V0.4.5 installieren. „ChatGPT-Sprachmodus testen“ antippen. Sobald
   ChatGPT startet, direkt etwas sagen und auf eine gesprochene Antwort achten.
3. Zurück zu EP Voice wechseln, die Erkennung starten und die App sichtbar
   lassen. „Hey EP“ sagen und direkt mit ChatGPT sprechen. Für „Hey Jarvis“
   den Test nach erneutem Start der Erkennung wiederholen.
4. Bei regulärem Chat, schwarzem Bildschirm oder fehlender Audioantwort
   den sichtbaren ChatGPT-Bildschirm und die Zeile „ChatGPT“ in EP Voice
   festhalten.

Der automatische Start bei einer anderen sichtbaren App sowie bei
gesperrtem Bildschirm ist mit diesem Teststand nicht eingerichtet.
[Android beschränkt das Öffnen von Apps im Hintergrund.](https://developer.android.com/guide/components/activities/background-starts)
