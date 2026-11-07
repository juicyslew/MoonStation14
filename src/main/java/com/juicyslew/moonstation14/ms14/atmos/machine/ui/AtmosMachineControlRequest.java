package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Bounded setting intent; placement identity and coordinates are never client supplied. */
public record AtmosMachineControlRequest(int containerId, UUID session, long requestId, long expectedRevision,
        AtmosMachineSettings.Operation operation, boolean desired, double numericValue, int gasIndex) implements CustomPacketPayload {
    public static final Type<AtmosMachineControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "atmos_machine_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosMachineControlRequest> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeVarInt(v.containerId);b.writeUUID(v.session);b.writeVarLong(v.requestId);b.writeVarLong(v.expectedRevision);b.writeEnum(v.operation);b.writeBoolean(v.desired);b.writeDouble(v.numericValue);b.writeVarInt(v.gasIndex);},
            b->{int c=b.readVarInt();UUID s=b.readUUID();long r=b.readVarLong(),rev=b.readVarLong();var op=b.readEnum(AtmosMachineSettings.Operation.class);boolean desired=b.readBoolean();double number=b.readDouble();int gas=b.readVarInt();if(gas<0||gas>=GasType.values().length)throw new IllegalArgumentException("gas index");return new AtmosMachineControlRequest(c,s,r,rev,op,desired,number,gas);});
    public AtmosMachineControlRequest { if(containerId<0||session==null||requestId<0||expectedRevision<0||operation==null||!Double.isFinite(numericValue)||numericValue<0||gasIndex<0||gasIndex>=GasType.values().length)throw new IllegalArgumentException("Invalid atmos machine intent"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
