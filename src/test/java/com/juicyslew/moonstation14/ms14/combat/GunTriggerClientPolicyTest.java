package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GunTriggerClientPolicyTest {
    private static GunTriggerClientPolicy.Context c(long epoch, String dimension, String hand, String token, String mode) {
        return new GunTriggerClientPolicy.Context(UUID.fromString("00000000-0000-0000-0000-000000000001"), epoch, dimension, hand, token, mode);
    }
    @Test void semiWaitsForReleaseAndStopsOnlyOnce() {
        var p = new GunTriggerClientPolicy(); var context = c(1,"overworld","right","token","SEMI_AUTO");
        long id=p.begin(context); assertEquals(new GunTriggerClientPolicy.Shot(id,true),p.nextShot(context,false,true));
        assertTrue(p.submitted(id)); assertNull(p.nextShot(context,false,true));
        var stop=p.release(); assertEquals(new GunTriggerClientPolicy.Stop(1,id,"right","token"),stop);
        assertNull(p.release()); assertNull(p.contextLost(context));
    }
    @Test void fullAutoRequiresReadyAndNoPendingAndUsesSameId() {
        var p=new GunTriggerClientPolicy(); var context=c(1,"overworld","right","token","FULL_AUTO"); long id=p.begin(context);
        assertNull(p.nextShot(context,true,true)); assertNull(p.nextShot(context,false,false));
        assertEquals(new GunTriggerClientPolicy.Shot(id,true),p.nextShot(context,false,true)); p.submitted(id);
        assertNull(p.nextShot(context,true,true));
        assertEquals(new GunTriggerClientPolicy.Shot(id,false),p.nextShot(context,false,true));
    }
    @Test void changedContextEndsHoldAndCounterResetsOnlyOnEpoch() {
        var p=new GunTriggerClientPolicy(); var a=c(2,"overworld","right","a","FULL_AUTO"); long one=p.begin(a);
        assertEquals(new GunTriggerClientPolicy.Stop(2,one,"right","a"),p.contextLost(c(2,"nether","right","a","FULL_AUTO")));
        long two=p.begin(c(2,"overworld","left","b","FULL_AUTO")); assertTrue(two>one);
        p.deniedOrTimedOut(); long three=p.begin(c(3,"overworld","left","b","FULL_AUTO")); assertEquals(1,three);
    }
    @Test void staleRejectedTriggerCannotReleaseNewPress() {
        var p=new GunTriggerClientPolicy(); var context=c(1,"overworld","right","token","SEMI_AUTO");
        long old=p.begin(context); p.submitted(old); p.release();
        long current=p.begin(context);
        assertNull(p.releaseIfMatches(old));
        assertTrue(p.held());
        assertEquals(new GunTriggerClientPolicy.Stop(1,current,"right","token"),p.releaseIfMatches(current));
        assertNull(p.releaseIfMatches(current));
    }
}
