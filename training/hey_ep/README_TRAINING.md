# Hey EP – reproduzierbares Modelltraining (V0.4.3 DEV)

## Reproduzieren

Mit Python 3.11 / Linux, `onnxruntime`, `onnx`, PyTorch, scipy, soundfile und eSpeak:

```bash
cd training/hey_ep
python3.11 -m venv .venv
. .venv/bin/activate
pip install -r requirements-training.txt
sudo apt-get install espeak
python generate_corpus.py
python extend_hard_corpus.py
python add_confusable_phrases.py
python train_hey_ep.py --epochs 50 --model-out results/hey_ep_v2_confusable.onnx
python evaluate_hey_ep.py        # benötigt zusätzlich ort_native.py + systemweite ONNX Runtime 1.21
python evaluate_extended.py      # nur synthetische Tests
python evaluate_novel.py         # synthetischer Test mit nicht trainierten Phrasen
```

**Hinweis:** Für diese Entwicklungsumgebung wurde die native `libonnxruntime.so.1.21` über `ort_native.py` verwendet. Der Export des trainierten Netzes erfolgte über den spezialisierten ONNX-Proto-Exporter `minimal_onnx_export.py`. Technische Ein- und Ausgabe des exportierten Modells wurden erfolgreich mit ONNX Runtime geprüft. Der alternative standardmäßige PyTorch-ONNX-Export ist im Trainingsskript ebenfalls implementiert.

## Stand

`results/hey_ep.onnx` wurde **tatsächlich trainiert**. Es ist auch testweise unter `app/src/main/assets/openwakeword/hey_ep.onnx` eingebunden. Es ist wegen der dokumentierten unabhängigen synthetischen Fehlauslösungen **NICHT für produktiven Dauerbetrieb geeignet**. Siehe `PRUEFBERICHT_V043_2026-11-07.md`.

Original-WAVs sind privat; `.gitignore` verhindert ihre versehentliche Übernahme in das öffentliche GitHub-Repository. Die neuen Modelle können Stimmencharakteristika enthalten und müssen vor öffentlicher Veröffentlichung entsprechend geprüft werden.

Benötigt vor Freigabe: reale unabhängige Testsprecher, sauberere Negativ-Korpora, stundenlange Fehlalarmtests, Samsung-Test und CI-Android-Build.

## Ergänzung V0.4.3 – Konfusionsmodell
Neu: `add_confusable_phrases.py`, `evaluate_streaks.py`, `test_original_streaks.py`; Experimente dokumentiert in `docs/PRUEFBERICHT_V043_CONFUSABLE_2026-11-07.md`. Das Modell ist **nicht für produktiven Dauerbetrieb validiert**.
