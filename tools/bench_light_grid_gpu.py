"""区画索引付き圧縮のGPU試作計測。本番プロトコルは変更しない。
moderngl/numpy環境で実行: python tools/bench_light_grid_gpu.py --output local/compression/gpu.json
"""
import argparse
import json
import random
import statistics
import struct
import time
from pathlib import Path
import moderngl
import numpy as np
from light_grid_codec import encode, decode, cells

VERTEX = '''#version 330
void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}
'''
COMMON = '''#version 330
uniform sampler2D Data;
uniform ivec3 Size;
layout(location=0) out vec4 Color;
int word(int i){ivec3 c=ivec3(round(texelFetch(Data,ivec2(i%512,1+i/512),0).rgb*255.));
return (c.b&240)==160 ? c.r*256+c.g : 0;}
int bits(int p,int n){int a=p/14,b=p%14;int v=word(a)>>b;
if(b+n>14)v|=word(a+1)<<(14-b);return v&((1<<n)-1);}
int byteAt(int p){return bits(p*8,8);}
'''
LEGACY = '''int code(ivec3 v){int i=v.x+Size.x*(v.z+Size.z*v.y);
int b=(i%7)*4,c=23+2*(i/7);
int lo=b<14?word(c):0,hi=b+4>14?word(c+1):0;
return ((lo|(hi<<14))>>b)&15;}
'''
PALETTE = '''int code(ivec3 v){ivec3 count=Size/4,t=v/4,q=v%4;
int tile=t.x+count.x*(t.z+count.z*t.y);
int total=count.x*count.y*count.z;
int p=total*2+bits(tile*16,16),mode=byteAt(p),i=q.x+4*(q.z+4*q.y);
if(mode==0)return byteAt(p+1);
if(mode==3)return bits((p+1)*8+i*4,4);
int n=byteAt(p+1),id=bits((p+2+n)*8+i*mode,mode);
return byteAt(p+2+id);}
'''
MAIN = '''void main(){ivec2 p=ivec2(gl_FragCoord.xy);ivec3 v=ivec3(p.x%Size.x,p.x/Size.x,p.y);
int c=code(v);Color=vec4(pow(1.25,float((c&7)-5))/2.,float(c>>3),1.,float(c)/15.);}
'''


def indexed(values,w,h,d):
    assert w%4==h%4==d%4==0
    packed,modes=encode(values,w,h,d)
    assert decode(packed)==(values,w,h,d)
    at=10; offsets=[]
    for indices in cells(w,h,d):
        offsets.append(at-10);m=packed[at]
        at += 2 if m==0 else 33 if m==3 else 2+packed[at+1]+8*m
    assert at==len(packed) and max(offsets)<65536
    return struct.pack('<'+'H'*len(offsets),*offsets)+packed[10:],modes


def words_from_bytes(data):
    value=int.from_bytes(data,'little')
    return [(value>>(14*i))&16383 for i in range((len(data)*8+13)//14)]


def old_words(values):
    out=[0]*23
    for start in range(0,len(values),7):
        v=sum(int(c)<<(4*i) for i,c in enumerate(values[start:start+7]))
        out.extend((v&16383,v>>14))
    return out


def texture(ctx,words):
    height=max(33,1+(len(words)+511)//512)
    data=np.zeros((height,512,4),dtype='u1');data[:,:,3]=255
    flat=data[1:].reshape(-1,4)
    a=np.asarray(words,dtype='u2');flat[:len(a),0]=a>>8;flat[:len(a),1]=a&255;flat[:len(a),2]=168
    tex=ctx.texture((512,height),4,data.tobytes());tex.filter=(moderngl.NEAREST,moderngl.NEAREST)
    return tex


def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path);p.add_argument('--samples',type=int,default=15);p.add_argument('--batch',type=int,default=64);args=p.parse_args()
    ctx=moderngl.create_context(standalone=True,backend='egl',require=330)
    programs=[ctx.program(vertex_shader=VERTEX,fragment_shader=COMMON+s+MAIN) for s in (LEGACY,PALETTE)]
    vaos=[ctx.vertex_array(pr,[]) for pr in programs]
    cases=[]
    for path in sorted((Path(__file__).resolve().parent.parent/'local/compression').glob('wall-sky-*.bin')):
        payload=path.read_bytes();ws=struct.unpack('>'+str(len(payload)//2)+'H',payload);w,h,d=ws[7:10]
        vals=[((ws[23+2*(i//7)]|(ws[24+2*(i//7)]<<14))>>((i%7)*4))&15 for i in range(w*h*d)]
        cases.append((path.stem,vals,w,h,d))
    rng=random.Random(8351)
    for w in (48,80):
        for n in (1,2,4,16,0):
            vals=[0]*(w*16*w)
            for indices in cells(w,16,w):
                palette=rng.sample(range(16),n if n else rng.choice((1,2,4,16)))
                for i in indices:vals[i]=rng.choice(palette)
            cases.append((f'synthetic-{w}-{n}values',vals,w,16,w))
    report={'gpu':ctx.info['GL_RENDERER'],'version':ctx.info['GL_VERSION'],'samples':args.samples,'draws_per_sample':args.batch,'cases':[]}
    for name,vals,w,h,d in cases:
        start=time.perf_counter();packed,modes=indexed(vals,w,h,d);cpu_ms=(time.perf_counter()-start)*1000
        wordsets=[old_words(vals),words_from_bytes(packed)]
        textures=[texture(ctx,ws) for ws in wordsets]
        out=ctx.texture((w*h,d),4,dtype='f1');fb=ctx.framebuffer([out]);fb.use();ctx.viewport=(0,0,w*h,d)
        images=[]
        for pr,vao,tex in zip(programs,vaos,textures):
            pr['Data'].value=0;pr['Size'].value=(w,h,d);tex.use(0);vao.render(vertices=3)
            images.append(np.frombuffer(out.read(),dtype='u1').reshape(d,w*h,4).copy())
        assert np.array_equal(*images),f'{name}: GPU outputs differ'
        expected=np.asarray(vals,dtype='u1').reshape(h,d,w).transpose(1,0,2).reshape(d,w*h)
        assert np.array_equal(images[1][:,:,3],expected*17),f'{name}: codes differ from CPU'
        for vao,tex in zip(vaos,textures):
            tex.use(0)
            for _ in range(16):vao.render(vertices=3)
        ctx.finish();times=[[],[]]
        for sample in range(args.samples):
            for mode in ([0,1] if sample%2==0 else [1,0]):
                textures[mode].use(0)
                with ctx.query(time=True) as q:
                    for _ in range(args.batch):vaos[mode].render(vertices=3)
                ctx.finish();times[mode].append(q.elapsed/1e6/args.batch)
        row={'name':name,'size':[w,h,d],'modes':modes,'indexed_bytes':len(packed),'legacy_wire_bytes':len(wordsets[0])*2,
             'indexed_wire_bytes':len(wordsets[1])*2,'indexed_fits_existing_near_only':len(wordsets[1])<=16384,
             'encode_and_cpu_verify_ms':round(cpu_ms,2),'output_equal':True}
        for label,ts in zip(('legacy','indexed'),times):
            row[label+'_ms']={'median':round(statistics.median(ts),6),'p10':round(float(np.percentile(ts,10)),6),'p90':round(float(np.percentile(ts,90)),6)}
        report['cases'].append(row);print(json.dumps(row),flush=True)
        fb.release();out.release()
        for tex in textures:tex.release()
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(report['gpu'])


if __name__=='__main__':main()
