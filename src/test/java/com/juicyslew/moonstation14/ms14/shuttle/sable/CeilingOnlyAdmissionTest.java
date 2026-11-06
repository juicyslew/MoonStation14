package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import java.util.UUID;
import java.util.List;

class CeilingOnlyAdmissionTest {
    @Test void bindsAllFrozenCeilingOnlyCallsIncludingPositiveRequest11446() {
        for (long sequence : new long[]{11446,18339,28494}) {
            var root=OwnerTraceFixture.load("owner-seq"+sequence+".json");
            var body=new Marker(EntityType.MARKER,null);
            var clipData=root.getAsJsonObject("worldClip");
            var bp=OwnerTraceFixture.vector(clipData.getAsJsonArray("positionBefore"));
            Vec3 before=new Vec3(bp.x(),bp.y(),bp.z());
            var b=clipData.getAsJsonArray("boundsBefore");
            var lo=OwnerTraceFixture.vector(b.get(0).getAsJsonArray()); var hi=OwnerTraceFixture.vector(b.get(1).getAsJsonArray());
            var bounds=new net.minecraft.world.phys.AABB(lo.x(),lo.y(),lo.z(),hi.x(),hi.y(),hi.z());
            body.setPos(before);
            var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
            var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
            var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
            var clip=OwnerTraceFixture.worldClip(root,body);
            var frame=UUID.fromString(root.get("frameId").getAsString());
            var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
            assertTrue(SableOwnedBodyMotion.ceilingOnlyPositionContext(body,nativeMotion,measured,rows,clip,before,bounds),"sequence "+sequence);
            if(sequence==11446) assertTrue(request.y()>0d);
            var proof=NativeSideContactProof.validateCeilingOnlyDetailed(request,nativeMotion,measured,frame,rows,r->true,clip).result().orElseThrow();
            var evidence=ContactPositionCorrectionEvidence.validateCeilingAndSide(request,proof.boundedOwn(),proof.geometryCorrection(),proof.horizontalContact()).orElseThrow();
            assertEquals(request.y()<0d?request.y():0d,evidence.boundedOwnDisplacement().y(),1e-12);
            assertNull(proof.horizontalContact());
            assertTrue(evidence.matches(request,proof.boundedOwn(),true));
            assertTrue(evidence.matches(request,proof.boundedOwn(),false));
            assertFalse(SableOwnedBodyMotion.worldClipMatchesCurrentMove(new Marker(EntityType.MARKER,null),nativeMotion,measured,clip,before,bounds));
            assertFalse(SableOwnedBodyMotion.worldClipMatchesCurrentMove(body,nativeMotion,measured,clip,before.add(1,0,0),bounds));
            assertFalse(SableOwnedBodyMotion.worldClipMatchesCurrentMove(body,nativeMotion,measured,clip,before,bounds.inflate(1)));
            assertFalse(SableOwnedBodyMotion.worldClipMatchesCurrentMove(body,nativeMotion.add(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,.01,0)),measured,clip,before,bounds));
            assertFalse(SableOwnedBodyMotion.worldClipMatchesCurrentMove(body,nativeMotion,measured.add(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,.01,0)),clip,before,bounds));
            assertFalse(SableOwnedBodyMotion.ceilingOnlyPositionContext(body,new MovementVector(nativeMotion.x(),0,nativeMotion.z()),measured,rows,clip,before,bounds));
            assertFalse(SableOwnedBodyMotion.ceilingOnlyPositionContext(body,nativeMotion,measured,List.of(),clip,before,bounds));
            assertFalse(SableOwnedBodyMotion.ceilingOnlyPositionContext(body,nativeMotion,measured,rows,null,before,bounds));
        }
    }
}
