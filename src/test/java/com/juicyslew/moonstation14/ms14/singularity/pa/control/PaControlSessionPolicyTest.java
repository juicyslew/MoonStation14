package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSessionPolicy.Match.INVALID;
import static com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSessionPolicy.Match.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaControlSessionPolicyTest {
    private record Fixture(PaControlSessionPolicy.Pin pin, PaControlSessionPolicy.Facts facts) {
        static Fixture create() {
            var player = new Object(); var id = UUID.randomUUID(); var body = UUID.randomUUID();
            var machine = UUID.randomUUID(); var position = new BlockPos(4, 65, 8);
            var dimension = ResourceLocation.withDefaultNamespace("overworld");
            var profile = ResourceLocation.withDefaultNamespace("standard");
            return new Fixture(new PaControlSessionPolicy.Pin(id, player, machine, position, body, 3, dimension, profile),
                    new PaControlSessionPolicy.Facts(player, id, body, 3, dimension, machine, position, profile,
                            true, true, true, true));
        }
    }

    @Test void exactViewerSessionAndLivePinsAreRequired() {
        var f = Fixture.create();
        assertEquals(VALID, PaControlSessionPolicy.match(f.pin(), f.facts()));
        var otherViewer = new PaControlSessionPolicy.Facts(new Object(), f.pin().id(), f.pin().body(), 3,
                f.pin().dimension(), f.pin().machine(), f.pin().position(), f.pin().profile(), true, true, true, true);
        assertEquals(INVALID, PaControlSessionPolicy.match(f.pin(), otherViewer));
        var otherSession = new PaControlSessionPolicy.Facts(f.pin().player(), UUID.randomUUID(), f.pin().body(), 3,
                f.pin().dimension(), f.pin().machine(), f.pin().position(), f.pin().profile(), true, true, true, true);
        assertEquals(INVALID, PaControlSessionPolicy.match(f.pin(), otherSession));
    }

    @Test void bodyEpochDimensionMachinePlacementAndProfileChangesInvalidate() {
        var f = Fixture.create(); var p = f.pin(); var x = f.facts();
        assertEquals(INVALID, PaControlSessionPolicy.match(p, new PaControlSessionPolicy.Facts(x.player(),x.session(),UUID.randomUUID(),x.epoch(),x.dimension(),x.machine(),x.position(),x.profile(),true,true,true,true)));
        assertEquals(INVALID, PaControlSessionPolicy.match(p, new PaControlSessionPolicy.Facts(x.player(),x.session(),x.body(),x.epoch()+1,x.dimension(),x.machine(),x.position(),x.profile(),true,true,true,true)));
        assertEquals(INVALID, PaControlSessionPolicy.match(p, new PaControlSessionPolicy.Facts(x.player(),x.session(),x.body(),x.epoch(),ResourceLocation.withDefaultNamespace("the_nether"),x.machine(),x.position(),x.profile(),true,true,true,true)));
        assertEquals(INVALID, PaControlSessionPolicy.match(p, new PaControlSessionPolicy.Facts(x.player(),x.session(),x.body(),x.epoch(),x.dimension(),UUID.randomUUID(),x.position(),x.profile(),true,true,true,true)));
        assertEquals(INVALID, PaControlSessionPolicy.match(p, new PaControlSessionPolicy.Facts(x.player(),x.session(),x.body(),x.epoch(),x.dimension(),x.machine(),x.position(),ResourceLocation.withDefaultNamespace("other"),true,true,true,true)));
    }

    @Test void actorTargetReachAndPowerMustRemainValid() {
        var f = Fixture.create(); var x=f.facts();
        assertEquals(INVALID, facts(f.pin(),false,true,true,true,x));
        assertEquals(INVALID, facts(f.pin(),true,false,true,true,x));
        assertEquals(INVALID, facts(f.pin(),true,true,false,true,x));
        assertEquals(INVALID, facts(f.pin(),true,true,true,false,x));
    }
    private PaControlSessionPolicy.Match facts(PaControlSessionPolicy.Pin pin, boolean actor, boolean target, boolean reach, boolean power,
                                                PaControlSessionPolicy.Facts x) {
        return PaControlSessionPolicy.match(pin, new PaControlSessionPolicy.Facts(x.player(),x.session(),x.body(),x.epoch(),x.dimension(),x.machine(),x.position(),x.profile(),actor,target,reach,power));
    }

    @Test void staleRevisionConsumesFreshIdAndRetryIsReplayUntilANewerCurrentRevisionRequest() {
        var stale = PaControlSessionPolicy.actionAdmission(8, 9, 4, 5);
        assertEquals(9, stale.lastRequestId());
        assertEquals(PaControlSessionPolicy.ActionDecision.STALE_REVISION, stale.decision());

        var retry = PaControlSessionPolicy.actionAdmission(stale.lastRequestId(), 9, 5, 5);
        assertEquals(9, retry.lastRequestId());
        assertEquals(PaControlSessionPolicy.ActionDecision.REPLAY, retry.decision());

        var fresh = PaControlSessionPolicy.actionAdmission(retry.lastRequestId(), 10, 5, 5);
        assertEquals(10, fresh.lastRequestId());
        assertEquals(PaControlSessionPolicy.ActionDecision.ACCEPTED, fresh.decision());
        assertEquals(PaControlSessionPolicy.ActionDecision.REPLAY,
                PaControlSessionPolicy.actionAdmission(8, 8, 5, 5).decision());
        assertEquals(PaControlSessionPolicy.ActionDecision.REPLAY,
                PaControlSessionPolicy.actionAdmission(8, 7, 5, 5).decision());
    }
}
