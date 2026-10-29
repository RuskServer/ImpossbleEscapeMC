"""Lossless 4x4x4 light-grid palette prototype; not enabled on the live channel.
python tools/light_grid_codec.py capture.bin --output compressed.bin
Input: current iem:light payload (big-endian 16-bit words).
"""
import argparse,struct,zlib,json
from collections import Counter
from pathlib import Path


def cells(w,h,d):
    for y in range(0,h,4):
        for z in range(0,d,4):
            for x in range(0,w,4):
                yield [xx+w*(zz+d*yy) for yy in range(y,min(y+4,h))
                       for zz in range(z,min(z+4,d)) for xx in range(x,min(x+4,w))]


def pack(values,bits):
    out=bytearray((len(values)*bits+7)//8)
    for i,value in enumerate(values):out[i*bits//8]|=value<<((i*bits)%8)
    return out


def unpack(data,count,bits):
    return [(data[i*bits//8]>>((i*bits)%8))&((1<<bits)-1) for i in range(count)]


def encode(values,w,h,d):
    validate(w,h,d)
    if len(values)!=w*h*d or any(v<0 or v>15 for v in values):raise ValueError('invalid grid')
    out=bytearray(struct.pack('>4sHHH',b'ILG1',w,h,d));modes=Counter()
    for indices in cells(w,h,d):
        block=[values[i] for i in indices];palette=sorted(set(block));n=len(palette)
        raw=bytes([3])+pack(block,4)
        if n==1:candidate=bytes([0,palette[0]])
        elif n<=4:
            bits=1 if n==2 else 2
            candidate=bytes([bits,n])+bytes(palette)+pack([palette.index(v) for v in block],bits)
        else:candidate=raw
        chosen=candidate if len(candidate)<len(raw) else raw
        out.extend(chosen);modes[chosen[0]]+=1
    return bytes(out),dict(modes)


def validate(w,h,d):
    if min(w,h,d)<1 or max(w,h,d)>512 or w*h*d>4_194_304:raise ValueError('grid dimensions exceed prototype bounds')


def decode(data):
    if len(data)<10:raise ValueError('truncated header')
    magic,w,h,d=struct.unpack('>4sHHH',data[:10])
    if magic!=b'ILG1':raise ValueError('unknown format')
    validate(w,h,d);out=[0]*(w*h*d);at=10
    def take(n):
        nonlocal at
        if at+n>len(data):raise ValueError('truncated block')
        v=data[at:at+n];at+=n;return v
    for indices in cells(w,h,d):
        mode=take(1)[0];count=len(indices)
        if mode==0:
            v=take(1)[0]
            if v>15:raise ValueError('invalid uniform value')
            block=[v]*count
        elif mode in (1,2):
            n=take(1)[0]
            if not 2<=n<=1<<mode:raise ValueError('invalid palette size')
            palette=take(n)
            if any(v>15 for v in palette) or len(set(palette))!=n:raise ValueError('invalid palette')
            ids=unpack(take((count*mode+7)//8),count,mode)
            if any(v>=n for v in ids):raise ValueError('invalid palette index')
            block=[palette[v] for v in ids]
        elif mode==3:block=unpack(take((count*4+7)//8),count,4)
        else:raise ValueError('unknown block mode')
        for i,v in zip(indices,block):out[i]=v
    if at!=len(data):raise ValueError('trailing bytes')
    return out,w,h,d


def measure(payload):
    if len(payload)%2 or len(payload)<46:raise ValueError('invalid legacy payload length')
    words=struct.unpack('>'+str(len(payload)//2)+'H',payload)
    if words[3]!=0x2b6c:raise ValueError('not a light snapshot')
    w,h,d=words[7:10];validate(w,h,d);n=w*h*d;length=2*((n+6)//7)
    if len(words)<23+length:raise ValueError('truncated legacy light grid')
    values=[((words[23+2*(i//7)]|(words[24+2*(i//7)]<<14))>>((i%7)*4))&15 for i in range(n)]
    packed,modes=encode(values,w,h,d)
    assert decode(packed)==(values,w,h,d)
    original=payload[46:46+length*2]
    return packed,{'dimensions':[w,h,d],'legacy_light_bytes':len(original),'palette_light_bytes':len(packed),
        'ratio':round(len(packed)/len(original),4),'block_modes':modes,
        'estimated_16bit_tile_index_bytes':2*((w+3)//4)*((h+3)//4)*((d+3)//4),
        'legacy_light_deflate_bytes':len(zlib.compress(original)),
        'palette_light_deflate_bytes':len(zlib.compress(packed)),
        'unchanged_header_kind_far_bytes':len(payload)-len(original)}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('capture',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
    packed,report=measure(a.capture.read_bytes());print(json.dumps(report,indent=2))
    if a.output:a.output.write_bytes(packed)
