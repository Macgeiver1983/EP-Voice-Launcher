#!/usr/bin/env python3
"""Deterministic, local German eSpeak corpus for Hey E-Pe; not a speaker-independent proof.

Reuses every real recording WITHOUT modifying it. Generates synthetics and confusables
as additional samples; audio stays local. Requires espeak, numpy, scipy, soundfile.
"""
import argparse
import csv
import hashlib
import io
from pathlib import Path
import subprocess
import wave

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT = Path(__file__).resolve().parent
PHRASE = 'Hey E Pe'
VOICE_VARIANTS = ['de', 'de+f1', 'de+m1', 'de+f2', 'de+m2', 'de+f3', 'de+m3']
NEGATIVES = [
    'Hey', 'E Pe', 'EP', 'Hey Peter', 'Hey Petra', 'Hey Apple', 'Hey Siri',
    'Hey Jarvis', 'Hey Eberhard', 'Hey Emily', 'Hey Eva', 'Ey', 'Hey der PC',
    'Das ist der Prüfbericht', 'Wir starten die Maschine',
    'Die Lieferung ist fertig', 'Kannst du das bitte öffnen',
    'Wie spät ist es', 'Heute ist das Wetter gut',
    'Bitte ruf mich morgen wieder an', 'Der Mitarbeiter prüft die Felge',
]


def render(text, voice, speed, pitch):
    proc = subprocess.run(['espeak', '-v', voice, '-s', str(speed), '-p', str(pitch),
                           '--stdout', text], check=True, capture_output=True)
    with wave.open(io.BytesIO(proc.stdout), 'rb') as f:
        assert f.getnchannels() == 1 and f.getsampwidth() == 2
        data = np.frombuffer(f.readframes(f.getnframes()), dtype='<i2').astype(np.float32)/32768.0
        rate = f.getframerate()
    if rate != 16000:
        from math import gcd
        g = gcd(rate,16000)
        data = resample_poly(data,16000//g,rate//g)
    return data


def write_one(path, x):
    path.parent.mkdir(parents=True,exist_ok=True)
    sf.write(str(path), x, 16000, subtype='PCM_16')


def main():
    pa = argparse.ArgumentParser()
    pa.add_argument('--output', type=Path, default=ROOT/'generated')
    pa.add_argument('--seed', type=int, default=43)
    args = pa.parse_args()
    rng=np.random.default_rng(args.seed)
    out=args.output
    manifest=[]
    real=sorted((ROOT/'wav_16khz').glob('hey_ep_*.wav'))
    assert len(real)==10, f'Need 10 originals, got {len(real)}'
    # Track source group to avoid train/test augment leakage.
    for i, f in enumerate(real, 1):
        x,rate=sf.read(str(f),dtype='float32')
        assert rate==16000 and x.ndim==1
        for j in range(8):
            scale=float(rng.uniform(.55,1.10))
            noise=float(rng.choice([0.0,0.0007,0.002,0.005]))
            # Keep original cadence; apply random quiet lead/tail, noise and gain.
            y=x*scale+ rng.normal(0, noise,len(x)).astype('float32')
            lead=np.zeros(int(rng.uniform(.15,.85)*16000),dtype=np.float32)
            tail=np.zeros(int(rng.uniform(.2,.7)*16000),dtype=np.float32)
            y=np.clip(np.concatenate([lead,y,tail]),-.999,.999)
            target=out/'positive'/f'real_{i:02d}_aug_{j:02d}.wav'
            write_one(target,y)
            manifest.append((target.relative_to(out).as_posix(),'positive',f'real_{i:02d}','real_augmented'))
    for v,voice in enumerate(VOICE_VARIANTS):
        for k in range(12):
            speed=int(rng.integers(115,175))
            pitch=int(rng.integers(32,68))
            x=render(PHRASE,voice,speed,pitch)
            lead=np.zeros(int(rng.uniform(.3,.9)*16000),dtype=np.float32)
            tail=np.zeros(int(rng.uniform(.35,.9)*16000),dtype=np.float32)
            x=np.concatenate([lead,x,tail]).astype('float32')
            target=out/'positive'/f'tts_{v:02d}_{k:02d}.wav'
            write_one(target,x)
            manifest.append((target.relative_to(out).as_posix(),'positive',f'tts_voice_{v:02d}','synthetic_espeak'))
    for phrase_id,phrase in enumerate(NEGATIVES):
        for v,voice in enumerate(VOICE_VARIANTS):
            x=render(phrase,voice,145,48)
            x=np.concatenate([np.zeros(6000,dtype='float32'),x,np.zeros(6000,dtype='float32')])
            target=out/'negative'/f'neg_{phrase_id:02d}_tts_{v:02d}.wav'
            write_one(target,x)
            manifest.append((target.relative_to(out).as_posix(),'negative',f'neg_phrase_{phrase_id:02d}','synthetic_espeak'))
    out.mkdir(parents=True,exist_ok=True)
    with (out/'manifest.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f);w.writerow(['file','label','source_group','type']);w.writerows(manifest)
    print(f'Generated: {sum(x[1]=="positive" for x in manifest)} positives, '
          f'{sum(x[1]=="negative" for x in manifest)} negatives. Groups prevent source leakage.')
    print('Synthetic corpus DOES NOT prove speaker-independent detection or low false positives.')


if __name__=='__main__': main()
