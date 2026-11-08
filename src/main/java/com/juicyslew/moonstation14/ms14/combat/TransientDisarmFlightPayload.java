package com.juicyslew.moonstation14.ms14.combat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
public record TransientDisarmFlightPayload(ResourceLocation dimension, UUID item, Vec3 origin, Vec3 velocity, boolean participant, boolean actor) implements CustomPacketPayload {
 public static final Type<TransientDisarmFlightPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "disarm_flight"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TransientDisarmFlightPayload> STREAM_CODEC = StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeUUID(p.item);vec(b,p.origin);vec(b,p.velocity);b.writeBoolean(p.participant);b.writeBoolean(p.actor);},b->new TransientDisarmFlightPayload(b.readResourceLocation(),b.readUUID(),vec(b),vec(b),b.readBoolean(),b.readBoolean()));
  public TransientDisarmFlightPayload { if(dimension==null||item==null||!finite(origin)||!finite(velocity)||(actor&&!participant)) throw new IllegalArgumentException("invalid disarm flight"); }
  public TransientDisarmFlightPayload(ResourceLocation dimension, UUID item, Vec3 origin, Vec3 velocity, boolean participant) { this(dimension,item,origin,velocity,participant,false); }
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
 private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
