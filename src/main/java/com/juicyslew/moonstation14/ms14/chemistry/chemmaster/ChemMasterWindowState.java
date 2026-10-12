package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable authoritative snapshot; reagent order is the machine's first-appearance order. */
public record ChemMasterWindowState(long frame, long stateRevision, long slotRevision, long bufferRevision, long acknowledgedRequestId,
 Optional<String> rejectionReason, UUID sessionId, UUID machineId, UUID bodyId,
 long bodyEpoch, ResourceLocation dimension, int x, int y, int z, boolean open, Optional<String> closeReason,
 Optional<Component> loadedItemName, long inputCapacityCents, long inputVolumeCents,
 List<ReagentRow> inputReagents, List<ReagentRow> bufferReagents,
 ChemMasterControlRequest.Mode mode, ChemMasterControlRequest.Sort sort, OutputSlot outputSlot,
 ChemMasterControlRequest.DrawSource drawSource, int pillType, int pillDosageLimit, boolean updateLabel) {
 public record ReagentRow(ResourceKey<ReagentData> reagent, long amountCents) {
  public ReagentRow { Objects.requireNonNull(reagent); ReagentUnits.validateCents(amountCents, "amountCents"); }
 }
 public enum OutputKind { EMPTY, BOTTLE, PILL_CANISTER }
 public record PillDescriptor(int x, int y, int type, Optional<String> label, List<ReagentRow> reagents) {
  public PillDescriptor {
   if (x < 0 || y < 0 || type < 0 || type > 19 || label == null || reagents == null) throw new IllegalArgumentException("invalid pill descriptor");
   label = label.map(String::new); reagents = List.copyOf(reagents); validateRows(reagents);
  }
 }
 public record OutputSlot(long revision, OutputKind kind, Optional<Component> itemName, long capacityCents,
  long volumeCents, List<ReagentRow> reagents, int width, int height, int freeCells,
  Optional<String> label, List<PillDescriptor> pills) {
  public OutputSlot {
   if (revision < 0 || kind == null || itemName == null || label == null || pills == null || reagents == null
       || width < 0 || height < 0 || freeCells < 0) throw new IllegalArgumentException("invalid output slot snapshot");
   ReagentUnits.validateCents(capacityCents, "output.capacityCents"); ReagentUnits.validateCents(volumeCents, "output.volumeCents");
   if (volumeCents > capacityCents || kind == OutputKind.EMPTY && (itemName.isPresent() || capacityCents != 0 || volumeCents != 0
       || !reagents.isEmpty() || width != 0 || height != 0 || freeCells != 0 || label.isPresent() || !pills.isEmpty())
       || kind == OutputKind.BOTTLE && (width != 0 || height != 0 || freeCells != 0 || !pills.isEmpty())
       || kind == OutputKind.PILL_CANISTER && (capacityCents != 0 || volumeCents != 0 || !reagents.isEmpty()))
    throw new IllegalArgumentException("inconsistent output slot snapshot");
   reagents = List.copyOf(reagents); validateRows(reagents); pills = List.copyOf(pills); label = label.map(String::new);
   if (kind == OutputKind.PILL_CANISTER && freeCells > width * height) throw new IllegalArgumentException("invalid free cell count");
  }
 }
 public ChemMasterWindowState {
  if (frame < 0 || stateRevision < 0 || slotRevision < 0 || bufferRevision < 0 || acknowledgedRequestId < -1 || bodyEpoch <= 0
      || sessionId == null || machineId == null || bodyId == null || dimension == null || closeReason == null
      || rejectionReason == null || loadedItemName == null || inputReagents == null || bufferReagents == null
      || mode == null || sort == null || outputSlot == null || drawSource == null || pillType < 0 || pillType > 19
      || pillDosageLimit < 0 || pillDosageLimit > 10000 || (rejectionReason.isPresent() && acknowledgedRequestId < 0))
   throw new IllegalArgumentException("invalid ChemMaster state");
  ReagentUnits.validateCents(inputCapacityCents, "inputCapacityCents"); ReagentUnits.validateCents(inputVolumeCents, "inputVolumeCents");
  if (inputVolumeCents > inputCapacityCents || (!open && (loadedItemName.isPresent() || !inputReagents.isEmpty()))
      || (open && closeReason.isPresent())) throw new IllegalArgumentException("inconsistent ChemMaster state");
  inputReagents = List.copyOf(inputReagents); bufferReagents = List.copyOf(bufferReagents);
  if (validateRows(inputReagents) != inputVolumeCents) throw new IllegalArgumentException("input rows do not match displayed volume");
  validateRows(bufferReagents);
 }

 /** Source compatibility for existing fixture/painter construction sites; wire schema is the new schema. */
 public ChemMasterWindowState(long frame, long stateRevision, long slotRevision, long acknowledgedRequestId,
  Optional<String> rejectionReason, UUID sessionId, UUID machineId, UUID bodyId, long bodyEpoch,
  ResourceLocation dimension, int x, int y, int z, boolean open, Optional<String> closeReason,
  Optional<Component> loadedItemName, long inputCapacityCents, long inputVolumeCents,
  List<ReagentRow> inputReagents, List<ReagentRow> bufferReagents,
  ChemMasterControlRequest.Mode mode, ChemMasterControlRequest.Sort sort) {
  this(frame,stateRevision,slotRevision,0,acknowledgedRequestId,rejectionReason,sessionId,machineId,bodyId,bodyEpoch,
   dimension,x,y,z,open,closeReason,loadedItemName,inputCapacityCents,inputVolumeCents,inputReagents,bufferReagents,
   mode,sort,new OutputSlot(0,OutputKind.EMPTY,Optional.empty(),0,0,List.of(),0,0,0,Optional.empty(),List.of()),
   ChemMasterControlRequest.DrawSource.BUFFER,0,0,false);
 }
 private static long validateRows(List<ReagentRow> rows) {
  Set<ResourceKey<ReagentData>> keys = new HashSet<>(); List<Long> amounts = new ArrayList<>();
  for (ReagentRow row : rows) { if (!keys.add(row.reagent())) throw new IllegalArgumentException("duplicate reagent row"); amounts.add(row.amountCents()); }
  return ReagentUnits.total(amounts);
 }
}
