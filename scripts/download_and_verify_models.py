from pathlib import Path
from urllib.request import urlopen, Request
import hashlib
import onnx

base = 'https://huggingface.co/Soulcreek2/speechkit-wakeword-models/resolve/main/'
files = ['hey_jarvis.onnx', 'melspectrogram.onnx', 'embedding_model.onnx']
out = Path('ci-models'); out.mkdir(exist_ok=True)
lines = ['EP Voice – Modellprüfung (kein Spracherkennungstest)']
for name in files:
    req = Request(base + name + '?download=true', headers={'User-Agent':'EPVoice-CI/1.0'})
    with urlopen(req, timeout=90) as resp:
        data = resp.read()
    if len(data) < 10000:
        raise RuntimeError(f'{name}: Datei unerwartet klein ({len(data)} Bytes)')
    path = out/name; path.write_bytes(data)
    model = onnx.load(str(path))
    onnx.checker.check_model(model)
    digest = hashlib.sha256(data).hexdigest()
    lines.append(f'{name}: OK; bytes={len(data)}; sha256={digest}')
Path('ci-model-report.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines))
