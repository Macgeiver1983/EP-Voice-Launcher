"""Add acoustically confusing *new* TTS phrases to the existing training corpus.

Do not change any of the 10 original human recordings or previous augmented clips.
"""
from pathlib import Path
import csv
import numpy as np
from generate_corpus import render,write_one

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'generated';MANIFEST=OUT/'manifest.csv'
PHRASES=['Hey E','Hey Pe','E Pe','Hey Epe','Hey B Pe','Hey E Tee','Hey PC',
'Hey AP','Hey E B','Hey E D','Hey E B Pe','Hey PT','Hey E V', 'Hey P E',
'Hey Beta','Hey Elli','Hey Pascal','Hey Evi','Hey Lars', 'Hey Edgar',
'Hey Elke','Hey Eddie','E Pe ist fertig','Das EP System ist hier',
'Wir haben ein Problem','Der Kunde braucht eine Antwort','Das Gerät ist aus',
'Die Produktion läuft heute gut','Morgen müssen wir die Lieferung prüfen',
'Ich benötige das Prüfprotokoll','Hey Siri, mach das Licht an','Hey Google, wie spät ist es?',
'Schalte die Maschine wieder ein','Das ist eine ganz normale Unterhaltung']
with MANIFEST.open(newline='',encoding='utf-8') as f:
    rows=list(csv.DictReader(f))
existing={r['file'] for r in rows}
new=[]
rng=np.random.default_rng(77)
for v,voice in enumerate(('de+f4','de+m4')):
    for j in range(12):
        x=render('Hey E Pe',voice,int(rng.integers(117,173)),int(rng.integers(32,66)))
        x=np.concatenate([np.zeros(int(rng.uniform(.3,.8)*16000),dtype=np.float32),x,np.zeros(int(rng.uniform(.35,.7)*16000),dtype=np.float32)])
        name=f'positive/hard_pos_{v:02d}_{j:02d}.wav'
        if name not in existing:
            write_one(OUT/name,x)
            new.append({'file':name,'label':'positive','source_group':f'hard_tts_voice_{v:02d}','type':'synthetic_espeak_hard'})
    for phrase_id,phrase in enumerate(PHRASES):
        x=render(phrase,voice,133,47)
        x=np.concatenate([np.zeros(5000,dtype=np.float32),x,np.zeros(5000,dtype=np.float32)])
        name=f'negative/hard_neg_{phrase_id:02d}_voice_{v:02d}.wav'
        if name not in existing:
            write_one(OUT/name,x)
            new.append({'file':name,'label':'negative','source_group':f'hard_neg_{phrase_id:02d}','type':'synthetic_espeak_hard'})
with MANIFEST.open('a',newline='',encoding='utf-8') as f:
    writer=csv.DictWriter(f,fieldnames=['file','label','source_group','type'])
    writer.writerows(new)
print('new:',len(new),'total:',len(rows)+len(new),'(all original data retained)')
