"""Independent fresh reproduction, provenance freeze and own-family publication."""
import argparse, hashlib, json, re, shutil, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'tooling'))
from ss14_model_tools import json_write
from prepare import PACKAGE
from build import build
from review import review
from validate import validate, digest
WORK=ROOT/'work/Structures/Botany/BasicBotany'

def inventory(root):return {str(p.relative_to(root)).replace('\\','/'):digest(p) for p in sorted(root.rglob('*')) if p.is_file()}

def publish(out):
    before=WORK/'retrieval_before';before.mkdir(parents=True,exist_ok=False)
    files=['asset_locations.json','README.md','MODEL_HANDOFF.md'];raw={n:(ROOT/n).read_bytes() for n in files}
    for n,v in raw.items():(before/n).write_bytes(v)
    data=json.loads(raw['asset_locations.json']);assert 'basic_botany' not in data['families']
    peers=json.loads(json.dumps(data['families']))
    root=str(out.relative_to(ROOT)).replace('\\','/')
    entry={'status':'35_native_Java_botany_projects_awaiting_user_review_NOT_installed_or_runtime_integrated',
      'package_root':root,'browse':root+'/index.html','example_master':root+'/authoring/poppy/harvest.bbmodel',
      'authoring_directory':root+'/authoring','open_verify_dimensions_limits':root+'/README.md',
      'export_manifest':root+'/export/manifest.json','plant_state_registry':root+'/export/plant-models.json',
      'binding_contract':root+'/export/binding_contract.json','source_manifest':root+'/source/source_manifest.json',
      'projects':35,'plant_logical_states':34,'runtime_models':35,'target':'Java1.21.1/NeoForge21.1.224 native block-model JSON + PNG; no new dependencies',
      'scope':'one independent tray plus wheat/tomato/potato/poppy/aloe all declared stages + harvest/dead; empty tray only; no machine/tool/item/hitbox commission',
      'transfer_rule':'Retrieve saved complete models/textures/state manifest/source/license. Root [8,5,8] already in model, overlay at block origin, no second translation/scaling. Authoritative selection; no simulation.',
      'read_only_verifier':'tooling/Structures/Botany/BasicBotany/validate.py'}
    data['families']['basic_botany']=entry
    # Check immediate inputs still match the captured current peer version.
    for n,v in raw.items():assert (ROOT/n).read_bytes()==v,'Concurrent root edit; preserve capture and stop, do not replace peers.'
    json_write(ROOT/'asset_locations.json',data)
    note=f'**Basic botany — new asset delivery:** [35 editable models / original-first source gallery]({root}/index.html) · [empty / harvest / dead]({root}/previews/status_review.png) · [dimensions/open/verify/transfer]({root}/README.md). One independent cyan/pale tray and five distinct plant constructions; all 34 declared plant states, native Java JSON/PNG/stable-key manifest. Tray inside 14×5×14-unit envelope; plants root at (8,5,8), already block-local. Candidate art awaits USER review, NOT Blockbench/Minecraft/production-renderer acceptance or installation. No tools/machines/items/collision/gameplay commissioned. Retrieve saved complete dependencies/source/licenses, never regenerate-to-retrieve; unrelated saved art unchanged.\n\n'
    for n in ['README.md','MODEL_HANDOFF.md']:(ROOT/n).write_bytes(note.encode('utf-8')+raw[n])
    current=json.loads((ROOT/'asset_locations.json').read_text());assert current['families']['basic_botany']==entry
    assert {k:v for k,v in current['families'].items() if k!='basic_botany'}==peers
    for n in ['README.md','MODEL_HANDOFF.md']:assert (ROOT/n).read_bytes()==note.encode('utf-8')+raw[n]
    json_write(WORK/'retrieval_publication.json',{'result':'PASS','preserved_peer_entries':len(peers),'prior_root_prose':'entire prior README and MODEL_HANDOFF bytes retained',
       'changes':'one new own family registry entry; own prepended retrieval paragraph only','own_entry':entry})

def finish(out):
    if (out/'delivery_hashes.json').exists():raise SystemExit('Refusing finalized delivery replacement; use read-only validate.py.')
    before=inventory(out);r=validate(out)
    independent=WORK/'independent_reproduction'
    if independent.exists():raise SystemExit('Preserve prior independent output; do not overwrite. Choose new explicitly authorized audit destination.')
    independent.mkdir(parents=True);shutil.copytree(out/'source',independent/'source');shutil.copyfile(out/'model_spec.json',independent/'model_spec.json')
    build(independent);review(independent);validate(independent)
    generated={k:h for k,h in inventory(independent).items() if not k.startswith('source/')}
    for rel,h in generated.items():assert digest(out/rel)==h,rel
    # Nothing in the saved package has changed during independent rendering.
    for rel,h in before.items():assert digest(out/rel)==h,rel
    r['independent_file_equalities']=len(generated);r['source_dependency_reproduction']='copied saved hashed references, no live-reference mutation';json_write(out/'validation.json',r)
    links=[]
    for p in out.glob('*.html'):
        for rel in re.findall(r'(?:href|src)="([^"]+)"',p.read_text(encoding='utf-8')):
            if not rel.startswith(('http:','https:','#')):assert (out/rel).is_file(),rel;links.append(rel)
    json_write(out/'delivery_validation.json',{'result':'PASS','independent_generated_file_equalities':len(generated),'gallery_links':len(links),
       'reference_repositories':'read-only; no installation','validation_scope':'asset structure + actual compatible CPU render only, not user/app/game acceptance',
       'freeze':'delivery_hashes.json protects every saved artifact except itself; verify read-only, future art revisions separate candidates'})
    json_write(out/'delivery_hashes.json',inventory(out))
    frozen=inventory(out);refusals=[]
    for script in ['prepare.py','build.py','review.py','finish.py']:
        cmd=[sys.executable,str(Path(__file__).parent/script),'--output',str(out)]
        res=subprocess.run(cmd,capture_output=True,text=True)
        assert res.returncode!=0 and ('Refusing' in res.stdout+res.stderr or 'frozen' in res.stdout+res.stderr)
        assert inventory(out)==frozen
        refusals.append({'script':script,'returncode':res.returncode,'output':res.stdout+res.stderr,'saved_files_unchanged':True})
    json_write(WORK/'writer_refusals.json',{'result':'PASS','saved_files':len(frozen),'checks':refusals})
    validate(out);assert inventory(out)==frozen
    publish(out)
    json_write(WORK/'final_saved_delivery_audit.json',{'result':'PASS','frozen_saved_files':len(frozen),'protected_entries':len(frozen)-1,
       'actual_writer_refusals':len(refusals),'independent_generated_file_equalities':len(generated),'art_changed_at_finish':False,
       'BB_app':'NOT RUN','Minecraft':'NOT RUN','production_renderer':'NOT INTEGRATED','user_acceptance':False})
    print(f'PASS: {len(generated)} independent generated-file equalities; {len(frozen)} saved files frozen; four actual writer refusals, publication preserves current peers.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=PACKAGE);a=p.parse_args();finish(a.output)
