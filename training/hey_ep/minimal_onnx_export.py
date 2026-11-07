"""Small ONNX protobuf exporter for the fixed EP wake word DNN.

Encodes ONNX v1 ModelProto, GraphProto and TensorProto using protobuf wire format.
Restricted intentionally to Reshape, MatMul, Add, Relu and Sigmoid.
The exported graph MUST be validated via native ONNX Runtime.
"""
from pathlib import Path
import numpy as np

def v(n):
    n=int(n)
    if n<0:n=(1<<64)+n
    b=bytearray()
    while n>127:
        b.append((n&127)|128);n>>=7
    b.append(n)
    return bytes(b)

def field(n,wire,payload):return v((n<<3)|wire)+payload

def i(n,x):return field(n,0,v(x))

def b(n,x):return field(n,2,v(len(x))+x)

def s(n,x):return b(n,x.encode('utf-8'))

def msg(n,*parts):return b(n,b''.join(parts))

def tensor(name,value):
    arr=np.asarray(value)
    dtype=7 if arr.dtype==np.int64 else 1
    if dtype==1:arr=arr.astype('<f4')
    else:arr=arr.astype('<i8')
    return b''.join([*(i(1,x) for x in arr.shape),i(2,dtype),s(8,name),b(9,arr.tobytes())])

def value_info(name,shape):
    dimlist=b''.join(msg(1,i(1,x)) for x in shape)
    type_tensor=i(1,1)+msg(2,dimlist)
    return s(1,name)+msg(2,msg(1,type_tensor))

def node(op,inputs,outputs):
    return b''.join([*(s(1,x) for x in inputs),*(s(2,x) for x in outputs),s(4,op)])

def export(model,destination):
    from torch.nn import Linear
    layers=[a for a in model if isinstance(a,Linear)]
    assert len(layers)==3 and [l.in_features for l in layers]==[1536,64,32]
    assert [l.out_features for l in layers]==[64,32,1]
    nodes=[node('Reshape',['x.1','flat_shape'],['flat'])]
    weights=[('flat_shape',np.array([1,1536],dtype=np.int64))]
    prev='flat'
    for j,l in enumerate(layers):
        w=l.weight.detach().numpy().T.copy()
        bias=l.bias.detach().numpy().copy()
        weights += [(f'W{j}',w),(f'B{j}',bias)]
        name=f'z{j}'
        nodes.append(node('MatMul',[prev,f'W{j}'],[name]))
        name2=f'zb{j}'
        nodes.append(node('Add',[name,f'B{j}'],[name2]))
        if j!=len(layers)-1:
            name3=f'relu{j}'
            nodes.append(node('Relu',[name2],[name3]));prev=name3
        else:
            nodes.append(node('Sigmoid',[name2],['score']))
    graph=b''.join([*(msg(1,n) for n in nodes),s(2,'ep_hey_ep_16x96'),
                    *(msg(5,tensor(n,x)) for n,x in weights),
                    msg(11,value_info('x.1',[1,16,96])),msg(12,value_info('score',[1,1]))])
    # ir_version 7 (ONNX 1.8+), opset ai.onnx version 13
    modeldata=i(1,7)+s(2,'EP Voice Launcher V0.4.3')+msg(7,graph)+msg(8,i(2,13))
    destination=Path(destination)
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_bytes(modeldata)
    return destination

if __name__=='__main__':
    import torch
    from ort_native import Session
    m=torch.nn.Sequential(torch.nn.Flatten(),torch.nn.Linear(1536,64),torch.nn.ReLU(),
        torch.nn.Linear(64,32),torch.nn.ReLU(),torch.nn.Linear(32,1),torch.nn.Sigmoid())
    outfile=Path('/tmp/ep_dummy_test.onnx')
    export(m,outfile)
    ses=Session(outfile)
    x=np.random.default_rng(42).normal(size=(1,16,96)).astype(np.float32)
    onnx_out=ses.run(x)
    with torch.no_grad(): ref=m(torch.from_numpy(x)).numpy()
    print('NATIVE ONNX TEST:',ses.input_name,ses.output_name,onnx_out.shape,'error',np.max(abs(onnx_out-ref)))
    assert onnx_out.shape==(1,1) and np.max(abs(onnx_out-ref))<1e-5
