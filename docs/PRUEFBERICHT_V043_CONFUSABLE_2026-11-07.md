# EP Voice Launcher 0.4.3 – Weiterentwicklung 7. November 2026

## Faktisch umgesetzt

- Ausgangsprojekt ist die vom Benutzer gelieferte V0.4.3 inklusive Modell und 10 privater WAV-Dateien.
- Ein *neuer* Hey-EP-Klassifikator wurde auf Grundlage des bisherigen Trainingscodes neu trainiert, einschließlich 312 zusätzlich generierter, ähnlich klingender negativer Sprachproben.
- Das Android-Projekt nutzt für beide ONNX-Klassifikatoren denselben AudioRecord-, Mel- und Embedding-Datenstrom.
- Hey EP verwendet in dieser experimentellen Variante `score >= 0.95` während **4 aufeinanderfolgenden 80-ms-Vorhersagefenstern**; Hey Jarvis verwendet unverändert die historische Schwelle 0.50 und eine einzelne Vorhersage.
- Schlägt das optionale Hey-EP-Modell beim Laden oder während der Inferenz fehl, läuft Jarvis weiter, soweit die gemeinsame Mel-/Embedding-Verarbeitung funktionsfähig ist.
- Die drei unveränderten V0.4.2-ONNX-Modelle wurden über ihre SHA256-Werte verifiziert.

## Gemessene Ergebnisse – synthetische Negativtests (66 Fälle, Stimmen `de+f5` / `de+m5`)

| Modell/Schaltung | Erkennung synthetisches „Hey EP“ | Synthetische Fehlauslösungen |
|---|---:|---:|
| Ursprüngliches V0.4.3-Modell, Score >=0.65, 1 Fenster | 8/8 | 18/66 |
| Neues Modell, Score >=0.65, 1 Fenster | 8/8 | 23/66 |
| Neues Modell, Score >=0.95, 4 Fenster (ausgewählt) | **8/8** | **4/66** |
| Neues Modell, Score >=0.95, 5 Fenster (nicht ausgewählt) | 8/8 | 3/66 |

Bei den **zehn echten Originalaufnahmen**, die bereits im Training benutzt wurden, erkannt: **10/10** mit 0.95 und 4 Fenstern. Mit 5 Fenstern nur 9/10. Das ist ein Trainingsdatensatz-Sanity-Check, **keine unabhängige Validierung**.

**Interpretation:** Relativ zum ursprünglichen Stand reduziert die Kombination aus neuem Modell und 4-Fenster-Bestätigung die beobachteten Fehlalarme in diesem festgelegten synthetischen Test von 18 auf 4. Es verbleiben nennenswerte falsch positive Erkennungen, insbesondere bei ähnlich klingenden Worten. Echte unbekannte Sprecher, reale Gespräche, Fernsehen, Hintergrundgeräusche und Fehlalarme pro Stunde sind **nicht getestet**. Die Parameterauswahl wurde anhand des genannten Tests vorgenommen; dieser ist damit kein unabhängiger Abschlussnachweis.

## Technische Tests

- Original-Engine-Prüfsummen: PASS.
- Originale WAVs: 10 WAVs, 16 kHz Mono PCM 16 Bit: PASS.
- Python-Quellcode: syntaktisch geprüft (compileall): PASS.
- ONNX-Berechnung: CPU-Inferenz mit der vorhandenen nativen ONNX Runtime: PASS (Score-Ausgabe `[1,1]` mit Input `[1,16,96]` gemäß Trainings- und Prüfscripten).
- Automatische Android-Quellcodeprüfungen (ohne Gradle-Build): PASS.
- Gradle-Compile/Android-Lint: **NICHT AUSGEFÜHRT** (Android SDK und Gradle fehlen hier).
- GitHub Actions APK: **NICHT AUSGEFÜHRT** (GitHub-Connector lehnt branch-Schreibzugriff mit 403 ab).
- Samsung-Gerätetest: **NICHT AUSGEFÜHRT**.
- Der Zustand ist **EXPERIMENTELL, NICHT PRODUKTIONSFREIGEGEBEN**.

## SHA-256 der ONNX-Assets

- `melspectrogram.onnx`: `ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f`
- `embedding_model.onnx`: `70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f`
- `hey_jarvis_v0.1.onnx`: `94a13cfe60075b132f6a472e7e462e8123ee70861bc3fb58434a73712ee0d2cb`
- `hey_ep.onnx`: `854c045dacef4ed0bc47e7e333cab344b536da72028d6dbc99e5cb250f8ada0c`

## Datenschutz / Veröffentlichung

Die privaten WAV-/M4A-Dateien, erzeugten WAV-Dateien und der Featurecache dürfen nicht nach GitHub übertragen werden. Das öffentliche Repository ist zum Zeitpunkt der Prüfung nicht verändert worden; die GitHub-API gab bei Branch-Erstellung **403 Resource not accessible by integration** zurück. Für einen Build muss zunächst der sichere Quellcode-ZIP auf einem neuen Branch veröffentlicht werden, nicht der private Projekt-ZIP.

## Reproduzierbarkeit

Trainingsverzeichnis: `training/hey_ep`. Erst originalen Korpus herstellen (`generate_corpus.py`), anschließend `extend_hard_corpus.py`, `add_confusable_phrases.py`, dann `train_hey_ep.py --epochs 50 --model-out results/hey_ep_v2_confusable.onnx`. Das fertige Modell gehört unter `app/src/main/assets/openwakeword/hey_ep.onnx`. Erneute Tests: `evaluate_streaks.py`, `test_original_streaks.py`; diese benötigen die vertraulichen Trainingsdaten und die lokal installierte ONNX Runtime. Für reproduzierbare Ergebnisse die Python-Versionen und die originale Datentrennung dokumentieren. Die synthetischen Evaluationsphrasen überschneiden sich inzwischen teilweise mit den Trainingsphrasen (andere eSpeak-Stimmen); es besteht weiterhin Anpassungs- und Evaluationsbias.

## Nächster Build

1. Ein GitHub-Branch `dev/v0.4.3-dual-wakeword` **vom erhaltenen Commit der V0.4.2** erstellen.
2. Ausschließlich das öffentliche Quellcode-Archiv (ohne `wav_16khz/`, `generated/`, `feature_cache/`, Trainings-`results/`) in den Branch übernehmen.
3. GitHub Actions `EP Voice – Modellprüfung und Android-Build` auf dem Branch ausführen.
4. `ep-voice-android`-Artefakt herunterladen, `app-debug.apk` auf Samsung installieren und Original-Jarvis wie Hey EP mit unabhängigen Sprechern testen.
5. Erst nach negativen Stundentests (Fernsehton/Unterhaltung) und echten Sprechertests eine Freigabe erwägen.
