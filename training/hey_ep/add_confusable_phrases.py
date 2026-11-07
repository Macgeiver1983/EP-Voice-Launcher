"""Reproducible train-only acoustic hard negatives, using speakers separate from novel eval.

Do not use de+f5 / de+m5: they are reserved for independent synthetic-voice evaluation.
Never change or publish original human voice recordings.
"""
from pathlib import Path
import csv
import numpy as np
from generate_corpus import render, write_one
ROOT=Path(__file__).resolve().parent
OUT=ROOT/'generated'
PHRASES=[
 'Hey B','Hey D','Hey TP','Hey DP','Hey IP',
 'Hey A Pe','Hey E Po','Hey E Pu','Hey E Pi','Hey E Pü',
 'Hey M Pe','Hey E Bä','Hey E Ka','Hey E Te','Hey E See',
 'Hey P B','Hey P Pe','Hey E F','Hey E Em','Hey E Na',
 'Die Maschine wird gestartet','Das Teil ist bereits fertig',
 'Heute bekommen wir eine Lieferung','Wann kommt der Mitarbeiter zurück',
 'Dieses Jahr haben wir viele Aufträge',
 'Hey Marvin','Hey Marie','Hey Alex','Hey Alfons',
 'Kannst du bitte EP aufrufen','Laut Programm ist alles in Ordnung',
 'Das EP hat ein neues Modell','Die Computersteuerung ist aktiviert',
 'Das Gerät heißt EP', 'Hey, E Pe ist auf dem Weg','EP ist eine Abkürzung',
 'Hey Elektronik', 'Hey Eberhard', 'Hey Pepsi'
]
VOICES=('de+f4','de+m4','de+f3','de+m3')
with (OUT/'manifest.csv').open(encoding='utf-8',newline='') as f:rows=list(csv.DictReader(f))
existing={r['file'] for r in rows}
new=[]
for vi,voice in enumerate(VOICES):
 for i,phrase in enumerate(PHRASES):
  for variant,(speed,pitch) in enumerate(((135,49),(160,60))):
   file=f'negative/confusable_v2_{i:02d}_{vi:02d}_{variant:02d}.wav'
   if file in existing: continue
   audio=render(phrase,voice,speed,pitch)
   audio=np.concatenate([np.zeros(5000,dtype=np.float32),audio,np.zeros(7000,dtype=np.float32)])
   write_one(OUT/file,audio)
   new.append({'file':file,'label':'negative','source_group':f'confusable_{i:02d}','type':'synthetic_espeak_confusable_v2'})
with (OUT/'manifest.csv').open('a',encoding='utf-8',newline='') as f:
 w=csv.DictWriter(f,fieldnames=['file','label','source_group','type'])
 w.writerows(new)
print('Added',len(new),'negative train-only phrase variants; originals unchanged')
