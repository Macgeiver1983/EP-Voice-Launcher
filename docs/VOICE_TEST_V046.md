# V0.4.6 – ChatGPT-Start und Hey-EP-Diagnose

Auf dem Samsung lieferte V0.4.5 drei Jarvis-Treffer, keinen EP-Treffer
und meldete, dass die getestete ChatGPT-Activity nicht verfügbar war.
Der Versuch hat damit keinen ChatGPT-Sprachstart auf diesem Gerät gezeigt.

V0.4.6 öffnet die installierte ChatGPT-App über ihren regulären
Android-Start nach Freigabe des Mikrofons. ChatGPT kann anschließend über
[Einstellungen → Sprache → Mit Sprache starten](https://help.openai.com/en/articles/20001274-chatgpt-voice)
bei einem neuen oder leeren Chat selbst das Sprachgespräch starten.
Die Option setzt eine unterstützte ChatGPT-App-Version voraus. EP Voice
kann die Option und den Sprachstatus von ChatGPT nicht auslesen. Das Öffnen
eines vorhandenen Chats kann entsprechend ohne Sprachstart enden.

Beide Wake-Words verwenden bei sichtbarer EP-Voice-App denselben Weg.
Die Benachrichtigung aus dem Hintergrund bleibt verfügbar. Es gibt
keine Umgehung von Androids Beschränkungen für Hintergrundstarts.

Die Modelldateien und die Erkennungsregeln bleiben gleich. Der aktuelle
Score wird ungefähr einmal pro Sekunde angezeigt. Zusätzlich erfasst
die Diagnose seit Start der Erkennung den höchsten EP-Score und die
längste Folge von bestätigten EP-Messungen bis maximal vier. Für eine
Auslösung sind mindestens 0,95 und vier aufeinanderfolgende Messungen
erforderlich. Die gemeinsame Auslösesperre bleibt aktiv.

## Gerätetest

1. In allen älteren EP-Voice-Versionen die Spracherkennung stoppen.
2. In der normalen ChatGPT-App die Mikrofonfreigabe prüfen, unter Sprache
   den automatischen Sprachstart aktivieren, sofern vorhanden, und einen
   neuen Chat ohne Eingabe öffnen.
3. In V0.4.6 „ChatGPT-Start testen“ antippen. Prüfen, ob ChatGPT zuhört
   und hörbar antwortet.
4. Erkennung neu starten, EP Voice sichtbar lassen, jeweils ein Wake-Word
   sagen und den Start prüfen. Bei null EP-Treffern vor dem Neustart
   „Spitze“ und „Bestätigung“ aus der Diagnose ablesen.
