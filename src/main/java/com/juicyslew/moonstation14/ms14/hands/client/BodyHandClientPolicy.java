package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Main-thread client correlation gate. Only a matching full query reply can enable an action. */
public final class BodyHandClientPolicy {
    private static final long REQUEST_TIMEOUT_TICKS = 80;
    /** Production admission predicate for routing transparent pointer-canvas clicks into body actions. */
    public static boolean allowPointerWorldAction(boolean pointerCanvas, boolean insideAnyUi, boolean textFocused) {
        return pointerCanvas && !insideAnyUi && !textFocused;
    }
    /** Global body mouse actions are only routed with no pointer Screen installed. */
    public static boolean routeGlobalMouseActions(boolean pointerCanvas) { return !pointerCanvas; }
    /** A pull reservation occupies a hand but is not item metadata. */
    public static boolean handAvailableForItemAction(BodyHandStateSnapshot.Hand hand) {
        return hand != null && hand.pullTarget() == null;
    }
    // Packet replies have no connection nonce. Never reuse a correlation ID, even after logout.
    private static final AtomicLong QUERY_IDS = new AtomicLong();
    private static final AtomicLong ACTION_IDS = new AtomicLong();
    private UUID account, body;
    private long epoch, querySequence, actionSequence, pendingQuery, pendingAction, pendingBlockUse, lastQueryTick = Long.MIN_VALUE;
    private long pendingQueryEpoch;
    private long sentActionTick;
    private long sentBlockUseTick, sentQueryTick;
    private BodyHandStateSnapshot snapshot;
    private BodyHandStateSnapshot presentationSnapshot;
    // Read-only last confirmed state is retained for input nomination while proof is refreshed.
    private BodyHandStateSnapshot lastConfirmedSnapshot;
    private long pushedRevision = -1;
    private String feedback;
    private long localFeedbackUntil;
    private boolean inputEnabled;
    private boolean refreshRequired;
    private boolean selectionPresentation;
    private boolean pendingSelection;
    private ThrowIntent throwIntent;
    private long primeAction;
    private boolean primeSucceeded;
    private record ThrowIntent(UUID account, UUID body, long epoch, String hand, String token,
                               long revision, long expires) { }

    public void reset() {
        suspend();
        querySequence = actionSequence = 0;
    }

    /** Drop all proof and outstanding correlations without rewinding this connection's sequence history. */
    public void suspend() {
        account = body = null;
        epoch = pendingQuery = pendingAction = pendingBlockUse = pendingQueryEpoch = 0;
        refreshRequired = false;
        lastQueryTick = Long.MIN_VALUE;
        snapshot = null;
        presentationSnapshot = null;
        lastConfirmedSnapshot = null;
        pushedRevision = -1;
        feedback = null;
        localFeedbackUntil = 0;
        inputEnabled = false;
        selectionPresentation = pendingSelection = false;
        throwIntent = null;
        primeAction = 0;
        primeSucceeded = false;
    }

    public void bind(UUID accountId, UUID bodyId, long committedEpoch) {
        if (accountId == null || bodyId == null || committedEpoch <= 0) { suspend(); return; }
        if (!accountId.equals(account) || !bodyId.equals(body) || committedEpoch != epoch) {
            suspend();
            account = accountId;
            body = bodyId;
            epoch = committedEpoch;
        }
        inputEnabled = true;
    }

    private static long next(AtomicLong ids) {
        return ids.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
    }

    public long query(long tick) {
        if (!inputEnabled || epoch == 0 || pendingAction != 0 || pendingBlockUse != 0 || pendingQuery != 0 || querySequence == Long.MAX_VALUE
                || (!refreshRequired && lastQueryTick != Long.MIN_VALUE && tick - lastQueryTick < 40)) return 0;
        long sequence = next(QUERY_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        lastQueryTick = tick;
        pendingQueryEpoch = queryEpoch();
        pendingQuery = querySequence = sequence;
        sentQueryTick = tick;
        return pendingQuery;
    }

    /** Invalidate any pre-action proof/correlation and bypass the ordinary query throttle. */
    public long forceFreshQuery(long tick) {
        if (!inputEnabled || epoch == 0 || pendingAction != 0 || querySequence == Long.MAX_VALUE) return 0;
        snapshot = null;
        pendingQuery = 0;
        pendingQueryEpoch = 0;
        lastQueryTick = Long.MIN_VALUE;
        return query(tick);
    }

    public long queryEpoch() { return snapshot == null ? 0 : epoch; }

    public boolean accept(BodyHandStateSnapshot incoming) {
        if (incoming.push()) {
            if (!inputEnabled || incoming.reason() != BodyHandStateSnapshot.Reason.OK
                    || !account.equals(incoming.accountId()) || !body.equals(incoming.bodyId())
                    || epoch != incoming.epoch() || snapshot != null && incoming.revision() < snapshot.revision()) return false;
            snapshot = incoming;
            presentationSnapshot = incoming;
            lastConfirmedSnapshot = incoming;
            pushedRevision = incoming.revision();
            refreshRequired = false;
            return true;
        }
        if (!inputEnabled || epoch == 0 || incoming.sequence() != pendingQuery || pendingQuery == 0
                || incoming.requestedEpoch() != pendingQueryEpoch) return false;
        pendingQuery = 0;
        if (incoming.reason() != BodyHandStateSnapshot.Reason.OK
                || !account.equals(incoming.accountId()) || !body.equals(incoming.bodyId())
                || epoch != incoming.epoch()) {
            snapshot = null;
            refreshRequired = true;
            lastQueryTick = Long.MIN_VALUE;
            return false;
        }
        if (incoming.revision() < pushedRevision) {
            refreshRequired = true;
            return false;
        }
        snapshot = incoming;
        refreshRequired = false;
        presentationSnapshot = incoming;
        lastConfirmedSnapshot = incoming;
        if (throwIntent != null) {
            var active = incoming.hands().stream().filter(hand -> hand.id().equals(incoming.activeHand())).findFirst().orElse(null);
            if (incoming.epoch() != throwIntent.epoch || !account.equals(throwIntent.account)
                    || !body.equals(throwIntent.body) || active == null
                    || !active.id().equals(throwIntent.hand) || !java.util.Objects.equals(active.token(), throwIntent.token)
                    || incoming.revision() <= throwIntent.revision) {
                clearThrowIntent();
            }
        }
        pendingAction = 0;
        pendingSelection = false;
        selectionPresentation = false;
        return true;
    }

    public BodyHandStateSnapshot snapshot() { return snapshot; }
    public boolean handQueryPending() { return pendingQuery != 0 && pendingAction == 0 && pendingBlockUse == 0; }
    public BodyHandStateSnapshot lastConfirmedSnapshot() { return inputEnabled ? lastConfirmedSnapshot : null; }
    /** Confirmed read-only authority state; unlike inventoryView this remains usable while input is barred. */
    public BodyHandStateSnapshot confirmedSnapshot() {
        return inputEnabled && snapshot != null && snapshot.reason() == BodyHandStateSnapshot.Reason.OK
                && snapshot.epoch() == epoch ? snapshot : null;
    }
    /** Screen presentation requires a settled, validated pair; never display stale state during a query. */
    public record InventoryView(BodyHandStateSnapshot.Hand first, BodyHandStateSnapshot.Hand second,
                                 String activeHand, EquipmentView belt, EquipmentView back,
                                 GridView beltGrid, GridView backGrid) { }

    /** A display-only reconstruction from one full host summary, never from local inventory. */
    public record GridView(BodyHandStateSnapshot.GridHost host, GridGeometry.Snapshot<String> occupancy,
                           boolean canStore, boolean canTake) {
        public BodyHandStateSnapshot.GridEntry entryAt(int x, int y) {
            var cell = new GridGeometry.Cell(x, y);
            for (var entry : host.entries()) {
                var placement = occupancy.placement(entry.token());
                if (placement != null && placement.occupiedCells().contains(cell)) return entry;
            }
            return null;
        }

        public GridGeometry.Cell proposed(String itemId, String token, int rotation, int x, int y) {
            if (!canStore || token == null || itemId == null || rotation < 0 || rotation > 270 || rotation % 90 != 0)
                return null;
            var id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
            if ("moonstation14".equals(id.getNamespace()) && !ItemFootprints.clientHasProfile(id)) return null;
            var cell = new GridGeometry.Cell(x, y);
            return occupancy.place(token, ItemFootprints.clientForId(id), cell,
                    GridGeometry.Rotation.values()[rotation / 90]).accepted() ? cell : null;
        }
    }

    /** Validates an explicitly nominated hand from the settled snapshot, independent of active-hand projection. */
    static boolean nominatedGridStoreEligible(BodyHandStateSnapshot.Hand hand, String token,
                                               String itemId, int count) {
        if (hand == null || token == null || !token.equals(hand.token()) || itemId == null
                || !itemId.equals(hand.itemId()) || count != hand.count() || count <= 0 || count > 64
                || hand.pullTarget() != null || hand.pouch() || "moonstation14:bag".equals(itemId) || "moonstation14:belt".equals(itemId)
                || "moonstation14:pill_canister".equals(itemId))
            return false;
        var id = ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR;
    }

    /** Explicit grid-child TAKE nomination is independent of the active-hand projection. */
    static boolean nominatedGridTakeEligible(BodyHandStateSnapshot.Hand destination,
            BodyHandStateSnapshot.GridEntry nominatedChild, BodyHandStateSnapshot.GridEntry snapshotChild,
            String heldHostHandId) {
        return destination != null && destination.pullTarget() == null && destination.token() == null && nominatedChild != null
                && nominatedChild.equals(snapshotChild)
                && (heldHostHandId == null || !heldHostHandId.equals(destination.id()));
    }

    private GridView gridView(BodyHandStateSnapshot source, String kind, BodyHandStateSnapshot.EquipmentSlot slot, BodyHandStateSnapshot.Hand active) {
        if (slot == null || slot.token() == null)
            return null;
        return gridView(source, kind, slot.token(), slot.childToken(), slot.childItemId(), slot.childCount(), active);
    }

    private GridView gridView(BodyHandStateSnapshot source, String kind, String hostToken,
                              String childToken, String childItemId, int childCount, BodyHandStateSnapshot.Hand sourceHand) {
        var matchingHosts = source.grids().stream().filter(g -> g.hostToken().equals(hostToken)).toList();
        if (matchingHosts.size() > 1) return null;
        BodyHandStateSnapshot.GridHost host;
        if (matchingHosts.size() == 1) {
            host = matchingHosts.getFirst();
            if (!host.kind().equals(kind)) return null;
        } else {
            // Legacy one-cell contents are an authoritative placement summary, not proof of
            // arbitrary grid ownership. Use them only as a display-time migration proposal.
            var entries = new java.util.ArrayList<BodyHandStateSnapshot.GridEntry>();
            if (childToken != null) {
                var id = ResourceLocation.tryParse(childItemId);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
                entries.add(new BodyHandStateSnapshot.GridEntry(childToken, childItemId,
                        childCount, 0, 0, 0));
            }
            try {
                host = new BodyHandStateSnapshot.GridHost(hostToken, kind,
                         "bag".equals(kind) ? 7 : "pill".equals(kind) ? 5 : 8, "bag".equals(kind) ? 4 : 2, entries);
            } catch (IllegalArgumentException malformedLegacyContents) {
                return null;
            }
        }
        var occupancy = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(host.width(), host.height()));
        for (var entry : host.entries()) {
            var id = ResourceLocation.tryParse(entry.itemId());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
            var result = occupancy.place(entry.token(), ItemFootprints.clientForId(id),
                    new GridGeometry.Cell(entry.x(), entry.y()), GridGeometry.Rotation.values()[entry.rotation() / 90]);
            if (!result.accepted()) return null;
            occupancy = result.snapshot();
        }
        boolean supported = handAvailableForItemAction(sourceHand) && sourceHand.token() != null && sourceHand.count() <= 64 && !sourceHand.pouch()
                && !"moonstation14:belt".equals(sourceHand.itemId()) && !"moonstation14:bag".equals(sourceHand.itemId());
        return new GridView(host, occupancy, supported, sourceHand.token() == null && handAvailableForItemAction(sourceHand));
    }

    /** Resolve by exact held token, not by item ID or whichever hand is active. */
    public GridView heldGrid(String token, boolean presentation) {
        var source = presentation ? presentationSnapshot : snapshot;
        if (!inputEnabled || source == null || !presentation && (pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0)
                || source.hands().size() != 2 || token == null) return null;
        var hosts = source.hands().stream().filter(h -> token.equals(h.token())
                && h.count() == 1 && ("moonstation14:bag".equals(h.itemId()) || "moonstation14:belt".equals(h.itemId())
                || "moonstation14:pill_canister".equals(h.itemId()))).toList();
        if (hosts.size() != 1) return null;
        var host = hosts.getFirst();
        var other = source.hands().get(0) == host ? source.hands().get(1) : source.hands().get(0);
        var result = gridView(source, "moonstation14:bag".equals(host.itemId()) ? "bag"
                        : "moonstation14:pill_canister".equals(host.itemId()) ? "pill" : "belt",
                token, host.childToken(), host.childItemId(), host.childCount(), other);
        return presentation && (snapshot != source || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0) ? disable(result) : result;
    }

    public record EquipmentView(BodyHandStateSnapshot.EquipmentSlot slot, boolean canEquip,
                                boolean canUnequip, boolean canStore, boolean canTake) {
        public boolean allows(BodyHandActionRequest.Action action) {
            return switch (action) {
                case EQUIP_BELT, EQUIP_BACK, EQUIP_SLOT -> canEquip;
                case UNEQUIP_BELT, UNEQUIP_BACK, UNEQUIP_SLOT -> canUnequip;
                case STORE_BELT, STORE_BACK -> canStore;
                case TAKE_BELT, TAKE_BACK -> canTake;
                default -> false;
            };
        }
    }

    private EquipmentView equipmentView(BodyHandStateSnapshot source, String id, BodyHandStateSnapshot.Hand active) {
        var slots = source.equipment().stream().filter(slot -> slot.id().equals(id)).toList();
        if (slots.size() != 1) return null;
        var slot = slots.getFirst();
        boolean emptyHand = active.token() == null && active.pullTarget() == null;
        boolean emptySlot = slot.token() == null;
        boolean matching = equipmentFits(source, id, active.itemId());
        boolean validCount = active.itemId() != null && equipmentCountFits(source, id, active.itemId(), active.count());
        // Snapshot summaries cannot prove stack components; the server still validates child admission.
        boolean supported = active.pullTarget() == null && active.token() != null && active.count() <= 64 && !active.pouch()
                && !"moonstation14:belt".equals(active.itemId()) && !"moonstation14:bag".equals(active.itemId());
        boolean gridHost = source.grids().stream().anyMatch(grid -> grid.hostToken().equals(slot.token()));
        return new EquipmentView(slot, emptySlot && matching && validCount,
                !emptySlot && emptyHand, !gridHost && !emptySlot && slot.childToken() == null && supported,
                !gridHost && !emptySlot && slot.childToken() != null && emptyHand);
    }

    static boolean equipmentCountFits(BodyHandStateSnapshot snapshot, String slotId, String itemId, int count) {
        var itemIdValue = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (itemIdValue == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemIdValue)) return false;
        var templateId = snapshot.templateId();
        var template = templateId == null ? null
                : com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog().get(templateId);
        var slot = template == null ? null : template.slot(slotId).orElse(null);
        if (slot == null) return count == 1;
        boolean pocket = slot.flags().contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET);
        int nativeCap = new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemIdValue)).getMaxStackSize();
        var medical = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(true).get(itemIdValue);
        if (medical != null) nativeCap = Math.min(nativeCap,
                medical.stack().map(com.juicyslew.moonstation14.ms14.medical.MedicalItemData.Stack::maxCount).orElse(nativeCap));
        return equipmentCountFits(count, nativeCap, pocket);
    }

    static boolean equipmentCountFits(int count, int resolvedNativeCap, boolean pocket) {
        return pocket ? count > 0 && count <= resolvedNativeCap : count == 1;
    }

    private static boolean equipmentFits(BodyHandStateSnapshot snapshot, String slotId, String itemId) {
        if (itemId == null) return false;
        if ("belt".equals(slotId)) return "moonstation14:belt".equals(itemId);
        if ("back".equals(slotId)) return "moonstation14:bag".equals(itemId);
        var templateId = snapshot.templateId();
        var itemResource = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (templateId == null || itemResource == null) return false;
        var template = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog().get(templateId);
        var slot = template == null ? null : template.slot(slotId).orElse(null);
        var item = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog().get(itemResource);
        if (slot == null) return false;
        var occupied = snapshot.equipment().stream().filter(value -> value.token() != null)
                .map(BodyHandStateSnapshot.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
        if (!slot.dependsOn().stream().allMatch(occupied::contains)) return false;
        boolean pocket = slot.flags().contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET);
        if (item == null) {
            if (pocket) {
                var medical = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(true).get(itemResource);
                if (medical != null) return "Tiny".equals(medical.item().size()) || "Small".equals(medical.item().size());
                var profile = com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints.get(
                        com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints.clientCatalog(), itemResource);
                var size = profile == null ? null : profile.itemSize().map(
                        com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile.ItemSize::name).orElse(null);
                return "Tiny".equals(size) || "Small".equals(size);
            }
            return false;
        }
        if (pocket)
            return "Tiny".equals(item.item().size()) || "Small".equals(item.item().size());
        if (item.clothing().isEmpty()) return false;
        try {
            var flags = slot.flags();
            if (java.util.Collections.disjoint(item.clothing().orElseThrow().slotFlags(), flags)
                    || slot.blacklist().stream().anyMatch(item.tags()::contains)) return false;
            if (flags.contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.SUITSTORAGE)) {
                var outer = snapshot.equipment().stream().filter(value -> "outerClothing".equals(value.id()) && value.itemId() != null)
                        .map(value -> com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog().get(
                                net.minecraft.resources.ResourceLocation.tryParse(value.itemId())))
                        .anyMatch(value -> value != null && value.allowSuitStorage().map(
                                com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.AllowSuitStorage::enabled).orElse(false));
                return outer && slot.allowSuitStorage();
            }
            return true;
        } catch (IllegalArgumentException unsupported) { return false; }
    }

    public static boolean equipmentFitsSlot(BodyHandStateSnapshot snapshot,String slotId,String itemId) {
        return snapshot!=null&&equipmentFits(snapshot,slotId,itemId);
    }

    public InventoryView inventoryView() {
        if (!inputEnabled || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0 || snapshot == null
                || snapshot.hands().size() != 2) return null;
        var first = snapshot.hands().get(0);
        var second = snapshot.hands().get(1);
        if (first.id().equals(second.id()) || !snapshot.activeHand().equals(first.id())
                && !snapshot.activeHand().equals(second.id())) return null;
        var active = first.id().equals(snapshot.activeHand()) ? first : second;
        var belt = equipmentView(snapshot, "belt", active);
        var back = equipmentView(snapshot, "back", active);
        return new InventoryView(first, second, snapshot.activeHand(), belt, back,
                gridView(snapshot, "belt", belt == null ? null : belt.slot(), active),
                gridView(snapshot, "bag", back == null ? null : back.slot(), active));
    }
    /** Last confirmed display data; controls are disabled until current proof settles. */
    public InventoryView presentationView() {
        if (!inputEnabled || presentationSnapshot == null || presentationSnapshot.hands().size() != 2) return null;
        var source = presentationSnapshot;
        var first = source.hands().get(0);
        var second = source.hands().get(1);
        if (first.id().equals(second.id()) || !source.activeHand().equals(first.id()) && !source.activeHand().equals(second.id())) return null;
        var active = first.id().equals(source.activeHand()) ? first : second;
        var belt = equipmentView(source, "belt", active); var back = equipmentView(source, "back", active);
        var view = new InventoryView(first, second, source.activeHand(), belt, back,
                gridView(source, "belt", belt == null ? null : belt.slot(), active),
                gridView(source, "bag", back == null ? null : back.slot(), active));
        return snapshot == source && pendingQuery == 0 && pendingAction == 0 && pendingBlockUse == 0 ? view : disabled(view);
    }
    private InventoryView disabled(InventoryView view) {
        EquipmentView belt = disable(view.belt()), back = disable(view.back());
        return new InventoryView(view.first(), view.second(), view.activeHand(), belt, back,
                disable(view.beltGrid()), disable(view.backGrid()));
    }
    private EquipmentView disable(EquipmentView v) { return v == null ? null : new EquipmentView(v.slot(), false, false, false, false); }
    private GridView disable(GridView v) { return v == null ? null : new GridView(v.host(), v.occupancy(), false, false); }
    /** Rendering must not expose the last authoritative state while an action is unresolved. */
    public BodyHandStateSnapshot hudSnapshot() { return inputEnabled ? presentationSnapshot : null; }
    /** Snapshot-only HUD presentation; unlike controls this remains available while proof is pending. */
    public BodyHandStateSnapshot presentationSnapshot() { return inputEnabled ? presentationSnapshot : null; }
    /** Presentation chrome changes only when no confirmed display exists or an action is unresolved. */
    public boolean presentationPending() {
        return !inputEnabled || presentationSnapshot == null
                || (pendingAction != 0 || pendingQuery != 0 || pendingBlockUse != 0) && !selectionPresentation;
    }
    public String feedback() { return feedback; }

    /** Presentation only: no candidate was nominated, so no request reached the server. */
    public void noItemUnderCursor(long tick) {
        if (inputEnabled && snapshot != null && pendingAction == 0 && pendingQuery == 0 && pendingBlockUse == 0) {
            feedback = "Hands: no item under cursor";
            localFeedbackUntil = tick + 60;
        }
    }

    public record HudPosition(int x, int y) { }

    /** The body vital rows end near y=48. Keep hands above chat and away from the hotbar. */
    public static HudPosition handHudPosition(int width, int height, int textWidth, int lineHeight) {
        if (width < 24 || height < 100 || lineHeight < 1) return null;
        int x = 6;
        int y = 54;
        if (y + lineHeight + 2 > height - 36) return null;
        return new HudPosition(Math.max(3, Math.min(x, width - textWidth - 3)), y);
    }

    /** No GUI overlay, menu, ghost, or uncommitted camera may show the aim cue. */
    public static boolean showAimCue(boolean committedCharacterCamera, boolean guiVisible, boolean screenClosed) {
        return committedCharacterCamera && guiVisible && screenClosed;
    }

    public long action(long tick) {
        return action(tick, false);
    }

    /** A SELECT_HAND may preserve presentation only; it never preserves action proof. */
    public long selectionAction(long tick) { return action(tick, true); }

    private long action(long tick, boolean selection) {
        if (!inputEnabled || snapshot == null || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0
                || actionSequence == Long.MAX_VALUE) return 0;
        long sequence = next(ACTION_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        pendingAction = actionSequence = sequence;
        selectionPresentation = selection;
        pendingSelection = selection;
        sentActionTick = tick;
        feedback = null;
        localFeedbackUntil = 0;
        return pendingAction;
    }

    /** A timed commit may mutate hands without a hand-action result; retain display data, discard proof. */
    public void externalTimedCompletion() {
        if (!inputEnabled) return;
        snapshot = null;
        pendingQuery = 0;
        pendingQueryEpoch = 0;
        refreshRequired = true;
        lastQueryTick = Long.MIN_VALUE;
    }

    /** Allocate an ID from the shared server action sequence for a block-use request. */
    public long blockUseSequence(long tick) {
        if (!inputEnabled || epoch == 0 || snapshot == null || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0
                || actionSequence == Long.MAX_VALUE || snapshot.hands().stream()
                .noneMatch(hand -> hand.id().equals(snapshot.activeHand()))) return 0;
        long sequence = next(ACTION_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        actionSequence = pendingBlockUse = sequence;
        sentBlockUseTick = tick;
        return sequence;
    }

    public boolean blockUseResult(BodyBlockUseResult incoming) {
        if (!inputEnabled || pendingBlockUse == 0 || incoming.sequence() != pendingBlockUse || incoming.epoch() != epoch) return false;
        pendingBlockUse = 0;
        if (incoming.outcome() == BodyBlockUseResult.Outcome.TARGET_NOT_ADMITTED) return true;
        if (incoming.outcome() == BodyBlockUseResult.Outcome.PLACED
                || incoming.outcome() == BodyBlockUseResult.Outcome.RECOVERY_REQUIRED
                || incoming.outcome() == BodyBlockUseResult.Outcome.DENIED
                || incoming.outcome() == BodyBlockUseResult.Outcome.HAND_CHANGED
                || incoming.outcome() == BodyBlockUseResult.Outcome.WORK_STARTED) {
            snapshot = null;
            presentationSnapshot = null;
            pendingQuery = 0;
            refreshRequired = true;
            lastQueryTick = Long.MIN_VALUE;
        }
        return true;
    }

    /** True only while a correlated block-use request or its authoritative refresh is outstanding. */
    public boolean blockUseBarrier() { return pendingBlockUse != 0 || refreshRequired; }

    /** Restrict the one deferred pointer intent to an active resolved disposal construction recipe. */
    public static boolean mayDeferConstructionUse(boolean barrier, boolean planActive, String prototype,
            com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.ConstructionStep step,
            String sourceItemId) {
        return barrier && planActive && prototype != null && prototype.startsWith("moonstation14:disposal_")
                && step != null && sourceItemId != null && step.material().equals(sourceItemId);
    }

    public static boolean mayDeferConstructionUse(boolean barrier, boolean planMatchesProof, String sourceItem,
                                                   int requiredCount, String heldItem, int heldCount) {
        return barrier && planMatchesProof && sourceItem != null && sourceItem.equals(heldItem)
                && requiredCount > 0 && heldCount >= requiredCount;
    }

    public static boolean deferredPlacementStillValid(UUID body, long epoch, UUID currentBody, long currentEpoch,
            long capturedHandsRevision, long freshHandsRevision,
            String hand, String currentHand, String token, String currentToken, String itemId, String currentItemId, long planRevision,
            long currentPlanRevision, net.minecraft.core.BlockPos target, net.minecraft.core.BlockPos currentTarget,
            net.minecraft.core.Direction face, net.minecraft.core.Direction currentFace, boolean activePlan,
            boolean screenClosed) {
        return body != null && body.equals(currentBody) && epoch > 0 && epoch == currentEpoch
                && capturedHandsRevision >= 0 && freshHandsRevision >= capturedHandsRevision
                && hand != null && hand.equals(currentHand) && token != null && token.equals(currentToken)
                && itemId != null && itemId.equals(currentItemId)
                && planRevision >= 0 && planRevision == currentPlanRevision && activePlan && screenClosed
                && target != null && target.equals(currentTarget) && face != null && face == currentFace;
    }
    public boolean requestThrowAfterPrime(long tick) {
        if (!inputEnabled || tick < 0) return false;
        if (throwIntent != null) return account.equals(throwIntent.account) && body.equals(throwIntent.body)
                && epoch == throwIntent.epoch && tick < throwIntent.expires;
        // Only a correlated in-hand use can justify buffering a throw until its result and refresh.
        if (primeAction == 0 || primeAccount == null || !account.equals(primeAccount)
                || !body.equals(primeBody) || epoch != primeEpoch || primeHand == null || primeToken == null
                || pendingAction != primeAction && !primeSucceeded) return false;
        throwIntent = new ThrowIntent(primeAccount, primeBody, primeEpoch, primeHand, primeToken, primeRevision, tick + 40);
        return true;
    }

    /** Containment has no external throw trajectory: discard all buffered prime/throw intent. */
    public void containedEntry() { cancelThrowIntent(); }

    private UUID primeAccount, primeBody;
    private long primeEpoch, primeRevision;
    private String primeHand, primeToken;
    public void markPrime(long sequence) {
        markUse(sequence);
    }
    public void markUse(long sequence) {
        if (snapshot == null || sequence == 0) return;
        var active = snapshot.hands().stream().filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null || active.token() == null) return;
        primeAction = sequence; primeAccount = account; primeBody = body; primeEpoch = epoch;
        primeHand = active.id(); primeToken = active.token(); primeRevision = snapshot.revision();
    }
    public boolean takeReadyThrow(long tick) {
        if (throwIntent == null) return false;
        if (tick >= throwIntent.expires) { clearThrowIntent(); return false; }
        if (!primeSucceeded || pendingQuery != 0 || pendingBlockUse != 0 || snapshot == null
                || snapshot.revision() <= throwIntent.revision) return false;
        var active = snapshot.hands().stream().filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null || !active.id().equals(throwIntent.hand) || !java.util.Objects.equals(active.token(), throwIntent.token)) {
            clearThrowIntent(); return false;
        }
        return true;
    }
    public void throwSent() { clearThrowIntent(); }
    public void cancelThrowIntent() { clearThrowIntent(); }
    private void clearThrowIntent() { throwIntent = null; primeAction = 0; primeSucceeded = false;
        primeAccount = primeBody = null; primeEpoch = primeRevision = 0; primeHand = primeToken = null; }

    public boolean result(BodyHandActionResult incoming, long tick) {
        if (!inputEnabled || pendingAction == 0 || incoming.sequence() != pendingAction || incoming.epoch() != epoch) return false;
        pendingAction = 0;
        if (pendingSelection) {
            if (incoming.reason() == BodyHandActionResult.Reason.OK) selectionPresentation = true;
            else selectionPresentation = false;
            pendingSelection = false;
        } else selectionPresentation = false;
        if (incoming.sequence() == primeAction) {
            if (incoming.reason() == BodyHandActionResult.Reason.OK) primeSucceeded = true;
            else clearThrowIntent();
        }
        snapshot = null;
        pendingQuery = 0;
        lastQueryTick = Long.MIN_VALUE;
        feedback = switch (incoming.reason()) {
            case DENIED -> "Hands: server DENIED";
            case RECOVERY_REQUIRED -> "Hands: RECOVERY_REQUIRED";
            case OK -> null;
            default -> "Hands: " + incoming.reason();
        };
        localFeedbackUntil = feedback == null ? 0 : tick + 60;
        return true;
    }

    public void timeout(long tick) {
        if (localFeedbackUntil != 0 && tick >= localFeedbackUntil) {
            feedback = null;
            localFeedbackUntil = 0;
        }
        if (pendingAction != 0 && tick - sentActionTick >= REQUEST_TIMEOUT_TICKS) {
            if (pendingAction == primeAction) clearThrowIntent();
            pendingAction = 0;
            selectionPresentation = pendingSelection = false;
            snapshot = null;
            pendingQuery = 0;
            lastQueryTick = Long.MIN_VALUE;
            feedback = null;
            localFeedbackUntil = 0;
        }
        if (pendingBlockUse != 0 && tick - sentBlockUseTick >= REQUEST_TIMEOUT_TICKS) {
            pendingBlockUse = 0;
            snapshot = null;
            pendingQuery = 0;
            lastQueryTick = Long.MIN_VALUE;
            refreshRequired = true;
        }
        if (pendingQuery != 0 && tick - sentQueryTick >= REQUEST_TIMEOUT_TICKS) {
            pendingQuery = 0;
            snapshot = null;
            lastQueryTick = Long.MIN_VALUE;
            refreshRequired = true;
        }
    }
}

