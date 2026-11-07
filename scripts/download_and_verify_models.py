"""Audit the exact bundled engine models; synthetic checks are not speech tests."""
from pathlib import Path
from urllib.request import urlopen, Request
import argparse
import hashlib
import zipfile

REVISION = 'aa7bf538e12bbb363aff47de453616b12d6df3d1'
EXPECTED = {'embedding_model.onnx': '70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f', 'hey_jarvis_v0.1.onnx': '94a13cfe60075b132f6a472e7e462e8123ee70861bc3fb58434a73712ee0d2cb', 'melspectrogram.onnx': 'ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f'}
ASSETS = Path('app/src/main/assets/openwakeword')


def verify(data, name):
    actual = hashlib.sha256(data).hexdigest()
    if actual != EXPECTED[name]:
        raise RuntimeError(f'{name}: SHA-256 mismatch: {actual}')
    return f'{name}: OK; bytes={len(data)}; sha256={actual}'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    report = Path('ci-apk-model-report.txt' if args.apk else 'ci-model-report.txt')
    lines = ['EP Voice: exact engine model audit; no acoustic recognition test']
    try:
        if args.apk:
            with zipfile.ZipFile(args.apk) as apk:
                for name in EXPECTED:
                    data = apk.read('assets/openwakeword/' + name)
                    lines.append(verify(data, name))
                    if data != (ASSETS / name).read_bytes():
                        raise RuntimeError(f'{name}: APK differs from project assets')
                ep = 'hey_ep.onnx'
                deployed = apk.read('assets/openwakeword/' + ep)
                expected_ep = (ASSETS / ep).read_bytes()
                if deployed != expected_ep:
                    raise RuntimeError('APK Hey EP model differs from source asset')
                lines.append(f'{ep}: APK asset OK; bytes={len(deployed)}; sha256={hashlib.sha256(deployed).hexdigest()}')
        else:
            import onnx
            import onnxruntime as ort
            import numpy as np
            out = Path('ci-models')
            out.mkdir(exist_ok=True)
            base = f'https://raw.githubusercontent.com/msnilsen/openwakeword-android/{REVISION}/openwakeword/src/main/assets/openwakeword/'
            sessions = {}
            for name in EXPECTED:
                req = Request(base + name, headers={'User-Agent': 'EPVoice-CI/0.4.2'})
                with urlopen(req, timeout=30) as response:
                    data = response.read(20_000_001)
                lines.append(verify(data, name))
                if data != (ASSETS / name).read_bytes():
                    raise RuntimeError(f'{name}: project assets differ from pinned upstream')
                path = out / name
                path.write_bytes(data)
                onnx.checker.check_model(onnx.load(str(path)))
                sessions[name] = ort.InferenceSession(str(path), providers=['CPUExecutionProvider'])
            mel = sessions['melspectrogram.onnx'].run(None, {'input': np.zeros((1,1760),np.float32)})[0]
            frames = mel.reshape(-1,32) / 10.0 + 2.0
            if frames.shape != (8,32):
                raise RuntimeError(f'Unexpected mel frames: {frames.shape}')
            emb = sessions['embedding_model.onnx'].run(None, {'input_1': np.ones((1,76,32,1),np.float32)})[0]
            wake = sessions['hey_jarvis_v0.1.onnx'].run(None, {'x.1': np.zeros((1,16,96),np.float32)})[0]
            if emb.shape != (1,1,1,96) or wake.shape != (1,1):
                raise RuntimeError('Unexpected embedding/wake shape')
            lines.append(f'Synthetic inference OK: mel={mel.shape}; 8 time slices; embedding={emb.shape}; classifier={wake.shape}')
            lines.append('NOT TESTED: real Hey Jarvis audio, Android microphone, battery, lock screen, ChatGPT voice.')
    except Exception as error:
        lines.append(f'FAILED: {type(error).__name__}: {error}')
        raise
    finally:
        report.write_text('\n'.join(lines)+'\n', encoding='utf-8')
        print('\n'.join(lines))


if __name__ == '__main__':
    main()
