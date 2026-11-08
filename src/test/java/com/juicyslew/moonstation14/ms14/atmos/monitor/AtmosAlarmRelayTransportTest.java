package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production queued transport and relay policy on the power-up SyncAlerts path. */
class AtmosAlarmRelayTransportTest {
    private static final ResourceKey<Level> DIM=Level.OVERWORLD;
    private static final Set<String> ALARM_TAGS=Set.of("AirAlarm");
    private static final class Endpoint implements DeviceEndpoint {
        final DeviceIdentity id=DeviceIdentity.random(); final String address; final boolean transmitter;
        final List<DevicePayload> received=new ArrayList<>(); final AtmosAlarmReceiverState states=new AtmosAlarmReceiverState();
        Set<UUID> members=Set.of(); DeviceNetworkTransport transport; Endpoint reply; boolean powered=true;
        Endpoint(String address,boolean transmitter){this.address=address;this.transmitter=transmitter;}
        public DeviceIdentity identity(){return id;} public ResourceKey<Level> dimension(){return DIM;} public BlockPos position(){return BlockPos.ZERO;}
        public DeviceComposition composition(){return new DeviceComposition(List.of(),List.of(),Optional.of(new DeviceComposition.NetworkInterface(2,transmitter,true,address,"",1621,1621,false,true,true,true,false,false)),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(new DeviceComposition.ConnectionConfig(false,true,100)),Optional.empty());}
        public boolean available(){return true;} public boolean powered(){return powered;}
        public void receive(DevicePayload payload,String sourceAddress){received.add(payload);
            if(payload instanceof AtmosAlarmPayload.RelaySync sync&&members.contains(sync.requester())&&reply.address.equals(sourceAddress)) {
                transport.enqueue(packet(this,sourceAddress,new AtmosAlarmPayload.Relay(id.value(),states.reports(),ALARM_TAGS),false));
            } else if(payload instanceof AtmosAlarmPayload.Relay relay&&relay.source().equals(reply.id.value())&&reply.address.equals(sourceAddress)) {
                var before=states.severity();
                if(AtmosAlarmReportPolicy.applyRelay(states,relay.source(),Set.of("AirAlarm"),ALARM_TAGS,relay)
                        &&before!=states.severity())transport.enqueue(packet(this,reply.address,
                        new AtmosAlarmPayload.Relay(id.value(),states.reports(),ALARM_TAGS),false));
            }
        }
    }
    private static DeviceNetworkPacket packet(Endpoint sender,String dest,DevicePayload payload,boolean broadcast){return new DeviceNetworkPacket(sender.id,2,1621,sender.address,dest,broadcast,payload,2,1621);}

    @Test void firelockPowerupSyncGetsUnchangedDangerThroughDeferredTransport() {
        var transport=new DeviceNetworkTransport();var alarm=new Endpoint("alarm",true);var firelock=new Endpoint("firelock",true);
        alarm.transport=firelock.transport=transport;alarm.reply=firelock;firelock.reply=alarm;alarm.members=Set.of(firelock.id.value());
        alarm.states.report(UUID.randomUUID(),AtmosAlarmSeverity.DANGER);
        firelock.states.report(UUID.randomUUID(),AtmosAlarmSeverity.DANGER);firelock.states.powerDown();
        assertEquals(AtmosAlarmSeverity.NORMAL,firelock.states.severity());
        transport.register(alarm);transport.register(firelock);
        assertTrue(transport.enqueue(packet(firelock,"",new AtmosAlarmPayload.RelaySync(firelock.id.value()),true)));
        assertEquals(1,transport.drain());assertEquals(AtmosAlarmSeverity.NORMAL,firelock.states.severity());
        assertEquals(1,transport.drain());assertEquals(AtmosAlarmSeverity.DANGER,firelock.states.severity());
        assertEquals(1,transport.drain());assertEquals(0,transport.queuedPackets(),"unchanged aggregate feedback must stop");
        assertEquals(1,((AtmosAlarmPayload.Relay)firelock.received.getFirst()).reports().size());
    }

    @Test void sourceMapNormalClearsDangerWithoutPeerAggregateEcho() {
        var transport=new DeviceNetworkTransport();var a=new Endpoint("a",true);var b=new Endpoint("b",true);
        a.transport=b.transport=transport;a.reply=b;b.reply=a;transport.register(a);transport.register(b);
        var source=UUID.randomUUID();
        a.states.report(source,AtmosAlarmSeverity.DANGER);
        assertTrue(transport.enqueue(packet(a,"b",new AtmosAlarmPayload.Relay(a.id.value(),a.states.reports(),ALARM_TAGS),false)));
        assertEquals(1,transport.drain());assertEquals(AtmosAlarmSeverity.DANGER,b.states.severity());
        assertEquals(1,transport.drain());assertEquals(0,transport.queuedPackets());
        a.states.report(source,AtmosAlarmSeverity.NORMAL);
        assertTrue(transport.enqueue(packet(a,"b",new AtmosAlarmPayload.Relay(a.id.value(),a.states.reports(),ALARM_TAGS),false)));
        assertEquals(1,transport.drain());assertEquals(1,transport.drain());assertEquals(0,transport.queuedPackets());
        assertEquals(AtmosAlarmSeverity.NORMAL,b.states.severity());
    }

    @Test void wrongPeerUuidOrAddressCannotReplaceTheCachedRelayMap() {
        var transport=new DeviceNetworkTransport();var alarm=new Endpoint("alarm",true);var firelock=new Endpoint("firelock",true);var impostor=new Endpoint("impostor",true);
        firelock.reply=alarm;for(var endpoint:List.of(alarm,firelock,impostor))transport.register(endpoint);
        var danger=new AtmosAlarmPayload.Relay(alarm.id.value(),Map.of(UUID.randomUUID(),AtmosAlarmSeverity.DANGER),ALARM_TAGS);
        assertTrue(transport.enqueue(packet(impostor,"firelock",danger,false)));transport.drain();
        assertEquals(AtmosAlarmSeverity.NORMAL,firelock.states.severity());
        var wrongId=new AtmosAlarmPayload.Relay(impostor.id.value(),danger.reports(),ALARM_TAGS);
        assertTrue(transport.enqueue(packet(impostor,"firelock",wrongId,false)));transport.drain();
        assertEquals(AtmosAlarmSeverity.NORMAL,firelock.states.severity());
    }

    @Test void senderUnloadDoesNotSynthesizeANormalOrDeleteKnownReport() {
        var transport=new DeviceNetworkTransport();var alarm=new Endpoint("alarm",true);var receiver=new Endpoint("receiver",true);
        alarm.transport=receiver.transport=transport;receiver.reply=alarm;transport.register(alarm);transport.register(receiver);
        var source=UUID.randomUUID();alarm.states.report(source,AtmosAlarmSeverity.DANGER);
        assertTrue(transport.enqueue(packet(alarm,"receiver",new AtmosAlarmPayload.Relay(alarm.id.value(),alarm.states.reports(),ALARM_TAGS),false)));
        transport.drain();assertEquals(AtmosAlarmSeverity.DANGER,receiver.states.severity());
        transport.unload(alarm.id);
        assertEquals(AtmosAlarmSeverity.DANGER,receiver.states.severity());
    }
}
