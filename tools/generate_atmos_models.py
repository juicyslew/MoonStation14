#!/usr/bin/env python3
"""Generate native block/item model bindings for resolved atmos-machine profiles."""
import argparse, json
from functools import lru_cache
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/moonstation14'
DATA=ROOT/'src/main/resources/data/moonstation14/moonstation14/atmos_machine'
BLOCKSTATES=ASSETS/'blockstates'; MODELS=ASSETS/'models/block/atmos_machine'; ITEMS=ASSETS/'models/item'
DIRECTIONS=('south','north','east','west')
Y_ROTATION={'north':0,'east':90,'south':180,'west':270}
SPECS={
 'teg_center': {'profile':'teg_center','states':{0:'unpowered', **{i:f'powered_l{i-1:02}' for i in range(1,13)}},'layers':False,'r5':True},
 'teg_circulator': {'profile':'teg_circulator','states':{26:'still_unlit',27:'still_lit',28:'slow_lit',29:'fast_lit'},'layers':False,'r5':True},
 'gas_pressure_pump': {'profile':'pressure_pump','states':{13:[('pumpPressureOn',False)],14:[('pumpPressure',False)]},'layers':True,'family':'pump'},
 'gas_volume_pump': {'profile':'volume_pump','states':{15:[('pumpVolume',False)],16:[('pumpVolumeBlocked',False)],17:[('pumpVolumeOn',False)]},'layers':True,'family':'pump'},
 'gas_valve': {'profile':'manual_valve','states':{22:[('pumpManualValveOn',False)],23:[('pumpManualValve',False)]},'layers':True,'family':'pump'},
 'gas_mixer': {'profile':'mixer','states':{18:[('gasMixerOn',False)],19:[('gasMixer',False)]},'layers':True,'family':'gasmixer'},
 'gas_filter': {'profile':'filter','states':{20:[('gasFilterOn',False)],21:[('gasFilter',False)]},'layers':True,'family':'gasfilter'},
 'gas_thermo_heater': {'profile':'heater','states':{24:[('heaterOn',False)],25:[('heaterOff',False)]},'layers':True,'family':'thermomachine'},
 'gas_thermo_freezer': {'profile':'freezer','states':{24:[('freezerOn',False)],25:[('freezerOff',False)]},'layers':True,'family':'thermomachine'},
 'passive_heat_exchanger': {'profile':'passive_heat_exchanger','states':{0:[('heStraight',False)]},'layers':False,'family':'heatexchanger'},
}
FALLBACK={'teg_center':0,'teg_circulator':26,'gas_pressure_pump':14,'gas_volume_pump':15,'gas_valve':23,'gas_mixer':19,'gas_filter':21,'gas_thermo_heater':25,'gas_thermo_freezer':25,'passive_heat_exchanger':0}

@lru_cache(maxsize=None)
def texture_for(family,state,direction):
 source=ASSETS/'textures/ss14/structures'/f'{family}.rsi/meta.json'
 metadata=json.loads(source.read_text(encoding='utf-8'))
 state_meta=next((s for s in metadata['states'] if s['name']==state),None)
 if state_meta is None: raise ValueError(f'{family}: missing source RSI state {state}')
 selected_direction=direction if state_meta.get('directions',1)==4 else 'south'
 return f'moonstation14:block/atmos_machine/{family}/{state}_{selected_direction}'

def source_for(spec,state):
 if 'family' not in spec: return 'power/generation/teg'
 return {'pump':'piping/atmospherics/pump','gasmixer':'piping/atmospherics/gasmixer','gasfilter':'piping/atmospherics/gasfilter','thermomachine':'piping/atmospherics/thermomachine','heatexchanger':'piping/atmospherics/heatexchanger'}[spec['family']]

def make_model(key, layers):
 family,states,direction=key
 layers=list(layers)
 textures={'casing':'minecraft:block/gray_concrete','surface':texture_for(family,states[0],direction),'particle':texture_for(family,states[0],direction)}
 for i,(state,fullbright) in enumerate(layers): textures[f'layer{i}']=texture_for(family,state,direction)
 elements=[{'from':[0,0,0],'to':[16,12,16],'faces':{f:{'texture':'#casing'} for f in ('north','south','east','west','up','down')}}]
 # Native sprite artwork is preserved on the top and the facing side panel. No rotation is
 # applied: each direction binds the matching SS14 RSI direction texture directly.
 elements.append({'from':[0,12.02,0],'to':[16,12.021,16],'faces':{'up':{'texture':'#surface','tintindex':-1}}})
 face=direction
 if face=='north': frm=[0,0,-.01]; to=[16,12,0]
 elif face=='south': frm=[0,0,16]; to=[16,12,16.01]
 elif face=='east': frm=[16,0,0]; to=[16.01,12,16]
 else: frm=[-.01,0,0]; to=[0,12,16]
 elements.append({'from':frm,'to':to,'faces':{face:{'texture':'#surface'}}})
 for i,(state,fullbright) in enumerate(layers):
  for index,face in enumerate(('up',face)):
   # Slightly offset each transparent composited layer to avoid z-fighting.
   if face=='up': frm=[0,12.03+i*.01,0]; to=[16,12.031+i*.01,16]
   elif face=='north': frm=[0,0,-.03-i*.01]; to=[16,12,.0-i*.01]
   elif face=='south': frm=[0,0,16+.03+i*.01]; to=[16,12,16.04+i*.01]
   elif face=='east': frm=[16+.03+i*.01,0,0]; to=[16.04+i*.01,12,16]
   else: frm=[-.03-i*.01,0,0]; to=[-.0-i*.01,12,16]
   fd={'texture':f'#layer{i}'}
   if fullbright: fd['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
   elements.append({'from':frm,'to':to,'faces':{face:fd}})
 return {'textures':textures,'elements':elements,'ambientocclusion':True,'render_type':'minecraft:cutout'}

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');ap.add_argument('--profiles',help='comma-separated profile ids to generate/check (default: all)');args=ap.parse_args()
 selected=set(SPECS)
 if args.profiles:
   selected={name.strip() for name in args.profiles.split(',') if name.strip()}
   unknown=selected-set(SPECS)
   if not selected or unknown: ap.error(f'unknown or empty profile selection: {sorted(unknown)}')
 models={}; expected_profiles={}; expected={}
 for block,spec in SPECS.items():
  if block not in selected: continue
  models={}
  # Bind against resolved-profile source identity (including parent inheritance) instead of
  # introducing a second visual registry. The machine-specific `visual` is explicit today.
  profile=json.loads((DATA/f'{block}.json').read_text(encoding='utf-8'))
  assert profile['kind']==spec['profile'] and profile['visual']['state']
  expected_state={'teg_center':'teg','teg_circulator':'circ-0','pressure_pump':'pumpPressure','volume_pump':'pumpVolume','manual_valve':'pumpManualValve','mixer':'gasMixer','filter':'gasFilter','heater':'heater','freezer':'freezer','passive_heat_exchanger':'heStraight'}[spec['profile']]
  assert profile['visual']['state']==expected_state, f'{block}: visual profile identity changed; review resource binding'
  expected_sprite={'teg_center':'Structures/Power/Generation/teg.rsi','teg_circulator':'Structures/Power/Generation/teg.rsi','pressure_pump':'Structures/Piping/Atmospherics/pump.rsi','volume_pump':'Structures/Piping/Atmospherics/pump.rsi','manual_valve':'Structures/Piping/Atmospherics/pump.rsi','mixer':'Structures/Piping/Atmospherics/gasmixer.rsi','filter':'Structures/Piping/Atmospherics/gasfilter.rsi','heater':'Structures/Piping/Atmospherics/thermomachine.rsi','freezer':'Structures/Piping/Atmospherics/thermomachine.rsi','passive_heat_exchanger':'Structures/Piping/Atmospherics/heatexchanger.rsi'}[spec['profile']]
  assert profile['visual']['sprite']==expected_sprite, f'{block}: visual sprite identity changed; review resource binding'
  expected_profiles[block]=profile['visual']
  if spec.get('r5'):
   base='power_collection/generation_teg' if block=='teg_center' else 'power_collection/generation_teg__tegcirculator'
   fallback=FALLBACK[block]
   variants={}
   for display in range(30):
    state=spec['states'].get(display, spec['states'][fallback])
    model=f'moonstation14:block/{base}/{state}'
    for facing in DIRECTIONS:
     for pipe_layer in range(3):
       variants[f'facing={facing},display={display},pipe_layer={pipe_layer}']={'model':model,'y':Y_ROTATION[facing]}
    if args.check:
     assert (ASSETS/'models/block'/base/f'{state}.json').is_file(), f'missing R5 model {base}/{state}'
   blockstate={'variants':variants}
   itemmodel={'parent':f'moonstation14:block/{base}/{spec["states"][fallback]}' }
   bs=BLOCKSTATES/f'{block}.json'; item=ITEMS/f'{block}.json'
   if args.check:
    assert json.loads(bs.read_text(encoding='utf-8'))==blockstate, f'blockstate mismatch {block}'
    assert json.loads(item.read_text(encoding='utf-8'))==itemmodel, f'item model mismatch {block}'
   else:
    bs.write_text(json.dumps(blockstate,indent=2)+'\n',encoding='utf-8')
    item.write_text(json.dumps(itemmodel,indent=2)+'\n',encoding='utf-8')
   expected[block]=variants
   continue
  family=spec.get('family','power/generation/teg')
  variants={}
  for display in range(30):
   selected=spec['states'].get(display, spec['states'].get(FALLBACK[block]))
   for pipe_layer in range(3):
    layer=pipe_layer if spec['layers'] else 0
    if family=='pump': art_family='piping/atmospherics/pump'+(f'_alt{layer}' if layer else '')
    elif family=='thermomachine': art_family='piping/atmospherics/thermomachine'+(f'_alt{layer}' if layer else '')
    elif family=='power/generation/teg': art_family=family
    else: art_family='piping/atmospherics/'+family
    # Multi-layer assemblies use the same-family source paths; connector sheets for pump
    # layers are separate upstream appearance overlays, not baked into the pump art.
    composite=[]
    for state,fullbright in selected:
     chosen_family=art_family if family=='pump' else ('piping/atmospherics/thermomachine' if family=='thermomachine' and state!='pipe' else art_family)
     composite.append((chosen_family,state,fullbright))
    # Preserve upstream composed Sprite layers: binary/unary pump port connectors,
    # trinary mixer/filter connectors, thermostat pipe strips and exchanger pipe.
    if family=='pump':
     connector_name={'gas_pressure_pump':'pipeBinaryConnectors','gas_volume_pump':'pipeBinaryConnectors','gas_valve':'pipeUnaryConnectors'}[block]
     composite.append(('piping/atmospherics/pipe',connector_name,False))
    elif family in ('gasmixer','gasfilter'):
     composite.append(('piping/atmospherics/pipe','pipeTrinaryConnectors',False))
    elif family=='thermomachine':
     composite.append((art_family,'pipe',False))
    elif family=='heatexchanger':
     composite.append(('piping/atmospherics/pipe','pipeStraight',False))
    for facing in DIRECTIONS:
     artlayers=[(s,fb) for _,s,fb in composite]
     key=(art_family,tuple(artlayers),facing)
     # Keep profile's imported sprite family at its canonical output path.
     def model_factory():
      # Build a specialized model where each layer can come from its own family.
      elems=make_model((composite[0][0],[artlayers[0][0]],facing),[])
      # Add overlay layers, including pump connector from the shared pipe RSI.
      for j,(fam,state,fb) in enumerate(composite[1:],1):
       elems['textures'][f'overlay{j}']=texture_for(fam,state,facing)
       face={'south':'south','north':'north','east':'east','west':'west'}[facing]
       a={'up':([0,12.05+j*.01,0],[16,12.051+j*.01,16]),'north':([0,0,-.05-j*.01],[16,12,-.04-j*.01]),'south':([0,0,16+.05+j*.01],[16,12,16+.06+j*.01]),'east':([16+.05+j*.01,0,0],[16+.06+j*.01,12,16]),'west':([-.05-j*.01,0,0],[-.04-j*.01,12,16])}
       # Face-facing panel and top overlay are separate planes for both normal viewpoints.
       for plane,(lo,hi) in a.items():
        data={'texture':f'#overlay{j}'}
        if fb: data['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
        elems['elements'].append({'from':lo,'to':hi,'faces':{plane:data}})
      return elems
     model_name='_'.join(s for _,s,_ in composite)+'_'+str(layer)+'_'+facing
     model_path=MODELS/block/f'{model_name}.json'
     model=f'moonstation14:block/atmos_machine/{block}/{model_name}'
     variants[f'facing={facing},display={display},pipe_layer={pipe_layer}']={'model':model}
     models[model_path]=model_factory()
  expected[block]=variants
  blockstate={'variants':variants}
  bs=BLOCKSTATES/f'{block}.json'
  item=ITEMS/f'{block}.json'
  item_parent=blockstate['variants'][f'facing=north,display={FALLBACK[block]},pipe_layer=0']['model']
  itemmodel={'parent':item_parent}
  if args.check:
   assert json.loads(bs.read_text(encoding='utf-8'))==blockstate, f'blockstate mismatch {block}'
   assert json.loads(item.read_text(encoding='utf-8'))==itemmodel, f'item model mismatch {block}'
   parent_file=MODELS/Path(item_parent.split('/atmos_machine/',1)[1]).with_suffix('.json')
   assert parent_file.is_file(), f'missing item parent model {item_parent}'
   for key,value in models.items():
    assert key.is_file() and json.loads(key.read_text(encoding='utf-8'))==value, f'model mismatch {key}'
    for texture in value['textures'].values():
     if texture.startswith('moonstation14:block/'):
      rel=texture.split('moonstation14:block/',1)[1]
      assert (ASSETS/'textures/block'/f'{rel}.png').is_file(), f'missing model texture {texture}'
  else:
   bs.parent.mkdir(parents=True,exist_ok=True);bs.write_text(json.dumps(blockstate,indent=2)+'\n',encoding='utf-8')
   item.parent.mkdir(parents=True,exist_ok=True);item.write_text(json.dumps(itemmodel,indent=2)+'\n',encoding='utf-8')
   for path,value in models.items():
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
 print(f'{len(selected)} resolved profiles bound; {len(selected)*30*3*4} native blockstate selectors covered (DISPLAY 0..29 x pipe_layer 0..2 x four directions); R5 selectors use imported composed OBJ snapshots')

if __name__=='__main__':main()
