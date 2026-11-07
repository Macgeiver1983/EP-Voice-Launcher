"""Check original WAV recordings against new model with multi-frame threshold gate."""
from pathlib import Path
import json
import soundfile as sf
from train_hey_ep import make_frontend
from ort_native import Session
ROOT=Path(__file__).resolve().parent
class Adapter:
 @staticmethod
 def SessionOptions():return type('O',(),{'intra_op_num_threads':1})()
 @staticmethod
 def InferenceSession(file,sess_options=None,providers=None):
  class W:
   def __init__(self):self.native=Session(file)
   def run(self,_,feed):return [self.native.run(next(iter(feed.values())))]
  return W()
frontend=make_frontend(Adapter)
models={'old':Session(ROOT/'results/hey_ep_v1_before_hard_negative.onnx'),'new':Session(ROOT/'results/hey_ep_v2_confusable.onnx')}
result=[]
for file in sorted((ROOT/'wav_16khz').glob('hey_ep_*.wav')):
 x,sr=sf.read(file,dtype='float32');assert sr==16000
 windows=frontend(x)
 entry={'recording':file.name}
 for key,m in models.items():
  scores=[float(m.run(w[None])[0,0]) for _,w in windows]
  for threshold,streak in [(0.65,1),(0.85,4),(0.85,5),(0.90,4),(0.90,5),(0.93,4),(0.93,5),(0.95,4),(0.95,5)]:
   run=0;det=False
   for a in scores:
    run=run+1 if a>=threshold else 0
    det|=run>=streak
   entry[f'{key}_{threshold}_{streak}']=det
  entry[key+'_max']=max(scores) if scores else 0
 result.append(entry)
print(json.dumps(result,indent=2))
(ROOT/'results/evaluation_real_original_streaks.json').write_text(json.dumps(result,indent=2))
