"""Bounded botanical constructors. Canonical centre X/Z; +Y up; root Y5.

Source projection interpreted by the artist, not pixel-voxelized or extruded.
All element rotations are native single-axis Java angles.
"""
import math
import numpy as np
from static_model_package import matrix

class Shape:
    def __init__(self): self.parts=[]
    def box(self,name,lo,hi,material='leaf',rotation=None,pivot=None,faces=None):
        p={'name':name,'from':[round(float(x),5) for x in lo],'to':[round(float(x),5) for x in hi],
           'group':'plant','surface':material,'rotation':rotation or [0,0,0],'origin':pivot or [0,5,0]}
        if faces:p['faces']=faces
        self.parts.append(p);return p
    def stem(self,name,x,z,y,height,width=.5,material='stem'):
        return self.box(name,[x-width/2,y,z-width/2],[x+width/2,y+height,z+width/2],material)
    def twig(self,name,x,z,y,length,angle,axis='z',width=.4,material='stem'):
        r=[0,0,0];r['xyz'.index(axis)]=angle
        return self.box(name,[x-width/2,y,z-width/2],[x+width/2,y+length,z+width/2],material,r,[x,y,z])
    def oval(self,name,x,y,z,w,h,d,material):
        # Three joined stepped volumes, not separate floating slivers.
        self.box(name+'_middle',[x-w/2,y+h*.18,z-d/2],[x+w/2,y+h*.82,z+d/2],material)
        self.box(name+'_bottom',[x-w*.32,y,z-d*.34],[x+w*.32,y+h*.18,z+d*.34],material)
        self.box(name+'_cap',[x-w*.32,y+h*.82,z-d*.34],[x+w*.32,y+h,z+d*.34],material)
    def leaf(self,name,x,y,z,length,width,side=1,axis='z',droop=False,material='leaf',thickness=.35,tilt=22.5):
        # Flattened connected blade; central body with smaller tip; vein painted.
        # Local +X blade; z=+/-22.5 tilts leaves from branch points.
        if axis=='z':
            lo=[x if side>0 else x-length,y,z-width/2];hi=[x+length if side>0 else x,y+thickness,z+width/2]
            angle=tilt*side*(-1 if droop else 1);r=[0,0,angle]
            self.box(name+'_body',lo,hi,material,r,[x,y,z],{'up':material+'_top','down':material+'_under'})
            end=x+side*length
            self.box(name+'_tip',[min(end,end+side*.65),y+thickness*.2,z-width*.28],[max(end,end+side*.65),y+thickness*.8,z+width*.28],material,r,[x,y,z])
        else:
            lo=[x-width/2,y,z if side>0 else z-length];hi=[x+width/2,y+thickness,z+length if side>0 else z]
            r=[-tilt*side*(-1 if droop else 1),0,0]
            self.box(name+'_body',lo,hi,material,r,[x,y,z],{'up':material+'_top','down':material+'_under'})
            end=z+side*length
            self.box(name+'_tip',[x-width*.28,y+thickness*.2,min(end,end+side*.65)],[x+width*.28,y+thickness*.8,max(end,end+side*.65)],material,r,[x,y,z])

def tray():
    s=Shape()
    for x in [-5.5,5.5]:
        for z in [-5.5,5.5]:
            s.box(f'foot_{x}_{z}',[x-.6,0,z-.6],[x+.6,1.5,z+.6],'pale')
    s.box('dark_undertray',[-6.1,1.0,-6.1],[6.1,3.6,6.1],'trough',faces={'north':'trough_front'})
    for z in [-6.4,5.95]:
        s.box(f'lower_rail_{z}',[-6.8,.65,z],[6.8,1.15,z+.45],'pale')
    for x in [-6.7,6.2]:
        s.box(f'lower_side_rail_{x}',[x,1.05,-5.95],[x+.5,1.6,5.95],'pale')
    s.box('upper_housing',[-6.65,3.0,-6.65],[6.65,4.4,6.65],'pale_side')
    # No opaque housing roof on top of the reservoir: four disjoint top rims.
    for z in [-6.7,5.8]:
        s.box(f'rim_north_south_{z}',[-5.8,4.4,z],[5.8,5,z+.9],'pale')
    for x in [-6.7,5.8]:
        s.box(f'rim_east_west_{x}',[x,4.4,-5.8],[x+.9,5,5.8],'pale')
    for x in [-6.4,5.8]:
        for z in [-6.4,5.8]:
            s.box(f'rim_step_corner_{x}_{z}',[x,4.4,z],[x+.6,5,z+.6],'pale')
    for x in [-7,6.65]:
        s.box(f'rounded_end_step_{x}',[x,3.55,-5.4],[x+.35,4.4,5.4],'pale_side')
    s.box('cyan_reservoir_surface',[-5.8,4.4,-5.8],[5.8,5.0,5.8],'water',faces={'up':'water_top'})
    s.box('front_cyan_band',[-5.8,3.55,-6.76],[5.8,4.4,-6.65],'water')
    s.box('rear_cyan_band',[-5.8,3.55,6.65],[5.8,4.4,6.76],'water')
    for p in s.parts:p['group']='tray'
    return s.parts

def wheat(cfg,state):
    s=Shape();dead=state=='dead';harvest=state=='harvest'
    stage=6 if harvest else (3 if dead else int(state.split('-')[1]))
    height=cfg['dead_height'] if dead else cfg['harvest_height'] if harvest else cfg['heights_above_root'][stage-1]
    count=6 if dead else cfg['stalk_counts'][stage-1]
    sites=[(-4.7,-2),(-2.1,1.5),(0,-1.5),(2.3,2),(4.5,-1.0),(-3.7,3.0),(3.6,-3.3),(-.8,3.8),(1.4,-3.8),(-4.5,-3.4),(4.4,3.6)]
    for i,(x,z) in enumerate(sites[:count]):
        h=height*(.78+[.1,.18,.02,.16,0,.22,.08][i%7]); top=5+h
        s.stem(f'stalk_{i}',x,z,5,max(.7,h-(6.2 if stage>=4 and not dead else 0)),.65 if stage>=4 else .45,'stem')
        if dead:
            y=5+h*.62
            s.twig(f'broken_stalk_{i}',x,z,y,h*.36,45 if i%2 else -45,width=.35)
            s.leaf(f'dry_blade_{i}',x,y,z,1.3,.6,1 if i%2 else -1,droop=True)
        elif stage<4:
            s.leaf(f'sprout_blade_{i}',x,5+h*.4,z,.75+stage*.35,.5,1 if i%2 else -1)
            if stage>=2:s.leaf(f'second_blade_{i}',x,5+h*.7,z,.7,.45,-1 if i%2 else 1,axis='x')
        else:
            ear_y=top-6.8;w=1.8 if stage>=5 else 1.35
            s.box(f'ear_core_{i}',[x-.38,ear_y,z-.34],[x+.38,top-.35,z+.34],'grain')
            for j in range(5):
                yy=ear_y+j*1.08
                # A small number of meaningful rows; ribs within rows are texture.
                ww=w*(.70 if j==4 else .85 if j==3 else 1)
                s.box(f'grain_row_{i}_{j}',[x-ww/2,yy,z-.58],[x+ww/2,yy+.85,z+.58],'grain')
            s.stem(f'awn_{i}',x,z,top-.35,.9,.22,'grain')
            s.leaf(f'blade_{i}',x,5+h*.32,z,1.8,.8,-1 if x>0 else 1)
            s.twig(f'upright_cereal_blade_{i}',x+.25,z,5.10,h*.55,22.5 if x>0 else -22.5,width=.48,material='leaf')
    return s.parts

def tomato(cfg,state):
    s=Shape();dead=state=='dead';harvest=state=='harvest';stage=6 if harvest or dead else int(state.split('-')[1])
    h=cfg['dead_height'] if dead else cfg['harvest_height'] if harvest else cfg['heights_above_root'][stage-1]
    if stage>=cfg['support_first_stage']:
        sh=h+.5 if dead else cfg['support_height']
        for x in [-4.4,4.4]:s.stem(f'timber_stake_{x}',x,1.2,5,sh,.5,'wood')
    s.stem('main_vine',0,0,5,h*.94,.65)
    tiers=1 if stage==1 else 2 if stage<=3 else 3 if stage==4 or dead else 4
    for t in range(tiers):
        yy=5+h*(.22+t*.19);length=min(3.5,.85+stage*.45)
        for side in [-1,1]:
            r=s.twig(f'branch_{t}_{side}',0,0,yy,length,side*-45,width=.42)
            tipx=side*length/math.sqrt(2);tipy=yy+length/math.sqrt(2)
            s.leaf(f'leaf_{t}_{side}',tipx,tipy,-.1,min(2.0,.65+stage*.22),1.1 if stage<4 else 2.1,side,droop=dead or stage>=4,thickness=.6 if stage<4 else 1.0)
            if stage>=3 and not dead:
                s.leaf(f'back_leaf_{t}_{side}',tipx,tipy+.2,.1,2.0,1.6,1,axis='x',thickness=.9)
                s.leaf(f'front_leaf_{t}_{side}',tipx,tipy-.2,-.1,2.1,1.7,-1,axis='x',droop=t==0,thickness=.9)
            if dead:s.twig(f'bare_droop_{t}_{side}',tipx,-.15,tipy,length*.62,side*45,width=.32,material='leaf')
    if stage>=2 and not dead:
        s.leaf('crown_leaf',0,5+h*.92,0,2.2,2.0,1,axis='x',thickness=1.0)
        s.leaf('front_crown_leaf',0,5+h*.88,0,2.0,1.8,-1,axis='x',thickness=.8)
    if harvest or (stage>=5 and not dead):
        fruit_sites=[(-3.3,7.2,-2.5),(3.2,8.2,-2.4),(-2.0,13,-2.6),(2.5,15.2,-1.9),(0,7.0,-2.9),(3.4,11.8,2.6)]
        for i,(x,yy,z) in enumerate(fruit_sites[:6 if harvest else stage-3]):
            if not harvest: yy=min(yy,5+h*.65)
            size=2.55 if harvest else 1.45
            s.stem(f'fruit_pedicel_{i}',x,z,yy+size*.85,1.25,.3)
            attach=yy+size*.85+1.1
            s.box(f'fruit_branch_x_{i}',[min(0,x)-.17,attach,-.17],[max(0,x)+.17,attach+.34,.17],'stem')
            s.box(f'fruit_branch_z_{i}',[x-.17,attach,min(0,z)-.17],[x+.17,attach+.34,max(0,z)+.17],'stem')
            s.oval(f'ripe_tomato_{i}' if harvest else f'green_tomato_{i}',x,yy,z,size,size*.85,size*.82,'fruit' if harvest else 'green_fruit')
            s.box(f'calyx_{i}',[x-.7,yy+size*.85,z-.22],[x+.7,yy+size*.85+.25,z+.22],'stem')
    return s.parts

def potato(cfg,state):
    s=Shape();dead=state=='dead';harvest=state=='harvest';stage=4 if harvest or dead else int(state.split('-')[1])
    h=cfg['dead_height'] if dead else cfg['harvest_height'] if harvest else cfg['heights_above_root'][stage-1]
    s.stem('central_stem',0,0,5,h*.9,.55)
    tiers=2 if dead or stage<3 else 3
    for t in range(tiers):
        yy=5+h*(.4+t*.24);l=min(4.1,1.1+stage*.8)*(1-t*.08)*(.75 if dead else 1)
        for side in [-1,1]:
            s.leaf(f'paired_leaflet_{t}_{side}',0,yy,.15*(t%2),l,1.55 if stage==1 else 2.6,side,droop=True,thickness=.65 if stage==1 else 1.05,tilt=22.5 if dead else 0)
            if (stage>=3 or harvest) and not dead:
                s.leaf(f'radial_leaflet_{t}_{side}',0,yy+.1,0,l*.9,2.2,side,axis='x',droop=True,thickness=.85,tilt=0)
    if not dead:
        for i,(x,z) in enumerate([(-1.2,-1),(.9,-1.3),(2.7,-1.0),(-3,-1.8),(1.8,1.8),(-1.7,1.9)][:6 if harvest else 1+stage//2]):
            size=2.25 if harvest else 1.55
            s.oval(f'exposed_tuber_{i}',x,5,z,size,.9 if harvest else .65,size*.72,'tuber')
    return s.parts

def poppy(cfg,state):
    s=Shape();dead=state=='dead';harvest=state=='harvest'
    stage=3 if harvest or dead else int(state.split('-')[1])
    h=cfg['dead_height'] if dead else cfg['harvest_height'] if harvest else cfg['heights_above_root'][stage-1]
    if dead:
        s.stem('dry_lower_stem',0,0,5,6,.45)
        s.twig('bent_dry_middle',0,0,11,5,-45,width=.45)
        s.stem('dry_upper_stem',3.54,0,14.54,4,.4)
        s.twig('spent_head_neck',3.54,0,18.54,2.0,45,width=.35)
        s.oval('grey_seed_remnant',2.1,19.9,0,1.5,1.2,1,'bud')
        for i,(xx,yy) in enumerate([(0,8),(1.3,12),(3.54,16)]):
            s.leaf(f'dry_grey_leaf_{i}',xx,yy,-.1,1.8,.55,-1 if i%2 else 1,droop=True)
    else:
        budh=2.7+stage*.85
        stemh=h-3 if harvest else max(1,h-budh)
        s.stem('single_flower_stem',0,0,5,stemh,.55)
        s.leaf('left_scalloped_leaf',0,5+stemh*.36,0,2.4,1.2,-1)
        if stage>=2:
            s.leaf('right_scalloped_leaf',0,5+stemh*.54,0,1.65,1.0,1)
            s.twig('low_asymmetric_shoot',-.15,.1,5.15,2.0,45,width=.4)
        if not harvest:
            w=1.6+stage*.65;d=1.5+stage*.4
            for i,(a,b,fac) in enumerate([(0,.13,.4),(.13,.28,.78),(.28,.72,1),(.72,.87,.78),(.87,1,.4)]):
                s.box(f'closed_green_bud_tier_{i}',[-w*fac/2,5+stemh+budh*a,-d*fac/2],[w*fac/2,5+stemh+budh*b,d*fac/2],'bud')
        else:
            # Upright shallow red cup; continuous rear calyx, open dark north eye.
            y=5+stemh+1
            s.oval('green_calyx_back',0,y-2,.7,2.8,3.2,1.0,'stem')
            petals=[(-2,0,2.2,3.2),(2,0,2.2,3.2),(0,2.0,3.2,2.2),(0,-2.0,3.2,2.2),(-1.5,1.5,2.1,2.1),(1.5,1.5,2.1,2.1),(-1.5,-1.5,2.1,2.1),(1.5,-1.5,2.1,2.1)]
            for i,(x,dy,w,hh) in enumerate(petals):
                s.box(f'red_petal_{i}',[x-w/2,y+dy-hh/2,-.8],[x+w/2,y+dy+hh/2,.55],'petal')
            s.box('dark_poppy_eye',[-.95,y-.95,-.82],[.95,y+.95,-.62],'eye')
    return s.parts

def aloe(cfg,state):
    s=Shape();dead=state=='dead';harvest=state=='harvest';stage=5 if harvest or dead else int(state.split('-')[1])
    h=cfg['dead_height'] if dead else cfg['harvest_height'] if harvest else cfg['heights_above_root'][stage-1]
    count=8 if dead else cfg['leaf_counts'][stage-1]
    s.oval('fleshy_root_crown',0,5,0,1.3+stage*.3,.8,1.3+stage*.3,'leaf')
    arrangements=[('z',0,0,0),('x',22.5,.6,.1),('z',45,-.6,.1),('z',-45,.6,-.2),('x',45,-.3,-.6),('x',-45,.3,.6),('z',22.5,-.7,-.7),('z',-22.5,.7,.7),('x',22.5,-.8,.3),('x',-22.5,.8,-.3)]
    for i,(axis,ang,x,z) in enumerate(arrangements[:count]):
        if dead:
            ang=45 if i%2 else -45;r=[0,0,0];r['xyz'.index(axis)]=ang
            length=2.4+(i%3)*.25;width=1.8;lift=width/2/math.sqrt(2)
            pivot=[x,5+lift,z]
            s.box(f'dry_spear_base_{i}',[x-width/2,5+lift,z-.3],[x+width/2,5+lift+length,z+.3],'spear',r,pivot)
            tip=np.array(pivot)+matrix(r)@np.array([0,length,0])
            side=(-1 if ang>0 else 1) if axis=='z' else (1 if ang>0 else -1)
            s.leaf(f'collapsed_dry_tip_{i}',*tip[[0,1,2]],2.1,1.1,side,axis=axis,droop=True,material='spear',thickness=.55)
            continue
        length=h*(1 if i<2 else .90 if abs(ang)==22.5 else .80)
        width=min(3.0,.60+stage*.46)*(1 if i>=2 else .82)
        thick=.60 if dead else 1.10
        r=[0,0,0];r['xyz'.index(axis)]=ang;pivot=[x,5,z]
        for j,(a,b,fac) in enumerate([(0,.52,1),(.52,.84,.62),(.84,1,.27)]):
            p=s.box(f'spear_{i}_taper_{j}',[x-width*fac/2,5+length*a,z-thick*fac/2],[x+width*fac/2,5+length*b,z+thick*fac/2],'spear',r,pivot,
                    {'north':'spear_front','south':'spear_front','up':'spear_tip'})
        # Raise this blade just enough that all rotated corners remain above soil.
        ps=s.parts[-3:];R=matrix(r)
        low=min(((np.array([[xx,yy,zz] for xx in [p['from'][0],p['to'][0]] for yy in [p['from'][1],p['to'][1]] for zz in [p['from'][2],p['to'][2]]])-pivot)@R.T+np.array(pivot))[:,1].min() for p in ps)
        lift=max(0,5-low)
        for p in ps:
            p['from'][1]=round(p['from'][1]+lift,5);p['to'][1]=round(p['to'][1]+lift,5);p['origin']=[x,round(5+lift,5),z]
    return s.parts

CONSTRUCTORS={'wheat':wheat,'tomato':tomato,'potato':potato,'poppy':poppy,'aloe':aloe}
