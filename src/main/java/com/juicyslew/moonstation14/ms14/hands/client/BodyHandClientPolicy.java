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
    // Packet replies have no connection nonce. Never reuse a correlation ID, even after logout.
    private static final AtomicLong QUERY_IDS = new AtomicLong();
    private static final AtomicLong ACTION_IDS = new AtomicLong();
    private UUID account, body;
    private long epoch, querySequence, actionSequence, pendingQuery, pendingAction, pendingBlockUse, lastQueryTick = Long.MIN_VALUE;
    private long pendingQueryEpoch;
    private long sentActionTick;
    private long sentBlockUseTick;
    private long sentQueryTick;
    private BodyHandStateSnapshot snapshot;
    private BodyHandStateSnapshot presentationSnapshot;
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
        snapshot = incoming;
        refreshRequired = false;
        presentationSnapshot = incoming;
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
            var cell = new GridGeometry.Cell(x, y);
            return occupancy.place(token, ItemFootprints.forId(id), cell,
                    GridGeometry.Rotation.values()[rotation / 90]).accepted() ? cell : null;
        }
    }

    /** Validates an explicitly nominated hand from the settled snapshot, independent of active-hand projection. */
    static boolean nominatedGridStoreEligible(BodyHandStateSnapshot.Hand hand, String token,
                                               String itemId, int count) {
        if (hand == null || token == null || !token.equals(hand.token()) || itemId == null
                || !itemId.equals(hand.itemId()) || count != hand.count() || count <= 0 || count > 64
                || hand.pouch() || "moonstation14:bag".equals(itemId) || "moonstation14:belt".equals(itemId))
            return false;
        var id = ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR;
    }

    /** Explicit grid-child TAKE nomination is independent of the active-hand projection. */
    static boolean nominatedGridTakeEligible(BodyHandStateSnapshot.Hand destination,
            BodyHandStateSnapshot.GridEntry nominatedChild, BodyHandStateSnapshot.GridEntry snapshotChild,
            String heldHostHandId) {
        return destination != null && destination.token() == null && nominatedChild != null
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
                        "bag".equals(kind) ? 7 : 8, "bag".equals(kind) ? 4 : 2, entries);
            } catch (IllegalArgumentException malformedLegacyContents) {
                return null;
            }
        }
        var occupancy = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(host.width(), host.height()));
        for (var entry : host.entries()) {
            var id = ResourceLocation.tryParse(entry.itemId());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
            var result = occupancy.place(entry.token(), ItemFootprints.forId(id),
                    new GridGeometry.Cell(entry.x(), entry.y()), GridGeometry.Rotation.values()[entry.rotation() / 90]);
            if (!result.accepted()) return null;
            occupancy = result.snapshot();
        }
        boolean supported = sourceHand.token() != null && sourceHand.count() <= 64 && !sourceHand.pouch()
                && !"moonstation14:belt".equals(sourceHand.itemId()) && !"moonstation14:bag".equals(sourceHand.itemId());
        return new GridView(host, occupancy, supported, sourceHand.token() == null);
    }

    /** Resolve by exact held token, not by item ID or whichever hand is active. */
    public GridView heldGrid(String token, boolean presentation) {
        var source = presentation ? presentationSnapshot : snapshot;
        if (!inputEnabled || source == null || !presentation && (pendingQuery != 0 || pendingAction != 0)
                || source.hands().size() != 2 || token == null) return null;
        var hosts = source.hands().stream().filter(h -> token.equals(h.token())
                && h.count() == 1 && ("moonstation14:bag".equals(h.itemId()) || "moonstation14:belt".equals(h.itemId()))).toList();
        if (hosts.size() != 1) return null;
        var host = hosts.getFirst();
        var other = source.hands().get(0) == host ? source.hands().get(1) : source.hands().get(0);
        var result = gridView(source, "moonstation14:bag".equals(host.itemId()) ? "bag" : "belt",
                token, host.childToken(), host.childItemId(), host.childCount(), other);
        return presentation && (snapshot != source || pendingQuery != 0 || pendingAction != 0) ? disable(result) : result;
    }

    public record EquipmentView(BodyHandStateSnapshot.EquipmentSlot slot, boolean canEquip,
                                boolean canUnequip, boolean canStore, boolean canTake) {
        public boolean allows(BodyHandActionRequest.Action action) {
            return switch (action) {
                case EQUIP_BELT, EQUIP_BACK -> canEquip;
                case UNEQUIP_BELT, UNEQUIP_BACK -> canUnequip;
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
        boolean emptyHand = active.token() == null;
        boolean emptySlot = slot.token() == null;
        boolean matching = ("belt".equals(id) ? "moonstation14:belt" : "moonstation14:bag").equals(active.itemId());
        // Snapshot summaries cannot prove stack components; the server still validates child admission.
        boolean supported = active.token() != null && active.count() <= 64 && !active.pouch()
                && !"moonstation14:belt".equals(active.itemId()) && !"moonstation14:bag".equals(active.itemId());
        boolean gridHost = source.grids().stream().anyMatch(grid -> grid.hostToken().equals(slot.token()));
        return new EquipmentView(slot, emptySlot && matching && active.count() == 1,
                !emptySlot && emptyHand, !gridHost && !emptySlot && slot.childToken() == null && supported,
                !gridHost && !emptySlot && slot.childToken() != null && emptyHand);
    }

    public InventoryView inventoryView() {
        if (!inputEnabled || pendingQuery != 0 || pendingAction != 0 || snapshot == null
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
        return snapshot == source && pendingQuery == 0 && pendingAction == 0 ? view : disabled(view);
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
                || (pendingAction != 0 || pendingQuery != 0) && !selectionPresentation;
    }
    public record PouchView(BodyHandStateSnapshot.Hand pouch, boolean canInsert, boolean canExtract) { }

    /** Only a correlated, settled two-hand state can advertise pouch contents or controls. */
    public PouchView pouchView() {
        if (!inputEnabled || snapshot == null || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0
                || snapshot.hands().size() != 2) return null;
        BodyHandStateSnapshot.Hand pouch = null, other = null;
        for (var hand : snapshot.hands()) {
            if (hand.pouch()) {
                if (pouch != null) return null;
                pouch = hand;
            } else other = hand;
        }
        if (pouch == null || other == null || pouch.token() == null) return null;
        return new PouchView(pouch, pouch.childToken() == null && other.token() != null,
                pouch.childToken() != null && other.token() == null);
    }
    public String feedback() { return feedback; }

    /** Presentation only: no candidate was nominated, so no request reached the server. */
    public void noItemUnderCursor(long tick) {
        if (inputEnabled && snapshot != null && pendingAction == 0 && pendingQuery == 0) {
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
        if (!inputEnabled || snapshot == null || pendingQuery != 0 || pendingAction != 0
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

    /** Allocate a server action-gate ID for block use without opening an action-result barrier. */
    public long blockUseSequence(long tick) {
        if (!inputEnabled || epoch == 0 || snapshot == null || pendingQuery != 0 || pendingAction != 0 || pendingBlockUse != 0
                || actionSequence == Long.MAX_VALUE || snapshot.hands().stream()
                .noneMatch(hand -> hand.id().equals(snapshot.activeHand()))) return 0;
        long sequence = next(ACTION_IDS);
        if (sequence == Long.MAX_VALUE) return 0;
        actionSequence = sequence;
        pendingBlockUse = sequence;
        sentBlockUseTick = tick;
        return sequence;
    }

    public boolean blockUseResult(BodyBlockUseResult incoming) {
        if (!inputEnabled || pendingBlockUse == 0 || incoming.sequence() != pendingBlockUse || incoming.epoch() != epoch) return false;
        pendingBlockUse = 0;
        if (incoming.outcome() == BodyBlockUseResult.Outcome.PLACED
                || incoming.outcome() == BodyBlockUseResult.Outcome.RECOVERY_REQUIRED
                || incoming.outcome() == BodyBlockUseResult.Outcome.DENIED) {
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
        // A grenade visible in an ordinary snapshot may be unprimed. Only a correlated local
        // PRIME action can justify buffering M until its result and authoritative refresh.
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
        if (snapshot == null || sequence == 0) return;
        var active = snapshot.hands().stream().filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null || active.token() == null || !"moonstation14:grenade".equals(active.itemId())) return;
        primeAction = sequence; primeAccount = account; primeBody = body; primeEpoch = epoch;
        primeHand = active.id(); primeToken = active.token(); primeRevision = snapshot.revision();
    }
    public boolean takeReadyThrow(long tick) {
        if (throwIntent == null) return false;
        if (tick >= throwIntent.expires) { clearThrowIntent(); return false; }
        if (!primeSucceeded || pendingQuery != 0 || snapshot == null
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
            presentationSnapshot = null;
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
