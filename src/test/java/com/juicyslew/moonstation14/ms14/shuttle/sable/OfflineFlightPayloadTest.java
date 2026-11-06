package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfflineFlightPayloadTest {
    private final UUID id=UUID.randomUUID();
    private CompoundTag payload() {
        CompoundTag root=new CompoundTag(); root.putUUID("uuid",id);
        CompoundTag pose=new CompoundTag(); pose.put("position",vec(4,5,6)); pose.put("rotation_point",vec(0,0,0));
        CompoundTag orientation=new CompoundTag(); orientation.putDouble("x",0);orientation.putDouble("y",0);
        orientation.putDouble("z",0);orientation.putDouble("w",1);pose.put("orientation",orientation);root.put("pose",pose);
        root.put("linear_velocity",vec(0,20,0)); return root;
    }
    private static CompoundTag vec(double x,double y,double z) {
        CompoundTag t=new CompoundTag();t.putDouble("x",x);t.putDouble("y",y);t.putDouble("z",z);return t;
    }
    @Test void validSourceShapedPayloadAndSnapshotsAreBoundedCopies() {
        CompoundTag input=payload(); var sample=OfflineFlightPayload.validate(id,input);
        assertTrue(sample.valid(),sample.toString());assertEquals(id,sample.uuid());assertEquals(20,sample.linear().y);
        var pose=sample.pose();pose.putDouble("extra",7);assertFalse(sample.pose().contains("extra"));
        var velocity=sample.linear();velocity.y=0;assertEquals(20,sample.linear().y);
        input.getCompound("pose").getCompound("position").putDouble("y",99);
        assertEquals(5,sample.pose().getCompound("position").getDouble("y"));
    }
    @Test void rejectsMissingAndMismatchedIdentityOrPayload() {
        assertFalse(OfflineFlightPayload.validate(null,payload()).valid());
        assertFalse(OfflineFlightPayload.validate(id,null).valid());
        assertFalse(OfflineFlightPayload.validate(UUID.randomUUID(),payload()).valid());
        assertFalse(OfflineFlightPayload.validate(id,new CompoundTag()).valid());
    }
    @Test void rejectsIncompleteOrWrongTypedPose() {
        CompoundTag p=payload();p.getCompound("pose").getCompound("orientation").remove("w");
        assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("pose").getCompound("position").putInt("x",4);
        assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("pose").getCompound("rotation_point").putDouble("z",Double.NaN);
        assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("pose").getCompound("orientation").putDouble("w",Double.POSITIVE_INFINITY);
        assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("pose").getCompound("orientation").putDouble("w",0);
        assertFalse(OfflineFlightPayload.validate(id,p).valid());
    }
    @Test void requiresFiniteSufficientLinearAndValidOptionalAngularVelocity() {
        CompoundTag p=payload();p.remove("linear_velocity");assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("linear_velocity").putDouble("y",1);assertFalse(OfflineFlightPayload.validate(id,p).valid());
        p=payload();p.getCompound("linear_velocity").putDouble("x",Double.NaN);
        var badLinear=OfflineFlightPayload.validate(id,p);assertFalse(badLinear.valid());
        assertNull(badLinear.angular());
        p=payload();p.put("angular_velocity",vec(0,0,Double.POSITIVE_INFINITY));
        var badAngular=OfflineFlightPayload.validate(id,p);assertFalse(badAngular.valid());
        assertEquals(20,badAngular.linear().y);assertNull(badAngular.angular());
        p=payload();p.putInt("angular_velocity",3);
        var wrongAngularType=OfflineFlightPayload.validate(id,p);
        assertFalse(wrongAngularType.valid());assertEquals(20,wrongAngularType.linear().y);assertNull(wrongAngularType.angular());
        p=payload();assertTrue(OfflineFlightPayload.validate(id,p).valid());
        p.put("angular_velocity",vec(0,0,1));assertTrue(OfflineFlightPayload.validate(id,p).valid());
    }
    @Test void compiledTagPreflightSelectsOnlyFlightFixture() throws Exception {
        var classes=java.nio.file.Path.of(System.getProperty("moonstation14.gametestClasses"));
        var selected=com.juicyslew.moonstation14.gametest.support.GameTestTagPreflight.validate(
                classes,"shuttle-flight-offline-chunk-persistence");
        assertEquals(1,selected.selected());
        System.out.println("Offline flight GameTest preflight: "+selected.selected()+"/"+selected.tests());
    }
}
