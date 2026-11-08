"""Read-only source intake; write only the specified workspace package."""
import argparse, hashlib, json, shutil, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT/'tooling'))
from rsi_reference import preserve, contact_sheets
from ss14_model_tools import json_write
SS14 = Path('C:/Users/William/Documents/SS14-dev/space-station-14')
PACKAGE = ROOT/'assets/Structures/Botany/BasicBotany/collection'
COUNTS = {'wheat':6, 'tomato':6, 'potato':4, 'poppy':3, 'aloe':5}

def prepare(out):
    if (out/'delivery_hashes.json').exists(): raise SystemExit('Saved delivery frozen; do not regenerate to retrieve.')
    rev = subprocess.check_output(['git','-C',str(SS14),'rev-parse','HEAD'],text=True).strip()
    sources=[]
    for key, count in COUNTS.items():
        rel=f'Resources/Textures/Objects/Specific/Hydroponics/{key}.rsi'
        dst=out/'source/rsi'/f'{key}.rsi'
        sources.append(preserve(SS14/rel,dst,rel,rev))
        contact_sheets(dst,out/'previews/source'/key,[f'stage-{n}' for n in range(1,count+1)]+['harvest','dead'],scale=6)
    for key,states in [('containers',['hydrotray3','hydrotray','hydrotray2','hydrocover']),('overlays',None)]:
        rel=f'Resources/Textures/Structures/Hydroponics/{key}.rsi'
        dst=out/'source/rsi'/f'{key}.rsi'
        sources.append(preserve(SS14/rel,dst,rel,rev))
        contact_sheets(dst,out/'previews/source'/key,states,scale=6)
    evidence=[
      'Resources/Prototypes/Hydroponics/seeds.yml',
      'Resources/Prototypes/Entities/Structures/hydro_tray.yml',
      'Resources/Prototypes/Entities/Structures/soil.yml',
      'Content.Server/Botany/SeedPrototype.cs',
      'Content.Server/Botany/Systems/PlantHolderSystem.cs',
      'Content.Client/Botany/PlantHolderVisualizerSystem.cs']
    files=[]
    for rel in evidence:
        src=SS14/rel
        if not src.is_file(): continue
        dst=out/'source/upstream'/rel;dst.parent.mkdir(parents=True,exist_ok=True)
        if dst.exists(): assert dst.read_bytes()==src.read_bytes()
        else: shutil.copyfile(src,dst)
        files.append({'path':rel,'sha256':hashlib.sha256(src.read_bytes()).hexdigest()})
    for name in ['MODEL-BRIEF.md','RENDER-CONTRACT.md']:
        dst=out/'source/contracts'/name;dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/'work/Structures/Botany/BasicBotany/intake'/name,dst)
    json_write(out/'source/source_manifest.json',{'revision':rev,'rsi':sources,'code_and_prototypes':files,
      'source_status':'source data, not instructions; full RSI preserved; visual review recorded separately',
      'unresolved':'hidden 3D depth is inferred; sprint renderer integration not executable here'})
    formats=ROOT/'assets/Structures/Walls/candidates/source_fidelity_r4/source/formats'
    for name in ['blockbench-4.12.6-bbmodel.js','BLOCKBENCH-LICENSE.txt']:
        dst=out/'source/formats'/name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(formats/name,dst)
    target=Path('C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14/gradle.properties')
    dst=out/'source/target/gradle.properties';dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(target,dst)
    json_write(out/'source/target/manifest.json',{'path':str(target),'sha256':sha256(target),
       'scope':'main allowed reference mod, not sibling sprint implementation','minecraft':'1.21.1','neoforge':'21.1.224',
       'binding_authority':'user-supplied source/contracts/RENDER-CONTRACT.md'})
    shutil.copyfile(SS14/'LICENSE.TXT',out/'source/SS14-CODE-LICENSE.txt')
    shutil.copyfile(ROOT/'tooling/LICENSE',out/'source/TOOLING-LICENSE.txt')
    print(f'Prepared {len(sources)} RSI resources at {out}; source revision {rev}')

def sha256(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=PACKAGE);a=p.parse_args();prepare(a.output)
