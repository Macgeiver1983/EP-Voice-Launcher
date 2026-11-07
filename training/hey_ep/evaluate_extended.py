#!/usr/bin/env python3
"""Additional *synthetic* holdout phrases and eSpeak variants: not a field test."""
from pathlib import Path
import json
import numpy as np
import soundfile as sf
from ort_native import Session
from train_hey_ep import make_frontend
from generate_corpus import render

ROOT=Path(__file__).resolve().parent
class Adapt:
    @staticmethod
    def SessionOptions(): return type('O',(),{'intra_op_num_threads':1})()
    @staticmethod
    def InferenceSession(path,sess_options=None,providers=None):
        class Wrap:
            def __init__(self,path):self.s=Session(path)
            def run(self,names,feeds):return [self.s.run(next(iter(feeds.values())))]
        return Wrap(path)

feat=make_frontend(Adapt)
cls=Session(ROOT/'results/hey_ep.onnx')

PHRASES=['Hey E','Hey Pe','E Pe','Hey Epe','Hey B Pe','Hey E Tee','Hey PC',
'Hey AP','Hey E B','Hey E D','Hey E B Pe','Hey PT','Hey E V', 'Hey P E',
'Hey Beta','Hey Elli','Hey Pascal','Hey Evi','Hey Lars', 'Hey Edgar',
'Hey Elke','Hey Eddie','E Pe ist fertig','Das EP System ist hier',
'Wir haben ein Problem','Der Kunde braucht eine Antwort','Das Gerät ist aus',
'Die Produktion läuft heute gut','Morgen müssen wir die Lieferung prüfen',
'Ich benötige das Prüfprotokoll','Hey Siri, mach das Licht an','Hey Google, wie spät ist es?',
'Schalte die Maschine wieder ein','Das ist eine ganz normale Unterhaltung']

def score(x):
    features=feat(x)
    if not features:return 0.
    return max(float(cls.run(f[None])[0,0]) for _,f in features)

rows=[]
for f in sorted((ROOT/'generated/positive').glob('tts_06_*.wav')):
    x,sr=sf.read(f,dtype='float32');assert sr==16000
    rows.append({'type':'heldout_positive_tts','phrase':'Hey E Pe','file':f.name,'score':score(x)})
for v in ['de+f4','de+m4']:
    for phrase in PHRASES:
        x=render(phrase,v,133,47)
        x=np.concatenate([np.zeros(5000,dtype=np.float32),x,np.zeros(5000,dtype=np.float32)])
        rows.append({'type':'novel_negative_tts','phrase':phrase,'voice':v,'score':score(x)})
sum_pos=sum(r['score']>=.65 for r in rows if r['type']=='heldout_positive_tts')
sum_neg=sum(r['score']>=.65 for r in rows if r['type']=='novel_negative_tts')
summary={'threshold':.65,'heldout_tts_positives_recognized':sum_pos,'heldout_tts_positives_total':12,
         'novel_tts_negatives_triggered':sum_neg,'novel_tts_negatives_total':len(PHRASES)*2,
         'warning':'Synthetic phrases only; no independent real human test, no TV false alarms/hour.'}
(ROOT/'results/evaluation_extended.json').write_text(json.dumps({'summary':summary,'rows':rows},indent=2,ensure_ascii=False))
print(json.dumps(summary,ensure_ascii=False,indent=2))
for r in rows:
    if r['type']=='novel_negative_tts' and r['score']>=.65:print('FALSE TRIGGER',r['voice'],r['phrase'],round(r['score'],4))
