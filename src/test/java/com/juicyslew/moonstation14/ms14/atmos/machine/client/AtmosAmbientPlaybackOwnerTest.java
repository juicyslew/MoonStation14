package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosAmbientPlaybackOwnerTest {
    static class Fake implements AtmosAmbientPlaybackOwner.Handle { int stops, updates; float gain; boolean stopped; public void stopHandle(){stops++;stopped=true;} public void updateGain(float g){updates++;gain=g;} public boolean isStoppedHandle(){return stopped;} }
    static class Recorder implements AtmosAmbientPlaybackOwner.Playback { List<Fake> handles=new ArrayList<>(); public AtmosAmbientPlaybackOwner.Handle start(AtmosAmbientPlaybackOwner.Snapshot s,float g){Fake f=new Fake();f.gain=g;handles.add(f);return f;} }
    static AtmosAmbientPlaybackOwner.Snapshot snap(Object src,Object level,UUID place,Object cue,int x,int z,boolean eligible,boolean loaded,double camX){return snap(src,level,place,cue,x,z,eligible,loaded,camX,1,20);}
    static AtmosAmbientPlaybackOwner.Snapshot snap(Object src,Object level,UUID place,Object cue,int x,int z,boolean eligible,boolean loaded,double camX,float gain,float range){return new AtmosAmbientPlaybackOwner.Snapshot(src,level,place,x,0,z,cue,gain,range,eligible,loaded,camX,0,0);}
    static void assertReplacement(AtmosAmbientPlaybackOwner.Snapshot before,AtmosAmbientPlaybackOwner.Snapshot changed,float expectedGain){
        Recorder r=new Recorder();var o=new AtmosAmbientPlaybackOwner(r);o.observe(before);o.observe(before);assertEquals(1,r.handles.size());
        Fake old=r.handles.get(0);o.observe(changed);assertTrue(old.stopped);assertEquals(1,old.stops);assertEquals(2,r.handles.size());
        Fake replacement=r.handles.get(1);assertEquals(expectedGain,replacement.gain,1e-5f);o.observe(changed);
        assertEquals(2,r.handles.size());assertEquals(1,replacement.updates);assertEquals(expectedGain,replacement.gain,1e-5f);assertFalse(replacement.stopped);
    }
    @Test void placementGainAndRangeChangesReplaceOnlyTheMatchingSourceLoop(){
        Object src=new Object(),level=new Object(),cue="hum";UUID placement=UUID.randomUUID();double distance=Math.sqrt(.75);
        var base=snap(src,level,placement,cue,0,0,true,true,0);
        assertReplacement(base,snap(src,level,UUID.randomUUID(),cue,0,0,true,true,0),1-(float)(distance/20));
        assertReplacement(base,snap(src,level,placement,cue,0,0,true,true,0,.5f,20),.5f*(1-(float)(distance/20)));
        assertReplacement(base,snap(src,level,placement,cue,0,0,true,true,0,1,10),1-(float)(distance/10));
    }
    @Test void lifecycleReplacementInvalidationAndRangeReentry(){
        Recorder r=new Recorder(); var o=new AtmosAmbientPlaybackOwner(r); Object src=new Object(), level=new Object(), cue="cue"; UUID p=UUID.randomUUID();
        var s=snap(src,level,p,cue,0,0,true,true,0); o.observe(s);o.observe(s);assertEquals(1,r.handles.size());assertEquals(1-(float)(Math.sqrt(.75)/20),r.handles.get(0).gain,1e-5f);
        o.observe(snap(src,level,p,cue,0,0,true,true,30));assertEquals(1,r.handles.get(0).stops);
        o.observe(s);assertEquals(2,r.handles.size());
        o.observe(snap(src,level,p,"other",0,0,true,true,0));assertEquals(1,r.handles.get(1).stops);
        o.observe(snap(src,level,p,"other",0,0,false,true,0));assertEquals(0,o.sources().size());
        o.observe(s); var other=new Object(); o.observe(snap(src,other,p,cue,0,0,true,true,0));assertEquals(1,r.handles.get(2).stops);
        r.handles.get(3).stopped=true; o.observe(snap(src,other,p,cue,0,0,true,true,0));assertEquals(5,r.handles.size());
    }
    @Test void chunkRemovalIsScopedAndClearStopsAll(){
        Recorder r=new Recorder();var o=new AtmosAmbientPlaybackOwner(r); Object a=new Object(),b=new Object(),s1=new Object(),s2=new Object(); UUID p=UUID.randomUUID();
        o.observe(snap(s1,a,p,"c",17,0,true,true,17));o.observe(snap(s2,b,p,"c",17,0,true,true,17));
        o.removeChunk(a,1,0);assertEquals(1,r.handles.get(0).stops);assertEquals(0,r.handles.get(1).stops);
        o.clear();assertEquals(1,r.handles.get(1).stops);assertTrue(o.sources().isEmpty());
    }
    @Test void ventilationAndAtmosMachineSourcesCoexistInOneOwner(){
        record MachineSource() { }
        record VentilationSource() { }
        Recorder r=new Recorder(); var o=new AtmosAmbientPlaybackOwner(r); Object level=new Object(); UUID p1=UUID.randomUUID(),p2=UUID.randomUUID();
        Object machine=new MachineSource(), ventilation=new VentilationSource();
        var machineSnapshot=snap(machine,level,p1,"pump",0,0,true,true,0);
        var ventSnapshot=snap(ventilation,level,p2,"gas_vent",1,0,true,true,1);
        o.observe(machineSnapshot); o.observe(ventSnapshot);
        assertEquals(2,r.handles.size()); assertEquals(2,o.sources().size());
        o.observe(snap(ventilation,level,p2,"gas_vent",1,0,false,true,1));
        assertTrue(r.handles.get(1).stopped); assertFalse(r.handles.get(0).stopped);
        o.clear(); assertTrue(r.handles.get(0).stopped); assertTrue(o.sources().isEmpty());
    }
}
