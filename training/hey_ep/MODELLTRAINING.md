# Hey E-Pe Modell – Training vom 07.11.2026

Das erste echte trainierte Modell `results/hey_ep.onnx` wurde mit dem nativen ONNX Runtime 1.21 C API geladen und gegen die entsprechenden PyTorch-Ausgaben numerisch abgeglichen. Es ist keine Kopie von Jarvis. Die originale OpenWakeWord Mel+Embedding-Pipeline wird unverändert benutzt.

Reale Daten: alle 10 WAVs (drei Personen laut Auftrag; Zuordnung nicht dokumentiert).
Zusätzlich: 164 positive und 147 negative erste synthetische/augmentierte Dateien; anschließend 24 weitere positive und 68 weitere negative Hard-Negative-/TTS-Beispiele. Insgesamt 403 generierte/augmentierte Dateien.

Training V3: 55 Epochen, alle negativen 80-ms-Zeitfenster einbezogen. Im internen synthetischen Holdout: 60/60 positive Fenster, 0/722 falsch positive Fenster. Gesamter Originalaudio-Sanity-Test: 10/10 bekannte reale Phrasen erkannt. Bei 21 synthetischen Original-Negativclips 0 Auslösungen; bei 68 gezielt nachtrainierten Negativclips 0 Auslösungen.

**Kritisch: unabhängigerer Test mit 66 neu formulierten, nicht trainierten synthetischen Negativclips und zwei neuen eSpeak-Stimmvarianten: 18 Falschauslösungen.** Zusätzlich 8/8 neu erzeugte synthetische Zielwort-Samples erkannt. Die Generalisierung ist noch unzureichend! Das Modell ist **NUR EIN EXPERIMENTELLER TESTKANDIDAT**.

Gerätetest, echte unabhängige Testsprecher, TV-/Gesprächs-Simulation über Stunden und eine Fehlalarmrate pro Stunde fehlen. Die Schwelle 0,65 ist nicht freigegeben. Das Modell wurde zur V0.4.3-Testintegration unter `app/src/main/assets/openwakeword/hey_ep.onnx` kopiert. **Nicht für den unbeaufsichtigten Dauerbetrieb freigeben.**

Siehe `results/train_report.json`, `results/evaluation_original_audio.json`, `results/evaluation_extended.json`, `results/evaluation_novel.json` und `training/hey_ep/README_TRAINING.md`.
