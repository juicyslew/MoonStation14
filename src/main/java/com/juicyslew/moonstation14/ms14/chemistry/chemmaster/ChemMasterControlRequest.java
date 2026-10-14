package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

/** Untrusted command for one independently bound ChemMaster interaction. */
public record ChemMasterControlRequest(UUID sessionId, UUID machineId, ResourceLocation dimension, BlockPos position,
 long requestId, long expectedStateRevision, long expectedSlotRevision, Operation operation, Mode mode, Sort sort,
 Optional<ResourceKey<ReagentData>> reagent, long amountCents) implements CustomPacketPayload {
 public enum Operation { QUERY, CLOSE, EJECT, SET_MODE, SET_SORT, INPUT_REAGENT, BUFFER_REAGENT }
 public enum Mode { TRANSFER, DISCARD }
 public enum Sort { OLDEST_FIRST, ALPHABETICAL, QUANTITY, RECENT_FIRST }
 /** All uses the shared supported quantity ceiling, never a floating-point sentinel. */
 public static long allAmountCents() { return ReagentUnits.MAX_CENTS; }
 public static final Type<ChemMasterControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "chemmaster_control"));
 public static final StreamCodec<RegistryFriendlyByteBuf, ChemMasterControlRequest> STREAM_CODEC = StreamCodec.of(ChemMasterWireCodec::writeRequest, ChemMasterWireCodec::readRequest);
 public ChemMasterControlRequest {
  if (sessionId == null || machineId == null || dimension == null || position == null || requestId < 0 || expectedStateRevision < 0 || expectedSlotRevision < 0 || operation == null || mode == null || sort == null || reagent == null) throw new IllegalArgumentException("invalid ChemMaster request");
  ReagentUnits.validateCents(amountCents, "amountCents");
  boolean selects = operation == Operation.INPUT_REAGENT || operation == Operation.BUFFER_REAGENT;
  if (selects != reagent.isPresent() || (!selects && amountCents != 0) || (selects && amountCents == 0)) throw new IllegalArgumentException("noncanonical ChemMaster request fields");
 }
 @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

