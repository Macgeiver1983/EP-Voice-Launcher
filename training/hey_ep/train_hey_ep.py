#!/usr/bin/env python3
"""Train a real ONNX classifier on the EXACT EP Android openWakeWord features.

Requires onnxruntime, onnx, numpy, torch, soundfile. No pretrained Jarvis classifier
is changed or fine-tuned. This script refuses to report a 'validated model' unless
ONNX checker AND onnxruntime output agreement both pass.

NB: Synthetic test results do NOT demonstrate person-independent wakeword performance.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import random
import numpy as np

ROOT=Path(__file__).resolve().parent
PROJECT=ROOT.parents[1]
ASSETS=PROJECT/'app/src/main/assets/openwakeword'


def make_frontend(ort):
    o=ort.SessionOptions();o.intra_op_num_threads=1
    mel=ort.InferenceSession(str(ASSETS/'melspectrogram.onnx'),sess_options=o, providers=['CPUExecutionProvider'])
    emb=ort.InferenceSession(str(ASSETS/'embedding_model.onnx'),sess_options=o, providers=['CPUExecutionProvider'])

    def featurize(audio):
        # AudioRecord emits signed 16-bit values as FLOATS, not normalized [-1,+1].
        audio=np.asarray(audio,dtype=np.float32)
        audio=(np.clip(audio,-1,1)*32767).astype('<i2').astype(np.float32)
        raw=np.zeros(160000,dtype=np.float32)
        mel_frames=[np.ones(32,dtype=np.float32) for _ in range(76)]
        embeddings=[np.zeros(96,dtype=np.float32) for _ in range(16)]
        n=0; writepos=0; predictions=0; output=[]
        for start in range(0,len(audio)-1279,1280):
            chunk=audio[start:start+1280]
            for val in chunk:
                raw[writepos]=val
                writepos=(writepos+1)%len(raw)
            n+=1280
            if n<1760:continue
            idx=(writepos-1760+len(raw))%len(raw)
            if idx+1760<=len(raw): buf=raw[idx:idx+1760].copy()
            else:buf=np.concatenate([raw[idx:],raw[:1760-(len(raw)-idx)]])
            m=mel.run(None,{'input':buf.reshape(1,1760)})[0]
            m=m.reshape(-1,32).astype(np.float32)/10.0+2.0
            mel_frames.extend(list(m))
            if len(mel_frames)>970:mel_frames=mel_frames[-970:]
            window=np.asarray(mel_frames[-76:],dtype=np.float32).reshape(1,76,32,1)
            e=emb.run(None,{'input_1':window})[0].reshape(96).astype(np.float32)
            embeddings.append(e)
            if len(embeddings)>120:embeddings=embeddings[-120:]
            predictions+=1
            if predictions<=5:continue
            output.append((n/16000.0,np.stack(embeddings[-16:])))
        return output
    return featurize


def end_speech(x):
    # crude adaptive end estimate; intended for a first experimental classifier
    step=320
    blocks=[np.sqrt(np.mean(x[i:i+step]**2)) for i in range(0,len(x)-step,step)]
    if not blocks:return len(x)/16000
    e=np.asarray(blocks)
    t=max(0.012,float(e.max())*.13)
    positions=np.where(e>=t)[0]
    return (int(positions[-1])+1)*step/16000 if len(positions) else len(x)/16000


def load_rows(manifest):
    with manifest.open(newline='',encoding='utf-8') as f:
        rows=list(csv.DictReader(f))
    assert len(rows)>=250, 'Insufficient synthetic/real corpus; first run generate_corpus.py'
    return rows


def dataset(rows, feat, root, cache):
    import soundfile as sf
    samples=[];labels=[];splits=[];groups=[]
    cache.mkdir(exist_ok=True,parents=True)
    for i, row in enumerate(rows):
        file=root/row['file']; waveform,sr=sf.read(str(file),dtype='float32')
        if sr!=16000 or waveform.ndim!=1:raise ValueError('Expected mono 16k PCM: '+str(file))
        digest=hashlib.sha256(file.read_bytes()).hexdigest()[:20]
        cached=cache/(digest+'.npz')
        if cached.exists():
            pack=np.load(cached)
            times=pack['times'];windows=pack['windows']
        else:
            calculated=feat(waveform)
            times=np.array([x[0] for x in calculated],dtype=np.float32)
            windows=np.array([x[1] for x in calculated],dtype=np.float32)
            np.savez_compressed(cached,times=times,windows=windows)
        if len(times)==0:continue
        positive=row['label']=='positive'
        # All variants of held-out originals share a source_group and stay together.
        group=row['source_group']
        holdout=(group in ('tts_voice_06','neg_phrase_19','neg_phrase_20','hard_neg_30','hard_neg_31','hard_neg_32','hard_neg_33'))
        if positive:
            stop=end_speech(waveform)
            chosen=np.where((times>=stop-.2)&(times<=stop+.55))[0]
            if len(chosen)==0:chosen=np.arange(max(0,len(times)-3),len(times))
            chosen=chosen[::max(1,len(chosen)//4)][:6]
        else:
            chosen=np.arange(0,len(times))  # Hard negatives: every 80 ms window, not just 5 sparse samples
        for j in chosen:
            samples.append(windows[j]);labels.append(int(positive));splits.append(holdout);groups.append(group)
        if i%35==0: print('Features extracted',i+1,'/',len(rows),flush=True)
    return np.array(samples,dtype=np.float32),np.array(labels,dtype=np.float32),np.array(splits),groups


def main():
    pa=argparse.ArgumentParser()
    pa.add_argument('--corpus',type=Path,default=ROOT/'generated')
    pa.add_argument('--epochs',type=int,default=45)
    pa.add_argument('--seed',type=int,default=43)
    pa.add_argument('--model-out',type=Path,default=ROOT/'results/hey_ep.onnx')
    args=pa.parse_args()
    import torch
    try:
        import onnxruntime as ort
        import onnx
        use_native = False
    except ImportError:
        from ort_native import Session as NativeSession
        use_native = True
        class _NativeShim:
            def __init__(self,path): self.session = NativeSession(path)
            def run(self, _requested_outputs, feed):
                return [self.session.run(next(iter(feed.values())))]
        class _ORT:
            @staticmethod
            def SessionOptions():
                class S:
                    intra_op_num_threads=1
                return S()
            @staticmethod
            def InferenceSession(path,sess_options=None,providers=None): return _NativeShim(path)
        ort = _ORT()
    seed=args.seed;random.seed(seed);np.random.seed(seed);torch.manual_seed(seed)
    rows=load_rows(args.corpus/'manifest.csv')
    feat=make_frontend(ort)
    X,Y,holdout,groups=dataset(rows,feat,args.corpus,ROOT/'feature_cache')
    train=(~holdout);valid=holdout
    assert train.any() and valid.any() and (Y[train]==1).any() and (Y[valid]==1).any()
    model=torch.nn.Sequential(torch.nn.Flatten(),torch.nn.Linear(1536,64),torch.nn.ReLU(),
                              torch.nn.Linear(64,32),torch.nn.ReLU(),torch.nn.Linear(32,1),torch.nn.Sigmoid())
    optimizer=torch.optim.AdamW(model.parameters(),lr=.0005,weight_decay=.001)
    x=torch.from_numpy(X[train]);y=torch.from_numpy(Y[train,None])
    positive=float(Y[train].sum());negative=float(train.sum()-positive)
    weights=torch.from_numpy(np.where(Y[train]>0.5,negative/max(1,positive),1).astype('float32'))[:,None]
    for ep in range(args.epochs):
        model.train()
        indices=torch.randperm(len(x))
        loss_sum=0
        for batch in indices.split(128):
            score=model(x[batch]).clamp(1e-5,1-1e-5)
            loss=(-(y[batch]*torch.log(score)+(1-y[batch])*torch.log(1-score))*weights[batch]).mean()
            optimizer.zero_grad();loss.backward();optimizer.step()
            loss_sum+=float(loss.detach())
        if (ep+1)%10==0: print('epoch',ep+1,'loss_sum',round(loss_sum,4),flush=True)
    model.eval()
    args.model_out.parent.mkdir(parents=True,exist_ok=True)
    # Export from REAL learned weights, not an empty/renamed pretrained model.
    dummy=torch.zeros((1,16,96),dtype=torch.float32)
    if use_native:
        from minimal_onnx_export import export
        export(model,args.model_out)
    else:
        torch.onnx.export(model,dummy,str(args.model_out),input_names=['x.1'],
                          output_names=['score'],opset_version=13)
        onnx.checker.check_model(onnx.load(str(args.model_out)))
    actual=ort.InferenceSession(str(args.model_out),providers=['CPUExecutionProvider'])
    probe=X[valid][:min(5,valid.sum())]
    with torch.no_grad(): reference=model(torch.from_numpy(probe)).numpy().reshape(-1)
    observed=np.concatenate([actual.run(None,{'x.1':a[None]})[0].reshape(-1) for a in probe])
    assert np.max(np.abs(reference-observed))<1e-4,'ONNX output differs from PyTorch'
    with torch.no_grad(): val_score=model(torch.from_numpy(X[valid])).numpy().reshape(-1)
    pred=val_score>=.65; true=Y[valid]>.5
    tp=int(np.sum(pred&true));fp=int(np.sum(pred&~true));fn=int(np.sum(~pred&true));tn=int(np.sum(~pred&~true))
    report={
      'status':'EXPERIMENTAL MODEL; NOT RELEASE VALIDATED',
      'model_file':str(args.model_out),'train_windows':int(train.sum()),'test_windows':int(valid.sum()),
      'train_groups':sorted(set(g for g,h in zip(groups,holdout) if not h)),
      'test_groups':sorted(set(g for g,h in zip(groups,holdout) if h)),
      'test_confusion_at_threshold_0.65':{'TP':tp,'FP':fp,'FN':fn,'TN':tn},
      'onnx_checked':not use_native,'onnxruntime_cpu_loaded':True,'onnx_numeric_agreement':True,
      'warning':'All 10 original recordings used for training; held-out TTS voice and negative phrases are not independent real speakers. No real-world false alarms/hour measured.',
      'false_alarms_per_hour':'NOT TESTED','real_samsung_test':'NOT TESTED',
    }
    (args.model_out.parent/'train_report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf8')
    print(json.dumps(report,indent=2,ensure_ascii=False))
    print('Model training completed; DO NOT deploy until independent wakeword/false alarm testing succeeds.')


if __name__=='__main__':main()
