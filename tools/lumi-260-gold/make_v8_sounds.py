#!/usr/bin/env python3
"""Create 5 original *nonverbal* LUMI sound effects as mono 16k Ogg Opus.
Run on a GitHub ubuntu-latest runner (ffmpeg installed), not on ESP32.
No voice impersonation: the Raquel TTS service is not exposed by XiaoZhi.
Deterministic, embedded at compile time; no WiFi request, assets or NVS changes.
"""
from pathlib import Path
import subprocess, wave, math, hashlib, sys
SR=16000
SOUNDS=(('tap_ping',0.36,'ping'),('tap_plip',0.40,'plip'),('tap_bubble',0.49,'bubble'),
        ('shake_wobble',0.72,'wobble'),('shake_tumble',0.68,'tumble'))

def sample(t,dur,kind):
    u=max(0.0,min(1.0,t/dur))
    if kind=='ping':
        f=640+390*math.exp(-t*19)
        env=math.exp(-t*6.5)
    elif kind=='plip':
        f=900-530*(1-math.exp(-t*18))
        env=math.exp(-t*7.5)
    elif kind=='bubble':
        f=275+740*(1-math.exp(-t*9))
        env=math.exp(-t*5.5)
    elif kind=='wobble':
        f=520-260*u+55*math.sin(2*math.pi*8*t)
        env=math.exp(-t*2.6)
    else:
        f=580-330*u+90*math.sin(2*math.pi*6*t)
        env=math.exp(-t*3.0)
    fade=min(1.0,t/.018,max(0.0,(dur-t)/.055))
    return f,env*fade

def render(name,dur,kind,output):
    n=int(dur*SR)
    phase=0.0
    pcm=bytearray()
    for i in range(n):
        t=i/SR
        f,env=sample(t,dur,kind)
        phase+=2*math.pi*f/SR
        harmonic=0.08 if kind in ('ping','plip','bubble') else 0.12
        trem=(.80+.16*math.sin(2*math.pi*8*t)) if kind in ('wobble','tumble') else 1.0
        x=env*trem*(math.sin(phase)*.51+math.sin(phase*1.7)*harmonic)
        v=max(-32767,min(32767,int(x*26500)))
        pcm.extend(v.to_bytes(2,'little',signed=True))
    wav=output/(name+'.wav')
    ogg=output/(name+'.ogg')
    with wave.open(str(wav),'wb') as fd:
        fd.setnchannels(1);fd.setsampwidth(2);fd.setframerate(SR)
        fd.writeframes(pcm)
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),
                    '-c:a','libopus','-ar','16000','-ac','1','-b:a','24k',str(ogg)],check=True)
    data=ogg.read_bytes()
    assert 700 < len(data) < 5200 and data[:4]==b'OggS' and b'OpusHead' in data
    print(name,len(data),hashlib.sha256(data).hexdigest())
    return data

def main():
    target=Path(sys.argv[1])
    tmp=target.parent/'lumi_sfx_build'
    tmp.mkdir(parents=True,exist_ok=True)
    out=['#pragma once','#include <cstddef>',
         '// LUMI original nonverbal SFX; same XiaoZhi Ogg Opus playback path.',
         'namespace LumiFx {']
    for name,dur,kind in SOUNDS:
        data=render(name,dur,kind,tmp)
        alias=''.join(word.capitalize() for word in name.split('_'))
        out.append('static const unsigned char k'+alias+'Ogg[] = {')
        for off in range(0,len(data),16):
            out.append('    '+','.join('0x%02x'%b for b in data[off:off+16])+',')
        out.append('};')
        out.append('static constexpr size_t k'+alias+'OggLen = sizeof(k'+alias+'Ogg);')
    out.append('}')
    target.write_text('\n'.join(out)+'\n',encoding='utf-8')
    print('Header',target,'bytes',target.stat().st_size)

if __name__=='__main__':
    if len(sys.argv)!=2: raise SystemExit('Usage: make_v8_sounds.py /path/to/lumi_fx_sound.h')
    main()
