package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authoritative update for a nonmodal ChemMaster workspace. */
public record ChemMasterWindowUpdate(ChemMasterWindowState state) implements CustomPacketPayload {
 public static final Type<ChemMasterWindowUpdate> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "chemmaster_window"));
 public static final StreamCodec<RegistryFriendlyByteBuf, ChemMasterWindowUpdate> STREAM_CODEC = StreamCodec.of((b,v)->ChemMasterWireCodec.writeState(b,v.state), b->new ChemMasterWindowUpdate(ChemMasterWireCodec.readState(b)));
 public ChemMasterWindowUpdate { if (state == null) throw new IllegalArgumentException("missing ChemMaster state"); }
 @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
