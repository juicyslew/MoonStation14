package com.juicyslew.moonstation14.ms14.mobs.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalAnimationTransitionTest {
    private float runAtRate(int fps) {
        var state=new AnimalAnimationTransition.State();
        state.observe("cow",0,0,0,2.5);
        state.select("cow","walk",0,false);
        for(int i=1;i<=fps;i++) {
            double t=i/(double)fps;
            boolean moving=state.observe("cow",t,t,0,2.5);
            state.select("cow",moving?"walk":"idle_breathe",t,false);
        }
        return state.select("cow","walk",1,false).destinationPhase();
    }

    @Test void acceptedPositionPathIsFramerateIndependentAndSameTimeDoesNotAdvance() {
        assertEquals(runAtRate(20),runAtRate(10),.0001f);
        assertEquals(runAtRate(20),runAtRate(60),.0001f);
        var state=new AnimalAnimationTransition.State();
        state.observe("cow",0,0,0,2.5); state.select("cow","walk",0,false);
        state.observe("cow",.05,.05,0,2.5);
        var first=state.select("cow","walk",.05,false);
        assertEquals(first,state.select("cow","walk",.05,false));
    }

    @Test void stopFreezesWalkPhaseAndAppearanceAndDownReset() {
        var state=new AnimalAnimationTransition.State();
        state.observe("cow",0,0,0,2.5); state.select("cow","walk",0,false);
        state.observe("cow",.05,.05,0,2.5); state.select("cow","walk",.05,false);
        state.observe("cow",.1,.05,0,2.5);
        var stopped=state.select("cow","idle_breathe",.1,false);
        float frozen=stopped.sourcePhase();
        state.observe("cow",.15,.05,0,2.5);
        assertEquals(frozen,state.select("cow","idle_breathe",.15,false).sourcePhase());
        var reset=state.select("bee","idle_breathe",.21,false);
        assertEquals("idle_breathe",reset.destination());
        var down=state.select("bee","down_hold",.22,true);
        assertEquals(1,down.destinationWeight());
    }

    @Test void flightAdvancesWithoutDisplacementAndEntityClocksAreIndependent() {
        var a=new AnimalAnimationTransition.State(); var b=new AnimalAnimationTransition.State();
        a.observe("bat",0,0,0,2.5); a.select("bat","fly",0,false);
        b.observe("bat",0,0,0,2.5); b.select("bat","idle_breathe",0,false);
        a.observe("bat",.2,0,0,2.5);
        assertEquals(.2f,a.select("bat","fly",.2,false).destinationPhase(),.0001f);
        assertEquals(0,b.select("bat","idle_breathe",.2,false).destinationPhase());
    }

    @Test void crabWalkAndRunUseFasterAuthoredRateButOtherClipsAndSpeciesDoNot() {
        assertEquals(1.5F, AnimalAnimationTransition.State.authoredPlaybackRate("crab", "walk"));
        assertEquals(1.5F, AnimalAnimationTransition.State.authoredPlaybackRate("crab", "run"));
        assertEquals(1F, AnimalAnimationTransition.State.authoredPlaybackRate("crab", "idle_breathe"));
        assertEquals(1F, AnimalAnimationTransition.State.authoredPlaybackRate("crab", "down_hold"));
        assertEquals(1F, AnimalAnimationTransition.State.authoredPlaybackRate("snake", "walk"));
    }
}
