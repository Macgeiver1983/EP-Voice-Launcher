#!/usr/bin/env python3
"""Streaming-like ONNX wakeword test on originals and generated negative phrases."""
from pathlib import Path
import json,sys
import numpy as np
import soundfile as sf
from train_hey_ep import make_frontend
from ort_native import Session

ROOT=Path(__file__).resolve().parent
MODEL=ROOT/'results/hey_ep.onnx'
front=make_frontend(type('Adapter',(),{
    'SessionOptions':staticmethod(lambda:type('Options',(),{'intra_op_num_threads':1})()),
    'InferenceSession':staticmethod(lambda file,sess_options=None,providers=None:type('Wrapper',(),{
        'session':Session(file),
        'run':lambda self,names,inputs:[self.session.run(next(iter(inputs.values())))]
    })())
})())
classifier=Session(MODEL)
rows=[]
originals=sorted((ROOT/'wav_16khz').glob('*.wav'))
negatives=sorted((ROOT/'generated/negative').glob('*.wav'))
# All ten originals plus 21 synthetic confusables (German default voice)
files=[(p,'real_positive') for p in originals]+[(p,'tts_negative') for p in negatives if '_tts_00' in p.name]
for file,label in files:
    wav,sr=sf.read(file,dtype='float32')
    assert sr==16000
    features=front(wav)
    scores=np.array([float(classifier.run(x[None])[0,0]) for _,x in features])
    score=float(scores.max()) if len(scores) else None
    hit=score is not None and score>=.65
    row={'file':file.name,'label':label,'duration_s':round(len(wav)/16000,3),
         'max_score':round(score,5) if score is not None else None,'hit_0_65':hit}
    rows.append(row)
    print(label,file.name,'score',round(score,3) if score is not None else 'NO_WINDOW','HIT',hit,flush=True)
summary={
 'model':MODEL.name,
 'threshold':0.65,
 'real_positive_10_detected':sum(x['hit_0_65'] for x in rows if x['label']=='real_positive'),
 'real_positive_count':len(originals),
 'synthetic_negatives_triggered':sum(x['hit_0_65'] for x in rows if x['label']=='tts_negative'),
 'synthetic_negatives_count':sum(x['label']=='tts_negative' for x in rows),
 'note':'Real originals were part of training. This is a memorization/sanity check, NOT independent validation.'
}
(ROOT/'results/evaluation_original_audio.json').write_text(json.dumps({'summary':summary,'rows':rows},indent=2,ensure_ascii=False),encoding='utf8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
