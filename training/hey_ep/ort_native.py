"""Minimal CPython bridge to installed ONNX Runtime C API v21 when Python wheel is unavailable.

Indices match official v1.21.0 onnxruntime_c_api.h `struct OrtApi`.
Only CPU float32 tensors are supported. Use standard `onnxruntime` package in Work if possible.
"""
import ctypes as C
import numpy as np

P=C.c_void_p
lib=C.CDLL('/usr/lib/x86_64-linux-gnu/libonnxruntime.so.1.21')
lib.OrtGetApiBase.restype=P
base=lib.OrtGetApiBase()
get_api=C.CFUNCTYPE(P,C.c_uint32)(P.from_address(base).value)
api_addr=get_api(21)
if not api_addr:raise RuntimeError('Incompatible ONNX Runtime C API')
api=(P*285).from_address(api_addr)

def fn(i,restype,*argtypes):
    return C.CFUNCTYPE(restype,*argtypes)(api[i])

ERROR=fn(2,C.c_char_p,P)
RELEASE_STATUS=fn(93,None,P)

def check(status):
    if status:
        msg=ERROR(status).decode('utf-8','replace')
        RELEASE_STATUS(status)
        raise RuntimeError('ONNX Runtime: '+msg)

CREATE_ENV=fn(3,P,C.c_int,C.c_char_p,C.POINTER(P))
CREATE_OPTIONS=fn(10,P,C.POINTER(P))
SET_THREADS=fn(24,P,P,C.c_int)
CREATE_SESSION=fn(7,P,P,C.c_char_p,P,C.POINTER(P))
GET_ALLOCATOR=fn(78,P,C.POINTER(P))
GET_INPUT_NAME=fn(36,P,P,C.c_size_t,P,C.POINTER(C.c_char_p))
GET_OUTPUT_NAME=fn(37,P,P,C.c_size_t,P,C.POINTER(C.c_char_p))
ALLOC_FREE=fn(76,P,P,P)
CREATE_MEM_INFO=fn(69,P,C.c_int,C.c_int,C.POINTER(P))
CREATE_TENSOR=fn(49,P,P,P,C.c_size_t,C.POINTER(C.c_int64),C.c_size_t,C.c_int,C.POINTER(P))
RUN=fn(9,P,P,P,C.POINTER(C.c_char_p),C.POINTER(P),C.c_size_t,C.POINTER(C.c_char_p),C.c_size_t,C.POINTER(P))
GET_TENSOR_SHAPE=fn(65,P,P,C.POINTER(P))
GET_DIMS_COUNT=fn(61,P,P,C.POINTER(C.c_size_t))
GET_DIMS=fn(62,P,P,C.POINTER(C.c_int64),C.c_size_t)
GET_TENSOR_DATA=fn(51,P,P,C.POINTER(P))
RELEASE_ENV=fn(92,None,P)
RELEASE_MEMORY=fn(94,None,P)
RELEASE_SESSION=fn(95,None,P)
RELEASE_VALUE=fn(96,None,P)
RELEASE_SHAPE=fn(99,None,P)
RELEASE_OPTIONS=fn(100,None,P)

env=P();check(CREATE_ENV(3,b'EPNativeTest',C.byref(env)))
opts=P();check(CREATE_OPTIONS(C.byref(opts)));check(SET_THREADS(opts,1))
mem=P();check(CREATE_MEM_INFO(0,0,C.byref(mem)))
alloc=P();check(GET_ALLOCATOR(C.byref(alloc)))

class Session:
    def __init__(self,path):
        self.ptr=P();check(CREATE_SESSION(env,str(path).encode(),opts,C.byref(self.ptr)))
        self.input_name=self.name(GET_INPUT_NAME)
        self.output_name=self.name(GET_OUTPUT_NAME)
    def name(self,call):
        out=C.c_char_p()
        check(call(self.ptr,0,alloc,C.byref(out)))
        name=out.value.decode()
        check(ALLOC_FREE(alloc,C.cast(out,P)))
        return name
    def run(self,x):
        x=np.ascontiguousarray(x,dtype=np.float32)
        dims=(C.c_int64*len(x.shape))(*x.shape)
        value=P()
        check(CREATE_TENSOR(mem,x.ctypes.data,x.nbytes,dims,len(x.shape),1,C.byref(value)))
        input_names=(C.c_char_p*1)(self.input_name.encode())
        output_names=(C.c_char_p*1)(self.output_name.encode())
        inputs=(P*1)(value)
        outputs=(P*1)()
        try:
            check(RUN(self.ptr,None,input_names,inputs,1,output_names,1,outputs))
            info=P();check(GET_TENSOR_SHAPE(outputs[0],C.byref(info)))
            try:
                n=C.c_size_t();check(GET_DIMS_COUNT(info,C.byref(n)))
                shape=(C.c_int64*n.value)();check(GET_DIMS(info,shape,n.value))
                shape=tuple(shape)
                data=P();check(GET_TENSOR_DATA(outputs[0],C.byref(data)))
                size=int(np.prod(shape))
                result=np.ctypeslib.as_array(C.cast(data,C.POINTER(C.c_float)),shape=(size,)).copy()
                return result.reshape(shape)
            finally:RELEASE_SHAPE(info)
        finally:
            if outputs[0]:RELEASE_VALUE(outputs[0])
            RELEASE_VALUE(value)
    def close(self):
        if self.ptr:RELEASE_SESSION(self.ptr);self.ptr=None

if __name__=='__main__':
    from pathlib import Path
    a=Path(__file__).resolve().parents[1]/'../app/src/main/assets/openwakeword'
    m=Session(a/'melspectrogram.onnx');print('MEL',m.input_name,m.output_name,m.run(np.zeros((1,1760),np.float32)).shape);m.close()
    e=Session(a/'embedding_model.onnx');print('EMB',e.input_name,e.output_name,e.run(np.ones((1,76,32,1),np.float32)).shape);e.close()
    w=Session(a/'hey_jarvis_v0.1.onnx');print('JAR',w.input_name,w.output_name,w.run(np.zeros((1,16,96),np.float32)).shape);w.close()
