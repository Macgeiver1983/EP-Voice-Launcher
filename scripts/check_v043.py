#!/usr/bin/env python3
"""Fast fail-closed V0.4.3 checks. Does not claim acoustic or Android tests."""
from pathlib import Path
import csv
import hashlib
import wave
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/assets/openwakeword'
EXPECTED={
 'embedding_model.onnx':'70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f',
 'hey_jarvis_v0.1.onnx':'94a13cfe60075b132f6a472e7e462e8123ee70861bc3fb58434a73712ee0d2cb',
 'melspectrogram.onnx':'ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f',
}
for name,digest in EXPECTED.items():
    data=(BASE/name).read_bytes()
    assert hashlib.sha256(data).hexdigest()==digest, f'V0.4.2 original asset changed: {name}'
print('PASS – all 3 V0.4.2 ONNX assets have original SHA256')
skip_audio='--skip-audio' in sys.argv
if not skip_audio:
    manifest=ROOT/'training/hey_ep/manifest.csv'
    with manifest.open('r',encoding='utf-8-sig',newline='') as f:rows=list(csv.DictReader(f))
    assert len(rows)==10
    assert {r['klasse'] for r in rows}=={'positive'}
    for row in rows:
        file=ROOT/'training/hey_ep/wav_16khz'/row['wav']
        with wave.open(str(file)) as w:
            assert (w.getframerate(),w.getnchannels(),w.getsampwidth())==(16000,1,2), str(file)
            assert w.getnframes()>16000
print('PASS – ten original 16kHz mono WAV training recordings' if not skip_audio else 'SKIPPED – private WAV files intentionally excluded from public GitHub')
core=(ROOT/'app/src/main/java/com/openwakeword/OpenWakeWord.kt').read_text()
service=(ROOT/'app/src/main/java/de/europlast/epvoice/WakeService.kt').read_text()
assert 'runWakeWordModel(features, classifier)' in core
assert 'val embedding = computeEmbedding(melWindow)' in core
assert 'val newMelFrames = computeMelSpectrogram(audioSlice)' in core
assert 'classifier.threshold' in core and 'lastAnyDetectionTime' in core
assert 'classifier.consecutiveFramesSeen' in core and 'classifier.consecutiveFramesRequired' in core
assert 'if (!classifier.optional) throw error' in core
assert 'if (!config.optional) throw error' in core
assert 'addModelAsset("openwakeword/hey_ep.onnx", "Hey EP"' in service
assert 'showLaunchNotification(wakeWord)' in service
assert 'modelThreshold = 0.95f' in service and 'consecutiveFrames = 4' in service
print('PASS – shared frontend, separated classifiers and threshold, global trigger guard, optional EP fallback')
if '--require-model' in sys.argv[1:]:
    ep=BASE/'hey_ep.onnx'
    assert ep.exists(), 'No trained hey_ep.onnx: release blocked'
    import numpy as np
    try:
        import onnx,onnxruntime as ort
        onnx.checker.check_model(str(ep))
        ses=ort.InferenceSession(str(ep),providers=['CPUExecutionProvider'])
        assert len(ses.get_inputs())==1 and ses.get_inputs()[0].shape==[1,16,96]
        z=ses.run(None,{ses.get_inputs()[0].name:np.zeros((1,16,96),dtype=np.float32)})[0]
        print('PASS – ONNX checker and CPU ONNX Runtime loaded model')
    except ImportError:
        # Fallback for this offline development image, which has a native ORT C library
        # but cannot download its Python binding. GitHub Actions installs packages.
        import sys
        sys.path.insert(0,str(ROOT/'training/hey_ep'))
        from ort_native import Session
        native=Session(ep)
        assert native.input_name=='x.1' and native.output_name=='score'
        z=native.run(np.zeros((1,16,96),dtype=np.float32))
        assert z.shape==(1,1)
        print('PASS – native ONNX Runtime loaded model (onnx Python checker unavailable)')
    assert z.shape==(1,1) and np.isfinite(z).all()
    assert (0<=z).all() and (z<=1).all()
    print('PASS – real Hey EP ONNX validates; no claim about recognition accuracy')
else:
    print('RELEASE MODEL GATE NOT RUN; no Hey EP recognition claim')
