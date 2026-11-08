package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;

class AtmosMachineControlProtocolTest {
    @Test void requestRejectsUnboundedAndInvalidCorrelation(){
        UUID id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlRequest(-1,id,0,0,AtmosMachineSettings.Operation.SET_ENABLED,false,0,0));
        assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlRequest(1,id,0,0,AtmosMachineSettings.Operation.SET_ENABLED,false,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlRequest(1,id,0,0,AtmosMachineSettings.Operation.SET_ENABLED,false,0,Integer.MAX_VALUE));
    }
    @Test void initialSnapshotCanCarryUnacknowledgedSettings(){
        var values=new AtmosMachineSettings.Values(0,Optional.of(true),Optional.of(101.325),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
        var state=new AtmosMachineControlSnapshot(1,UUID.randomUUID(),-1,true,values,new AtmosMachineControlSnapshot.Controls("p",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP,true,true,false,false,false,false,4500,0,0,0),0,false);
        assertEquals(-1,state.requestId());
        assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlSnapshot(1,state.session(),-2,true,values,state.controls(),0,false));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);AtmosMachineControlSnapshot.STREAM_CODEC.encode(buf,state);assertEquals(state,AtmosMachineControlSnapshot.STREAM_CODEC.decode(buf));
    }
    @Test void filterWithoutSelectedGasStillAdvertisesSelectorAndRoundTrips(){var c=new AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);assertTrue(c.filterGas());var v=new AtmosMachineSettings.Values(0,Optional.of(false),Optional.empty(),Optional.of(1d),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());var x=new AtmosMachineControlSnapshot(2,UUID.randomUUID(),0,true,v,c,0,false);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);AtmosMachineControlSnapshot.STREAM_CODEC.encode(b,x);assertEquals(x,AtmosMachineControlSnapshot.STREAM_CODEC.decode(b));}
     @Test void requestEnvelopePolicySeparatesReplayFromStaleSettings(){var s=UUID.randomUUID();var r=new AtmosMachineControlRequest(4,s,8,3,AtmosMachineSettings.Operation.SET_ENABLED,true,0,0);assertTrue(AtmosMachineMenuService.admitRequest(4,s,7,r,4));assertFalse(AtmosMachineMenuService.admitRequest(4,s,8,r,3));assertFalse(AtmosMachineMenuService.admitRequest(4,UUID.randomUUID(),7,r,3));assertFalse(AtmosMachineMenuService.admitRequest(5,s,7,r,3));assertNotEquals(r.expectedRevision(),4);}
     @Test void snapshotRejectsNonfiniteAndUnsupportedValues(){var c=new AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);var invalid=new AtmosMachineSettings.Values(0,Optional.of(true),Optional.empty(),Optional.of(Double.POSITIVE_INFINITY),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlSnapshot(1,UUID.randomUUID(),0,false,invalid,c,0,false));assertThrows(IllegalArgumentException.class,()->new AtmosMachineControlSnapshot(1,UUID.randomUUID(),0,false,new AtmosMachineSettings.Values(0,Optional.of(true),Optional.empty(),Optional.of(2d),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty()),c,0,false));}
     @Test void sixOrdinaryProfilesRoundTripCapabilitiesAndSettings(){for(var k:new AtmosMachineProfile.Kind[]{AtmosMachineProfile.Kind.PRESSURE_PUMP,AtmosMachineProfile.Kind.VOLUME_PUMP,AtmosMachineProfile.Kind.MIXER,AtmosMachineProfile.Kind.FILTER,AtmosMachineProfile.Kind.HEATER,AtmosMachineProfile.Kind.FREEZER}){boolean p=k==AtmosMachineProfile.Kind.PRESSURE_PUMP||k==AtmosMachineProfile.Kind.MIXER,r=k==AtmosMachineProfile.Kind.VOLUME_PUMP||k==AtmosMachineProfile.Kind.FILTER,m=k==AtmosMachineProfile.Kind.MIXER,f=k==AtmosMachineProfile.Kind.FILTER,t=k==AtmosMachineProfile.Kind.HEATER||k==AtmosMachineProfile.Kind.FREEZER;var values=new AtmosMachineSettings.Values(2,Optional.of(false),p||m?Optional.of(120d):Optional.empty(),r?Optional.of(.15):Optional.empty(),m?Optional.of(.3):Optional.empty(),Optional.empty(),t?Optional.of(300d):Optional.empty(),Optional.empty());var controls=new AtmosMachineControlSnapshot.Controls("p",k,true,p||m,r,m,f,t,p||m?4500:0,r?1:0,t?73:0,t?593:0);var s=new AtmosMachineControlSnapshot(3,UUID.randomUUID(),-1,true,values,controls,0,false);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);AtmosMachineControlSnapshot.STREAM_CODEC.encode(b,s);assertEquals(s,AtmosMachineControlSnapshot.STREAM_CODEC.decode(b));}}
      @Test void decoderRejectsMalformedGasIndexDirectlyFromBuffer(){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);b.writeVarInt(1);b.writeUUID(UUID.randomUUID());b.writeVarLong(1);b.writeVarLong(0);b.writeEnum(AtmosMachineSettings.Operation.SET_FILTER_GAS);b.writeBoolean(false);b.writeDouble(0);b.writeVarInt(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values().length);assertThrows(IllegalArgumentException.class,()->AtmosMachineControlRequest.STREAM_CODEC.decode(b));}
      @Test void lifecycleTransportRoundTripsAndBoundsCloseReason(){var id=UUID.randomUUID();var open=new AtmosMachineSessionState(id,true,"");var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);AtmosMachineSessionState.STREAM_CODEC.encode(b,open);assertEquals(open,AtmosMachineSessionState.STREAM_CODEC.decode(b));var close=new AtmosMachineSessionClose(id);b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);AtmosMachineSessionClose.STREAM_CODEC.encode(b,close);assertEquals(close,AtmosMachineSessionClose.STREAM_CODEC.decode(b));assertThrows(IllegalArgumentException.class,()->new AtmosMachineSessionState(id,false,"x".repeat(65)));}
}
