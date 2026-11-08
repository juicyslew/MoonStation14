#!/usr/bin/env python3
"""Extract runtime-used SS14 RSI states into native vertically animated PNGs."""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/moonstation14'
RSI = ASSETS / 'textures/ss14/structures'
OUT = ASSETS / 'textures/block/atmos_machine'
PROV = ASSETS / 'power-reactor-provenance/teg/atmos-derived-manifest.json'
REV = 'c9df5ef5d675b0d1d226828bddf6b78c28502d91'
SPECS = {
 'piping/atmospherics/pump': ['pumpPressure','pumpPressureOn','pumpVolume','pumpVolumeOn','pumpVolumeBlocked','pumpManualValve','pumpManualValveOn'],
 'piping/atmospherics/pump_alt1': ['pumpPressure','pumpPressureOn','pumpVolume','pumpVolumeOn','pumpVolumeBlocked','pumpManualValve','pumpManualValveOn'],
 'piping/atmospherics/pump_alt2': ['pumpPressure','pumpPressureOn','pumpVolume','pumpVolumeOn','pumpVolumeBlocked','pumpManualValve','pumpManualValveOn'],
 'piping/atmospherics/pipe': ['pipeUnaryConnectors','pipeBinaryConnectors','pipeTrinaryConnectors','pipeStraight'],
 'piping/atmospherics/gasmixer': ['gasMixer','gasMixerOn'],
 'piping/atmospherics/gasfilter': ['gasFilter','gasFilterOn'],
 'piping/atmospherics/thermomachine': ['heaterOff','heaterOn','freezerOff','freezerOn','pipe'],
 'piping/atmospherics/thermomachine_alt1': ['pipe'],
 'piping/atmospherics/thermomachine_alt2': ['pipe'],
 'piping/atmospherics/heatexchanger': ['heStraight'],
 'power/generation/teg': ['teg','teg_mid'] + [f'teg-op{i}' for i in range(1,12)] + [f'circ-{i}' for i in range(3)] + [f'circ-{i}-light' for i in range(3)],
}
DIRS4 = ['south','north','east','west']

def sha(path):
 h=hashlib.sha256(); h.update(path.read_bytes()); return h.hexdigest()

def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--check',action='store_true'); args=ap.parse_args()
 manifest=[]
 for family, wanted in SPECS.items():
  src=RSI/(family+'.rsi'); meta=json.loads((src/'meta.json').read_text(encoding='utf-8')); size=meta['size']; w,h=size['x'],size['y']
  states={s['name']:s for s in meta['states']}
  for name in wanted:
   st=states[name]; nd=st.get('directions',1); delays=st.get('delays',[[0.1]] if nd==1 else [[0.1] for _ in range(nd)])
   if len(delays)!=nd: raise ValueError(f'{src}: direction delay count mismatch {name}')
   image_path=src/(name+'.png')
   with Image.open(image_path) as im:
    im=im.convert('RGBA'); frames=sum(len(d) for d in delays)
    if im.width%w or im.height%h or im.width//w*(im.height//h)<frames: raise ValueError(f'{image_path}: image grid cannot contain RSI delays ({frames} frames)')
    cols=im.width//w
    k=0
    for di, ddelays in enumerate(delays):
     direction=DIRS4[di] if nd==4 else ('south' if nd==1 else f'dir{di}')
     extracted=[]
     for fi in range(len(ddelays)):
      x=(k%cols)*w; y=(k//cols)*h; frame=im.crop((x,y,x+w,y+h)); extracted.append(frame); k+=1
     rel=Path(family)/f'{name}_{direction}.png'; dest=OUT/rel; dest.parent.mkdir(parents=True,exist_ok=True)
     strip=Image.new('RGBA',(w,h*len(extracted)))
     for i,frame in enumerate(extracted): strip.paste(frame,(0,i*h))
     if args.check:
      with Image.open(dest) as check:
       check=check.convert('RGBA')
       assert check.size==strip.size and check.tobytes()==strip.tobytes(), f'frame pixel mismatch: {dest}'
     else:
      strip.save(dest,optimize=False)
      if len(ddelays)>1:
       ticks=[round(t*20) for t in ddelays]
       if any(abs(t/20-v)>1e-7 for t,v in zip(ticks,ddelays)): raise ValueError(f'non-integral Minecraft tick delay {src}:{name}')
       (dest.with_suffix('.png.mcmeta')).write_text(json.dumps({'animation':{'frametime':ticks[0] if len(set(ticks))==1 else 1,'frames':list(range(len(ticks))) if len(set(ticks))==1 else [{'index':i,'time':t} for i,t in enumerate(ticks)]}},separators=(',',':'))+'\n',encoding='utf-8')
     manifest.append({'source':f'{src.relative_to(ASSETS).as_posix()}/{name}.png','sourceSha256':sha(image_path),'state':name,'directionIndex':di,'direction':direction,'output':f'{(Path("textures/block/atmos_machine")/rel).as_posix()}','outputSha256':sha(dest),'frames':len(ddelays),'frameSize':[w,h],'delaysSeconds':ddelays})
 if args.check:
  prior=json.loads(PROV.read_text(encoding='utf-8'))
  assert manifest==prior['assets'], 'provenance or generated output differs; regenerate converter outputs/manifest'
 else:
  PROV.write_text(json.dumps({'sourceRepository':'space-station-14','sourceRevision':REV,'conversion':'RSI row-major source image frames, ordered direction-major by RSI metadata; exact frame pixels cropped without resampling; vertical strips for animation','directions4':DIRS4,'assets':manifest},indent=2)+'\n',encoding='utf-8')
 print(f'{len(manifest)} state-direction textures validated')

if __name__=='__main__': main()
