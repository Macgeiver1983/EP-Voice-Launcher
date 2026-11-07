"""Additional independent-from-training synthetic phrases and synthetic voices.
No claim of independence of real human speakers.
"""
from pathlib import Path
import json
import numpy as np
from train_hey_ep import make_frontend
from generate_corpus import render
from ort_native import Session

ROOT=Path(__file__).resolve().parent
class Adapter:
    @staticmethod
    def SessionOptions():return type('O',(),{'intra_op_num_threads':1})()
    @staticmethod
    def InferenceSession(file,sess_options=None,providers=None):
        class Wrapper:
            def __init__(self):self.native=Session(file)
            def run(self,_out,feed):return [self.native.run(next(iter(feed.values())))]
        return Wrapper()
frontend=make_frontend(Adapter)
classifier=Session(ROOT/'results/hey_ep.onnx')

def predict(voice,phrase,speed,pitch):
    audio=render(phrase,voice,speed,pitch)
    audio=np.concatenate([np.zeros(6000,dtype=np.float32),audio,np.zeros(6000,dtype=np.float32)])
    windows=frontend(audio)
    return max((float(classifier.run(w[None])[0,0]) for _,w in windows),default=0.)

NEGATIVE=[
 'Hey B','Hey D','Hey TP','Hey DP','Hey IP',
 'Hey A Pe','Hey E Po','Hey E Pu','Hey E Pi','Hey E Pü',
 'Hey M Pe','Hey E Bä','Hey E Ka','Hey E Te','Hey E See',
 'Hey P B','Hey P Pe','Hey E F','Hey E Em','Hey E Na',
 'Die Maschine wird gestartet','Das Teil ist bereits fertig',
 'Heute bekommen wir eine Lieferung','Wann kommt der Mitarbeiter zurück',
 'Dieses Jahr haben wir viele Aufträge',
 'Hey Marvin','Hey Marie','Hey Alex','Hey Alfons',
 'Kannst du bitte EP aufrufen','Laut Programm ist alles in Ordnung',
 'Das EP hat ein neues Modell','Die Computersteuerung ist aktiviert'
]
items=[]
for voice in ('de+f5','de+m5'):
    for phrase in ['Hey E Pe']*4:
        j=sum(r['voice']==voice for r in items)
        items.append({'kind':'positive','voice':voice,'phrase':phrase,'score':predict(voice,phrase,125+j*12,40+j*5)})
    for phrase in NEGATIVE:
        items.append({'kind':'negative','voice':voice,'phrase':phrase,'score':predict(voice,phrase,138,52)})
summary={'threshold':.65,'positive_detected':sum(x['score']>=.65 for x in items if x['kind']=='positive'),
         'positive_total':8,'negative_false_triggers':sum(x['score']>=.65 for x in items if x['kind']=='negative'),
         'negative_total':len(NEGATIVE)*2,
         'note':'Novel text and espeak variant unseen during training. All synthetic, no real speaker-independence or field false alarms measurement.'}
(ROOT/'results/evaluation_novel.json').write_text(json.dumps({'summary':summary,'items':items},ensure_ascii=False,indent=2))
print(json.dumps(summary,ensure_ascii=False,indent=2))
for item in items:
    if (item['kind']=='negative' and item['score']>=.65) or (item['kind']=='positive' and item['score']<.65):
        print('ERROR',item['kind'],item['voice'],item['phrase'],round(item['score'],4))
