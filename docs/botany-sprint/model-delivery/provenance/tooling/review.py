"""Actual saved-file CPU reviews. No Blockbench GUI or game launches."""
import argparse, copy, html, json, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'tooling'))
from static_model_package import read_bb, read_java, quads
from ss14_model_tools import render, json_write
from prepare import PACKAGE, COUNTS

VIEWS=[('front',0,0),('back',180,0),('east',90,0),('west',270,0),('top',0,90),('bottom',0,-90),('front_3q',35,22),('back_3q',215,22)]
DUMMY={'groups':[{'name':'door','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0}}
FLAGS={'open':False,'locked':False,'welded':False,'label':'none'}

def load(out,key,state,kind='bb'):
    if kind=='bb':
        b,parts,atlas=read_bb(out/f'authoring/{key}/{state}.bbmodel')
        for p in parts:
            for coord in ['from','to','origin']:p[coord]=(np.array(p[coord])-[8,0,8]).tolist()
    else:
        atlas=Image.open(out/f'textures/{key}/{state}.png').convert('RGBA')
        rel='hydroponics_tray.json' if key=='hydroponics_tray' else f'plants/{key}/{state}.json'
        b,parts=read_java(out/f'export/resourcepack/assets/moonstation14/models/block/botany/{rel}',atlas)
    return parts,atlas

def scene(out,key,state,kind='bb'):
    parts,tex=load(out,'hydroponics_tray','default',kind)
    if key=='hydroponics_tray':return quads(parts),tex
    plant,atlas=load(out,key,state,kind);W,H=tex.size
    both=Image.new('RGBA',(W+atlas.width,max(H,atlas.height)));both.paste(tex,(0,0));both.paste(atlas,(W,0))
    ps=quads(parts);qs=quads(plant)
    for p in qs:
        for q in p['quads']:q['uv']=(np.array(q['uv'])+[W,0]).tolist()
    return ps+qs,both

def image(parts,atlas,yaw=35,elevation=22,reference=False,size=(256,288),lighting=True):
    return render(DUMMY,parts,atlas,FLAGS,yaw,elevation,size=size,reference=reference,lighting=lighting)

def review(out):
    if (out/'delivery_hashes.json').exists():raise SystemExit('Refusing frozen preview replacement; retrieve saved previews.')
    if (out/'previews/models').exists():raise SystemExit('Refusing existing review overwrite; use a new candidate.')
    out.joinpath('previews/models').mkdir(parents=True)
    entries=json.loads((out/'collection_status.json').read_text())['items'];rows={k:[] for k in ['hydroponics_tray']+list(COUNTS)}
    for item in entries:
        key,state=item['key'],item['state'];ps,atlas=scene(out,key,state)
        sheet=Image.new('RGB',(4*256,2*310),(28,37,49));d=ImageDraw.Draw(sheet)
        folder=out/f'previews/models/{key}';folder.mkdir(parents=True,exist_ok=True)
        for i,(label,yaw,elev) in enumerate(VIEWS):
            im=image(ps,atlas,yaw,elev);x=i%4*256;y=i//4*310;sheet.paste(im,(x,y+20));d.text((x+4,y+4),f'{key} / {state} / {label}',fill='white')
            if label in ['front','front_3q']:im.save(folder/f'{state}_{label}.png')
        sheet.save(folder/f'{state}_eightviews.png')
        # Genuine saved-model native-sized readability plus the block-scale guide.
        scale=image(ps,atlas,35,22,True,size=(384,336));scale.save(folder/f'{state}_scale.png')
        src=out/('source/rsi/containers.rsi/hydrotray3.png' if key=='hydroponics_tray' else f'source/rsi/{key}.rsi/{state}.png')
        sim=Image.open(src).convert('RGBA').crop((0,0,32,32)).resize((192,192),Image.Resampling.NEAREST)
        contact=Image.new('RGB',(760,312),(28,37,49));cd=ImageDraw.Draw(contact)
        cd.text((8,4),f'SS14 ORIGINAL / {key} / {state} / static',fill='white');contact.paste(sim,(0,62),sim)
        contact.paste(Image.open(folder/f'{state}_front.png'),(216,24));contact.paste(Image.open(folder/f'{state}_front_3q.png'),(496,24))
        cd.text((220,6),'SAVED MODEL / front',fill='white');cd.text((500,6),'SAVED MODEL / front 3q',fill='white')
        cd.text((8,289),'32 native source pixels enlarged nearest; source projection is stylized, not a world-height blueprint.',fill='#bac5d2')
        contact.save(folder/f'{state}_comparison.png');rows[key].append(contact)
    for key,ims in rows.items():
        sheet=Image.new('RGB',(760,312*len(ims)),(28,37,49))
        for i,im in enumerate(ims):sheet.paste(im,(0,i*312))
        sheet.save(out/f'previews/{key}_source_models.png')
    # Common fixed-camera status sheet, harvest versus dead with empty base.
    sheet=Image.new('RGB',(5*256,3*320),(28,37,49));d=ImageDraw.Draw(sheet)
    for i,key in enumerate(COUNTS):
        for j,state in enumerate(['empty','harvest','dead']):
            ps,atlas=scene(out,'hydroponics_tray','default') if state=='empty' else scene(out,key,state)
            im=image(ps,atlas);sheet.paste(im,(i*256,j*320+24));d.text((i*256+4,j*320+4),f'{key} / {state}',fill='white')
    sheet.save(out/'previews/status_review.png')
    # Whole family ripe overview, front 3q and 16-unit reference block.
    overview=Image.new('RGB',(3*384,2*360),(28,37,49));d=ImageDraw.Draw(overview)
    for i,key in enumerate(['hydroponics_tray']+list(COUNTS)):
        state='default' if i==0 else 'harvest';ps,atlas=scene(out,key,state)
        im=image(ps,atlas,35,22,True,size=(384,336));x=i%3*384;y=i//3*360;overview.paste(im,(x,y+24));d.text((x+4,y+4),f'{key} / {state} | reference = 1 block',fill='white')
    overview.save(out/'previews/overview.png')
    links=[]
    for key in rows:
        items=[x for x in entries if x['key']==key]
        links.append(f'<h2>{key}</h2><a href="previews/{key}_source_models.png">Original-first source/model contact</a>')
        for i in items:
            state=i['state'];path=f'previews/models/{key}/{state}'
            links.append(f'<article><h3>{state} · {i["parts"]} cuboids</h3><img src="{path}_comparison.png"><p><a href="{path}_eightviews.png">Eight views</a> · <a href="{path}_scale.png">One-block scale</a> · <a href="authoring/{key}/{state}.bbmodel">Editable Blockbench</a> · <a href="specifications/{key}/{state}.json">Specification</a></p></article>')
    page='''<!doctype html><meta charset="utf-8"><title>Basic botany — source-directed models</title><style>body{background:#1c2531;color:#e5e9ef;font:16px system-ui;margin:24px}a{color:#8ed5ff}img{max-width:100%;image-rendering:pixelated}article{border-top:1px solid #4a5868;margin:20px 0}h2{margin-top:50px}</style><h1>Basic botany · 35 editable native-Java projects</h1><p>Candidate art: awaiting user review; not Blockbench-app/Minecraft/production-renderer validated or installed. No gameplay/collision supplied. Native RGBs, static state selection, one tray plus zero/one plant.</p><p><a href="README.md">Dimensions / open / rebuild / limitations</a> · <a href="export/plant-models.json">Stable-key state mapping</a> · <a href="export/manifest.json">Complete destination manifest</a> · <a href="previews/status_review.png">Empty / harvest / dead review</a></p><img src="previews/overview.png">'''+''.join(links)
    (out/'index.html').write_text(page,encoding='utf-8')
    json_write(out/'previews/render_method.json',{'renderer':'actual saved .bbmodel cube UV geometry -> static_model_package.quads -> ss14_model_tools CPU z-buffer',
       'texture_sampling':'nearest, no antialiasing, opaque/cutout', 'scale':'16 model units per block; grey cube = one block',
       'native_lighting':'approximate directional CPU shading; not Minecraft baked AO/blocklight/GPU validation','views_per_project':8,
       'animations':'none, source static states; no invented empty clips or growth-animation GIFs',
       'visual_review_status':'images generated; actual image inspection recorded by modeler separately'})
    print(f'Rendered actual saved geometry: {len(entries)} eight-view sheets, state comparisons and common scale/status sheets.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=PACKAGE);a=p.parse_args();review(a.output)
