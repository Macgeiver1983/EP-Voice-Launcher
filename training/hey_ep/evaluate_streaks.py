"""Offline stream-level comparison for unchanged v1 and experimental v2.
Synthetic unseen voices only. Model decisions are sampled every 80 ms.
No use of real recordings outside local process.
"""
import json
from pathlib import Path
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
feat=make_frontend(Adapter)
models={'original':Session(ROOT/'results/hey_ep_v1_before_hard_negative.onnx'),
        'hard_negative_v2':Session(ROOT/'results/hey_ep_v2_confusable.onnx')}
import ast
_original=ast.parse((ROOT/'evaluate_novel.py').read_text())
NEGATIVE=ast.literal_eval(next(n.value for n in _original.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='NEGATIVE' for t in n.targets)))
phrases=[('pos','Hey E Pe',s,p) for s,p in [(125,40),(137,45),(149,50),(161,55)]]+[( 'neg',v,138,52) for v in NEGATIVE]
res={k:{'positive':[], 'negative':[]} for k in models}
for voice in ('de+f5','de+m5'):
 for kind,phrase,speed,pitch in phrases:
  x=render(phrase,voice,speed,pitch)
  x=np.concatenate((np.zeros(6000,np.float32),x,np.zeros(6000,np.float32)))
  wins=feat(x)
  for modelname,model in models.items():
   scores=np.array([float(model.run(f[None])[0,0]) for t,f in wins])
   threshold=0.65
   runs=[];current=0
   for s in scores:
    if s>=threshold:current+=1
    else:runs.append(current);current=0
   runs.append(current)
   res[modelname]['positive' if kind=='pos' else 'negative'].append({'voice':voice,'phrase':phrase,'max_score':round(float(scores.max()) if len(scores) else 0,5),'max_consecutive':max(runs),'scores':[round(float(a),5) for a in scores]})
for k,v in res.items():
 print('\nMODEL:',k)
 for consecutive in [1,2,3,4,5,6,8]:
  tp=sum(i['max_consecutive']>=consecutive for i in v['positive'])
  fp=sum(i['max_consecutive']>=consecutive for i in v['negative'])
  print(f'consecutive={consecutive}: pos={tp}/8; neg={fp}/66')
(ROOT/'results/evaluation_streaks.json').write_text(json.dumps(res,indent=2,ensure_ascii=False),encoding='utf8')

for k,v in res.items():
 print('\nTHRESHOLD SWEEP', k)
 for threshold in [.65,.75,.85,.9,.95,.98,.99,.995]:
  for consecutive in [3,5,7]:
   def detected(item):
    streak=0
    for a in item['scores']:
     streak=streak+1 if a>=threshold else 0
     if streak>=consecutive:return True
    return False
   tp=sum(detected(i) for i in v['positive'])
   fp=sum(detected(i) for i in v['negative'])
   print(threshold,consecutive, f'{tp}/8',f'{fp}/66')
