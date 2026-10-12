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
 Optional<ResourceKey<ReagentData>> reagent, long amountCents, long expectedOutputSlotRevision,
 long expectedBufferRevision, Optional<DrawSource> drawSource, int pillType, Optional<OutputRequest> outputRequest) implements CustomPacketPayload {
  public enum Operation { QUERY, CLOSE, EJECT, SET_MODE, SET_SORT, INPUT_REAGENT, BUFFER_REAGENT,
   SET_DRAW_SOURCE, SET_PILL_TYPE, CREATE_PILLS, OUTPUT_TO_BOTTLE, EJECT_OUTPUT }
  public enum Mode { TRANSFER, DISCARD }
  public enum Sort { OLDEST_FIRST, ALPHABETICAL, QUANTITY, RECENT_FIRST }
  public enum DrawSource { BUFFER, INPUT }
  public record OutputRequest(int count, int doseUnits, String label) {
   public static final int MAX_LABEL_LENGTH=50;
   public OutputRequest { if(count<0||count>64||doseUnits<0||doseUnits>21474836||label==null||label.length()>MAX_LABEL_LENGTH) throw new IllegalArgumentException("invalid package request"); }
  }
 /** All uses the shared supported quantity ceiling, never a floating-point sentinel. */
 public static long allAmountCents() { return ReagentUnits.MAX_CENTS; }
 public static final Type<ChemMasterControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "chemmaster_control"));
 public static final StreamCodec<RegistryFriendlyByteBuf, ChemMasterControlRequest> STREAM_CODEC = StreamCodec.of(ChemMasterWireCodec::writeRequest, ChemMasterWireCodec::readRequest);
 public ChemMasterControlRequest {
   if (sessionId == null || machineId == null || dimension == null || position == null || requestId < 0 || expectedStateRevision < 0 || expectedSlotRevision < 0 || expectedOutputSlotRevision < 0 || expectedBufferRevision < 0 || operation == null || mode == null || sort == null || reagent == null || drawSource == null || outputRequest == null) throw new IllegalArgumentException("invalid ChemMaster request");
  ReagentUnits.validateCents(amountCents, "amountCents");
   boolean selects = operation == Operation.INPUT_REAGENT || operation == Operation.BUFFER_REAGENT;
   boolean usesSource = operation == Operation.SET_DRAW_SOURCE;
   boolean usesType = operation == Operation.SET_PILL_TYPE;
   boolean packages = operation == Operation.CREATE_PILLS || operation == Operation.OUTPUT_TO_BOTTLE;
   boolean usesOutputSlot = packages || operation == Operation.EJECT_OUTPUT;
   if (selects != reagent.isPresent() || (!selects && amountCents != 0) || (selects && amountCents == 0)
     || usesSource != drawSource.isPresent() || usesType != (pillType >= 0 && pillType <= 19)
     || (!usesType && pillType != -1) || packages != outputRequest.isPresent()
     || (!usesOutputSlot && expectedOutputSlotRevision != 0) || (!packages && expectedBufferRevision != 0))
    throw new IllegalArgumentException("noncanonical ChemMaster request fields");
   if (operation == Operation.CREATE_PILLS && (outputRequest.orElseThrow().count() < 1 || outputRequest.orElseThrow().doseUnits() < 1))
    throw new IllegalArgumentException("invalid pill package request");
   if (operation == Operation.OUTPUT_TO_BOTTLE && (outputRequest.orElseThrow().count() != 0 || outputRequest.orElseThrow().doseUnits() < 1))
    throw new IllegalArgumentException("invalid bottle package request");
  }
  /** Existing workspace constructor; new output proof fields default to unused canonical values. */
  public ChemMasterControlRequest(UUID sessionId, UUID machineId, ResourceLocation dimension, BlockPos position,
   long requestId, long stateRevision, long slotRevision, Operation operation, Mode mode, Sort sort,
   Optional<ResourceKey<ReagentData>> reagent, long amountCents) {
   this(sessionId,machineId,dimension,position,requestId,stateRevision,slotRevision,operation,mode,sort,reagent,amountCents,
    0,0,Optional.empty(),-1,Optional.empty());
  }
 @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
