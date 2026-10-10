package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable authoritative snapshot; buffer row order is its first-appearance order. */
public record ChemMasterWindowState(long frame, long stateRevision, long slotRevision, long acknowledgedRequestId,
 Optional<String> rejectionReason, UUID sessionId, UUID machineId, UUID bodyId,
 long bodyEpoch, ResourceLocation dimension, int x, int y, int z, boolean open, Optional<String> closeReason,
 Optional<Component> loadedItemName, long inputCapacityCents, long inputVolumeCents,
 List<ReagentRow> inputReagents, List<ReagentRow> bufferReagents,
 ChemMasterControlRequest.Mode mode, ChemMasterControlRequest.Sort sort) {
 public record ReagentRow(ResourceKey<ReagentData> reagent, long amountCents) {
  public ReagentRow { Objects.requireNonNull(reagent); ReagentUnits.validateCents(amountCents, "amountCents"); }
 }
 public ChemMasterWindowState {
  if (frame < 0 || stateRevision < 0 || slotRevision < 0 || acknowledgedRequestId < -1 || bodyEpoch <= 0 || sessionId == null || machineId == null || bodyId == null || dimension == null || closeReason == null || rejectionReason == null || loadedItemName == null || inputReagents == null || bufferReagents == null || mode == null || sort == null || (rejectionReason.isPresent() && acknowledgedRequestId < 0)) throw new IllegalArgumentException("invalid ChemMaster state");
  ReagentUnits.validateCents(inputCapacityCents, "inputCapacityCents"); ReagentUnits.validateCents(inputVolumeCents, "inputVolumeCents");
  if (inputVolumeCents > inputCapacityCents || (!open && (loadedItemName.isPresent() || !inputReagents.isEmpty())) || (open && closeReason.isPresent())) throw new IllegalArgumentException("inconsistent ChemMaster state");
  inputReagents = List.copyOf(inputReagents); bufferReagents = List.copyOf(bufferReagents);
  if (validateRows(inputReagents) != inputVolumeCents) throw new IllegalArgumentException("input rows do not match displayed volume");
  validateRows(bufferReagents);
 }
 private static long validateRows(List<ReagentRow> rows) {
  Set<ResourceKey<ReagentData>> keys = new HashSet<>(); List<Long> amounts = new ArrayList<>();
  for (ReagentRow row : rows) { if (!keys.add(row.reagent())) throw new IllegalArgumentException("duplicate reagent row"); amounts.add(row.amountCents()); }
  return ReagentUnits.total(amounts);
 }
}
