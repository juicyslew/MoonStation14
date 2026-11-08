"""Read-only RSI frame extraction and nearest-neighbour reference sheets (RSI v1).

No image is treated as a UV map. State definitions, native delays and attribution
are retained. Directions are stored direction-major, frames row-major in PNGs.
"""
import hashlib,json,math,shutil
from pathlib import Path
from PIL import Image,ImageDraw

DIRECTIONS={1:['none'],4:['south','north','east','west'],8:['south','north','east','west','southeast','southwest','northeast','northwest']}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def metadata(root):
    m=json.loads((Path(root)/'meta.json').read_text());assert m['version']==1
    return m
def frames(root,state):
    root=Path(root);m=metadata(root);s=next(s for s in m['states'] if s['name']==state)
    count=s.get('directions',1);names=DIRECTIONS[count];w,h=m['size']['x'],m['size']['y']
    delays=s.get('delays',[[None] for _ in names]);assert len(delays)==count
    im=Image.open(root/(state+'.png')).convert('RGBA');cols=im.width//w;index=0;result=[]
    assert im.width%w==0 and im.height%h==0
    for direction,times in zip(names,delays):
        for i,delay in enumerate(times):
            x=index%cols*w;y=index//cols*h;assert y+h<=im.height
            result.append({'state':state,'direction':direction,'frame':i,'seconds':delay,'image':im.crop((x,y,x+w,y+h))});index+=1
    return result
def contact_sheets(root,out,states=None,scale=4):
    root=Path(root);out=Path(out);out.mkdir(parents=True,exist_ok=True);m=metadata(root)
    names=states or [s['name'] for s in m['states']];all_=[f for name in names for f in frames(root,name)];pages=[]
    w,h=m['size']['x']*scale,m['size']['y']*scale;cw=max(220,w+8);ch=h+47
    for page in range(math.ceil(len(all_)/40)):
        sheet=Image.new('RGB',(cw*5,ch*8),'#1c2531');d=ImageDraw.Draw(sheet)
        for i,f in enumerate(all_[page*40:(page+1)*40]):
            x=i%5*cw;y=i//5*ch;d.text((x+4,y+2),f['state'],fill='white')
            d.text((x+4,y+16),f"{f['direction']} f{f['frame']} / {f['seconds'] if f['seconds'] is not None else 'static'}s",fill='#c2d3e2')
            im=f['image'].resize((w,h),Image.Resampling.NEAREST);sheet.paste(im,(x+4,y+35),im)
        path=out/f'source_{page+1:02}.png';sheet.save(path);pages.append(path)
    return pages,all_
def preserve(root,destination,source_path,revision):
    root=Path(root);dst=Path(destination);dst.mkdir(parents=True,exist_ok=True)
    for p in root.iterdir():
        if p.is_file():
            target=dst/p.name
            if target.exists():assert sha(target)==sha(p),'Edited preserved reference: '+str(target)
            else:shutil.copyfile(p,target)
    m=metadata(root)
    return {'resource':source_path,'revision':revision,'license':m.get('license'),'copyright':m.get('copyright'),
        'rsi_specification':'v1; direction-major frames, row-major sheet, South/North/East/West order',
        'size':m['size'],'states':m['states'],'hashes':{p.name:sha(p) for p in dst.iterdir() if p.is_file()}}
