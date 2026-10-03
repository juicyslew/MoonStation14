package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Main-thread-only read route. An absent or incompatible attachment is never created or advertised as empty. */
public final class BodyHandStateService {
    private static final Map<ServerPlayer, QueryGate> GATES = new IdentityHashMap<>();

    private BodyHandStateService() { }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) GATES.remove(actor);
    }

    /** No action before a successful full snapshot was sent for this exact connection and epoch. */
    static boolean hasIssued(ServerPlayer actor, HandActorAuthority.Snapshot authority) {
        QueryGate gate = GATES.get(actor);
        return gate != null && gate.issuedEpoch == authority.epoch() && gate.issuedBody == authority.body();
    }

    public static void requireFreshQuery(ServerPlayer actor) {
        QueryGate gate = GATES.get(actor);
        if (gate != null) gate.clearIssued();
    }

    public static BodyHandStateSnapshot query(ServerPlayer actor, BodyHandStateQuery query) {
        QueryGate gate = GATES.get(actor);
        if (gate != null) gate.clearIssued();
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        gate = GATES.computeIfAbsent(actor, ignored -> new QueryGate());
        var admission = gate.admit(query.epoch(), query.sequence(), actor.level().getGameTime());
        if (admission != null) return BodyHandStateSnapshot.rejected(query, admission);
        if (!HandActorAuthority.revalidate(actor, authority))
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.handIds().equals(authority.handIds())
                || !representable(hands, authority.body()))
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
        List<BodyHandStateSnapshot.Hand> slots;
        List<BodyHandStateSnapshot.EquipmentSlot> equipment;
        List<BodyHandStateSnapshot.GridHost> grids;
        BodyHandStateSnapshot summary;
        try {
            slots = slots(hands, authority.handIds());
            if (slots == null) return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
            equipment = equipment(hands, authority.body(), slots);
            if (equipment == null) return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
            grids = grids(hands, authority.body());
            summary = new BodyHandStateSnapshot(query.epoch(), query.sequence(), BodyHandStateSnapshot.Reason.OK,
                    actor.getUUID(), authority.body().getUUID(), authority.epoch(), hands.revision(), hands.activeHand(), slots, equipment, grids);
        } catch (RuntimeException invalidSummary) {
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
        }
        if (!HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands)
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        gate.issuedEpoch = authority.epoch();
        gate.issuedBody = authority.body();
        return summary;
    }

    /** Read-only, bounded admission for a prospective body owner. Also applies to loaded evidence at query time. */
    public static boolean representable(LiveHands hands, LivingEntity body) {
        if (hands == null || body == null || !hands.compatible(body)
                || hands.handIds().size() > HandState.MAX_HANDS) return false;
        try {
            List<BodyHandStateSnapshot.Hand> handSlots = slots(hands, hands.handIds());
            if (handSlots == null) return false;
            var equipmentSlots = equipment(hands, body, handSlots);
            if (equipmentSlots == null) return false;
            var gridHosts = grids(hands, body);
            for (String id : hands.handIds()) {
                if (hands.stackCopy(id).filter(stack -> !visibleStack(stack)).isPresent()) return false;
            }
            var worn = LiveHands.equipmentSnapshot(body);
            if (worn.isPresent()) for (var slot : worn.orElseThrow().slots()) {
                if (slot.stack().filter(stack -> !visibleStack(stack)).isPresent()) return false;
            }
            new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, java.util.UUID.randomUUID(),
                    java.util.UUID.randomUUID(), 1, hands.revision(), hands.activeHand(), handSlots, equipmentSlots, gridHosts);
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean visibleStack(ItemStack stack) {
        if ("moonstation14:pouch".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) return false;
        if (stack.has(ModDataComponents.POUCH_CONTENTS.get()) && stack.has(ModDataComponents.GRID_CONTENTS.get())) return false;
        // These components can conceal independently owned stacks; only our registered single-cell
        // hosts are modeled by the snapshot. POUCH_CONTENTS on anything else is not a host.
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.CONTAINER_LOOT)
                || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CHARGED_PROJECTILES)
                || stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.ENTITY_DATA)
                || stack.has(DataComponents.BUCKET_ENTITY_DATA) || stack.has(DataComponents.BEES)
                || stack.has(DataComponents.FOOD) && stack.get(DataComponents.FOOD).usingConvertsTo().isPresent())
            return false;
        if (stack.is(ModItems.BAG) || stack.is(ModItems.BELT)) {
            if (stack.has(ModDataComponents.GRID_CONTENTS.get())) return GridContents.read(stack).isPresent();
            if (stack.has(ModDataComponents.POUCH_CONTENTS.get())) return PouchContents.read(stack).isPresent();
            return PouchContents.read(stack).isPresent();
        }
        return !stack.has(ModDataComponents.POUCH_CONTENTS.get())
                && !stack.has(ModDataComponents.GRID_CONTENTS.get());
    }

    /** Only the current body attachment supplies pouch contents; never a request payload. */
    static List<BodyHandStateSnapshot.Hand> slots(LiveHands hands, List<String> handIds) {
        var slots = new ArrayList<BodyHandStateSnapshot.Hand>();
        for (String id : handIds) {
            var token = hands.token(id);
            var stack = hands.stackCopy(id);
            if (token.isPresent() != stack.isPresent())
                return null;
            if (token.isEmpty()) {
                slots.add(new BodyHandStateSnapshot.Hand(id, null, null, 0));
            } else {
                var item = stack.orElseThrow();
                String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                if (itemId.isBlank() || itemId.length() > 256 || item.getCount() > 99)
                    return null;
                if (item.is(ModItems.BAG) || item.is(ModItems.BELT)) {
                    if (item.has(ModDataComponents.GRID_CONTENTS.get())) {
                        if (GridContents.read(item).isEmpty()) return null;
                        slots.add(new BodyHandStateSnapshot.Hand(id, token.orElseThrow(), itemId, item.getCount()));
                    } else {
                        var contents = PouchContents.read(item);
                        if (contents.isEmpty()) return null;
                        var child = contents.orElseThrow().child();
                        if (child.isPresent()) {
                            var value = child.orElseThrow();
                            String childId = BuiltInRegistries.ITEM.getKey(value.stack().getItem()).toString();
                            slots.add(new BodyHandStateSnapshot.Hand(id, token.orElseThrow(), itemId, item.getCount(),
                                    false, value.token(), childId, value.stack().getCount()));
                        } else slots.add(new BodyHandStateSnapshot.Hand(id, token.orElseThrow(), itemId, item.getCount()));
                    }
                } else slots.add(new BodyHandStateSnapshot.Hand(id, token.orElseThrow(), itemId, item.getCount()));
            }
        }
        var tokens = new HashSet<String>();
        for (var slot : slots) if (slot.token() != null && !tokens.add(slot.token())) return null;
        for (var slot : slots) if (slot.childToken() != null && !tokens.add(slot.childToken())) return null;
        return slots;
    }

    /** Null means saved equipment is incompatible or any ownership token is ambiguous. */
    static List<BodyHandStateSnapshot.EquipmentSlot> equipment(LiveHands hands, LivingEntity body,
                                                                 List<BodyHandStateSnapshot.Hand> handsSlots) {
        var snapshot = LiveHands.equipmentSnapshot(body);
        Set<String> tokens = new HashSet<>();
        for (var hand : handsSlots) {
            if (hand.token() != null && !tokens.add(hand.token())) return null;
            if (hand.childToken() != null && !tokens.add(hand.childToken())) return null;
            // Grid children are validated for conservation even though the old summary cannot represent them.
            var stack = hands.stackCopy(hand.id());
            if (stack.isPresent() && (stack.orElseThrow().is(ModItems.BAG) || stack.orElseThrow().is(ModItems.BELT))) {
                if (stack.orElseThrow().has(ModDataComponents.GRID_CONTENTS.get())
                        && GridContents.read(stack.orElseThrow()).isEmpty()) return null;
                // Grid child identity is admitted and emitted by grids(); never count it twice here.
            }
        }
        // compatible() already rejects a persisted equipment layout without a matching prototype.
        if (snapshot.isEmpty()) return hands.compatible(body) ? List.of() : null;
        var result = new ArrayList<BodyHandStateSnapshot.EquipmentSlot>();
        for (var slot : snapshot.orElseThrow().slots()) {
            if (slot.token().isPresent() != slot.stack().isPresent()) return null;
            if (slot.token().isEmpty()) {
                result.add(new BodyHandStateSnapshot.EquipmentSlot(slot.id(), null, null, 0, null, null, 0));
                continue;
            }
            var stack = slot.stack().orElseThrow();
            if (!("belt".equals(slot.id()) && stack.is(ModItems.BELT)
                    || "back".equals(slot.id()) && stack.is(ModItems.BAG))) return null;
            if (!tokens.add(slot.token().orElseThrow())) return null;
            String childToken = null, childId = null;
            int childCount = 0;
            if (stack.has(ModDataComponents.GRID_CONTENTS.get())) {
                if (GridContents.read(stack).isEmpty()) return null;
            } else {
                var legacy = PouchContents.read(stack);
                if (legacy.isEmpty()) return null;
                var childContents = legacy.orElseThrow().child();
                if (childContents.isPresent()) {
                    var child = childContents.orElseThrow();
                    childToken = child.token();
                    childId = BuiltInRegistries.ITEM.getKey(child.stack().getItem()).toString();
                    childCount = child.stack().getCount();
                    if (!tokens.add(childToken)) return null;
                }
            }
            try {
                result.add(new BodyHandStateSnapshot.EquipmentSlot(slot.id(), slot.token().orElseThrow(),
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
                        childToken, childId, childCount));
            } catch (IllegalArgumentException invalid) { return null; }
        }
        return result;
    }

    private static List<BodyHandStateSnapshot.GridHost> grids(LiveHands hands, LivingEntity body) {
        var result = new ArrayList<BodyHandStateSnapshot.GridHost>();
        var seen = new HashSet<String>();
        for (String id : hands.handIds()) {
            var stack = hands.stackCopy(id);
            if (stack.isPresent() && (stack.orElseThrow().is(ModItems.BAG) || stack.orElseThrow().is(ModItems.BELT))) {
                var host = stack.orElseThrow();
                addGrid(result, seen, hands.token(id).orElse(null), host);
            }
        }
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isPresent()) for (var slot : equipment.orElseThrow().slots()) {
            if (slot.stack().isPresent() && (slot.stack().orElseThrow().is(ModItems.BAG)
                    || slot.stack().orElseThrow().is(ModItems.BELT)))
                addGrid(result, seen, slot.token().orElse(null), slot.stack().orElseThrow());
        }
        return result;
    }

    private static void addGrid(List<BodyHandStateSnapshot.GridHost> result, Set<String> seen, String token, ItemStack stack) {
        if (token == null || !seen.add(token)) throw new IllegalArgumentException("Ambiguous grid host");
        // Preserve legacy ownership summaries without asking grid migration to validate legacy contents.
        // In particular, valid oversized legacy children need not fit the projected grid dimensions.
        if (!stack.has(ModDataComponents.GRID_CONTENTS.get()) && stack.has(ModDataComponents.POUCH_CONTENTS.get())) {
            var legacy = PouchContents.read(stack).orElseThrow(() -> new IllegalArgumentException("Invalid legacy pouch host"));
            if (legacy.child().isPresent()) return;
        }
        var content = GridContents.read(stack).orElseThrow(() -> new IllegalArgumentException("Invalid grid host"));
        var entries = content.entries().stream().map(e -> new BodyHandStateSnapshot.GridEntry(e.token(),
                BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString(), e.stack().getCount(), e.x(), e.y(), e.rotation().degrees())).toList();
        result.add(new BodyHandStateSnapshot.GridHost(token, stack.is(ModItems.BAG) ? "bag" : "belt",
                stack.is(ModItems.BAG) ? 7 : 8, stack.is(ModItems.BAG) ? 4 : 2, entries));
    }

    /** Bounded replay history keyed by the exact player identity and requested epoch (including bootstrap zero). */
    static final class QueryGate {
        private final LinkedHashMap<Long, Long> sequences = new LinkedHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int count;
        private long issuedEpoch;
        private LivingEntity issuedBody;

        void clearIssued() {
            issuedEpoch = 0;
            issuedBody = null;
        }

        BodyHandStateSnapshot.Reason admit(long epoch, long sequence, long currentTick) {
            if (tick != currentTick) { tick = currentTick; count = 0; }
            if (++count > 4) return BodyHandStateSnapshot.Reason.RATE_LIMITED;
            if (sequence <= sequences.getOrDefault(epoch, 0L)) return BodyHandStateSnapshot.Reason.REPLAY;
            sequences.put(epoch, sequence);
            if (sequences.size() > 8) sequences.remove(sequences.keySet().iterator().next());
            return null;
        }
    }
}
