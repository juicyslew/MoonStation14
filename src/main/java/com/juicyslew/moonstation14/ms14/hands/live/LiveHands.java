package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Inert body-owned stack snapshot. Never publish its tokens to HANDS: legacy HANDS is metadata,
 * not stack ownership. Package-private whole-stack transitions are pure state calculations, not
 * authorization; server-side transfer services verify ownership, prototype and legacy compatibility
 * before publishing a snapshot.
 */
public final class LiveHands {
    private static final Codec<String> HAND_ID = Codec.STRING.validate(value ->
            value.isBlank() || value.length() > HandState.MAX_ID_LENGTH
                    ? DataResult.error(() -> "invalid hand ID") : DataResult.success(value));
    private static final Codec<String> TOKEN = Codec.STRING.validate(value ->
            value.isBlank() || value.length() > ItemToken.MAX_LENGTH
                    ? DataResult.error(() -> "invalid item token") : DataResult.success(value));
    private static final Codec<RawOccupant> RAW_OCCUPANT = RecordCodecBuilder.create(instance -> instance.group(
            TOKEN.fieldOf("token").forGetter(RawOccupant::token),
            ItemStack.CODEC.fieldOf("stack").forGetter(RawOccupant::stack)
    ).apply(instance, RawOccupant::new));
    private static final Codec<Occupant> OCCUPANT = RAW_OCCUPANT.comapFlatMap(raw -> {
        try {
            return DataResult.success(new Occupant(raw.token(), raw.stack()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, occupant -> new RawOccupant(occupant.token(), occupant.stack()));
    private static final Codec<Slot> SLOT = RecordCodecBuilder.create(instance -> instance.group(
            HAND_ID.fieldOf("id").forGetter(Slot::id),
            OCCUPANT.optionalFieldOf("occupant").forGetter(Slot::occupant)
    ).apply(instance, Slot::new));
    private static final Codec<String> EQUIPMENT_ID = Codec.STRING.validate(value ->
            EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.contains(value)
                    ? DataResult.success(value) : DataResult.error(() -> "invalid equipment slot"));
    private static final Codec<Slot> EQUIPMENT_SLOT = RecordCodecBuilder.create(instance -> instance.group(
            EQUIPMENT_ID.fieldOf("id").forGetter(Slot::id),
            OCCUPANT.optionalFieldOf("occupant").forGetter(Slot::occupant)
    ).apply(instance, Slot::new));
    private static final Codec<Raw> RAW = RecordCodecBuilder.create(instance -> instance.group(
            SLOT.listOf().fieldOf("hands").forGetter(Raw::hands),
            HAND_ID.fieldOf("active").forGetter(Raw::active),
            Codec.LONG.fieldOf("revision").forGetter(Raw::revision),
            EQUIPMENT_SLOT.listOf().optionalFieldOf("equipment").forGetter(Raw::equipment)
    ).apply(instance, Raw::new));
    public static final Codec<LiveHands> CODEC = RAW.comapFlatMap(raw -> {
        try {
            return DataResult.success(new LiveHands(raw.hands(), raw.active(), raw.revision(), raw.equipment()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, value -> new Raw(value.hands, value.active, value.revision, value.equipment));

    private final List<Slot> hands;
    private final String active;
    private final long revision;
    // Absent means a pre-equipment save, not a saved empty equipment layout.
    private final Optional<List<Slot>> equipment;

    private LiveHands(List<Slot> hands, String active, long revision, Optional<List<Slot>> equipment) {
        this.hands = List.copyOf(hands);
        this.active = active;
        this.revision = revision;
        this.equipment = equipment.map(List::copyOf);
        if (hands.isEmpty() || hands.size() > HandState.MAX_HANDS || revision < 0)
            throw new IllegalArgumentException("invalid hand count or revision");
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (Slot slot : hands) {
            if (!ids.add(slot.id()) || slot.occupant().filter(value -> !tokens.add(value.token())).isPresent())
                throw new IllegalArgumentException("duplicate hand or token");
        }
        if (this.equipment.map(slots -> slots.size() > EquipmentSlotsPrototypeComponent.MAX_SLOTS).orElse(false))
            throw new IllegalArgumentException("too many equipment slots");
        Set<String> equipmentIds = new HashSet<>();
        for (Slot slot : this.equipment.orElse(List.of())) {
            if (!EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.contains(slot.id())
                    || !equipmentIds.add(slot.id())
                    || slot.occupant().filter(value -> !tokens.add(value.token())).isPresent())
                throw new IllegalArgumentException("duplicate or invalid equipment slot or token");
        }
        if (!ids.contains(active)) throw new IllegalArgumentException("unknown active hand");
    }

    /** Explicit initialization only; never uses a human default for an unbound body or pig. */
    public static Optional<LiveHands> initialize(Entity body) {
        if (body.hasData(ModDataAttachments.LIVE_HANDS.get())) return Optional.empty();
        Optional<List<String>> prototype = hostHandIds(body);
        if (prototype.isEmpty() || prototype.orElseThrow().isEmpty()) return Optional.empty();
        try {
            HandState state = HandState.create(prototype.orElseThrow());
            LiveHands candidate = new LiveHands(state.handIds().stream()
                    .map(id -> new Slot(id, Optional.empty())).toList(), state.activeHand(), 0,
                    hostEquipmentIds(body).map(ids -> ids.stream().map(id -> new Slot(id, Optional.empty())).toList()));
            if (!candidate.compatible(body)) return Optional.empty();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), candidate);
            return Optional.of(candidate);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Read-only eligibility: absence is NOT proof of an empty hand. */
    public static boolean isEmptyHand(Entity body, String hand) {
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return state != null && state.compatible(body)
                && state.hands.stream().anyMatch(slot -> slot.id().equals(hand) && slot.occupant().isEmpty());
    }

    /** Legacy HANDS records layout metadata, not live active selection or stack ownership. */
    public boolean compatible(Entity body) {
        if (hostHandIds(body).filter(ids -> ids.equals(handIds())).isEmpty()) return false;
        Optional<List<String>> declared = hostEquipmentIds(body);
        if (equipment.isPresent() && declared.filter(ids -> ids.equals(equipmentIds())).isEmpty()) return false;
        HandAttachment metadata = body.getExistingDataOrNull(ModDataAttachments.HANDS.get());
        return metadata == null || (metadata.state().hands().stream().map(slot -> slot.id()).toList().equals(handIds())
                && metadata.state().hands().stream().allMatch(slot -> slot.token().isEmpty()));
    }

    private static Optional<List<String>> hostHandIds(Entity body) {
        return HandCapability.resolve(body);
    }

    private static Optional<List<String>> hostEquipmentIds(Entity body) {
        return HandCapability.resolveCharacter(body)
                .flatMap(character -> character.component(EquipmentSlotsPrototypeComponent.class))
                .map(EquipmentSlotsPrototypeComponent::slots);
    }

    private List<String> equipmentIds() {
        return equipment.orElse(List.of()).stream().map(Slot::id).toList();
    }

    /** Absent result means no compatible equipment capability; persisted distinguishes legacy absence. */
    public static Optional<EquipmentSnapshot> equipmentSnapshot(Entity body) {
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (state == null || !state.compatible(body)) return Optional.empty();
        return hostEquipmentIds(body).map(ids -> new EquipmentSnapshot(state.equipment.isPresent(),
                ids.stream().map(id -> new EquipmentSlot(id, state.equipment.orElse(List.of()).stream()
                        .filter(slot -> slot.id().equals(id)).findFirst()
                        .flatMap(Slot::occupant).map(Occupant::token),
                        state.equipment.orElse(List.of()).stream().filter(slot -> slot.id().equals(id))
                                .findFirst().flatMap(Slot::occupant).map(Occupant::stack))).toList()));
    }

    public record EquipmentSlot(String id, Optional<String> token, Optional<ItemStack> stack) {
        public EquipmentSlot {
            stack = stack.map(ItemStack::copy);
        }
        @Override public Optional<ItemStack> stack() { return stack.map(ItemStack::copy); }
    }
    public record EquipmentSnapshot(boolean persisted, List<EquipmentSlot> slots) {
        public EquipmentSnapshot { slots = List.copyOf(slots); }
    }

    public List<String> handIds() { return hands.stream().map(Slot::id).toList(); }
    public String activeHand() { return active; }
    public long revision() { return revision; }

    /** Transient pull lock; the UUID is the exact target and is not an item/stack token. */
    public Optional<java.util.UUID> reservation(String hand) {
        return hands.stream().filter(slot -> slot.id().equals(hand)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hand")).reservation();
    }

    public Optional<LiveHands> reserveHand(long expectedRevision, String hand, java.util.UUID target) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || target == null) return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0 || hands.get(index).occupant().isPresent() || hands.get(index).reservation().isPresent())
            return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.empty(), Optional.of(target)));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Pure component update for an owned stack; item identity and count cannot change. */
    Optional<LiveHands> replaceOwnedComponents(long expectedRevision, String hand, ItemToken expectedToken,
                                                ItemStack replacement) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hand == null || expectedToken == null
                || replacement == null || replacement.isEmpty()) return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0 || hands.get(index).reservation().isPresent()) return Optional.empty();
        Optional<Occupant> current = hands.get(index).occupant();
        if (current.isEmpty() || !current.orElseThrow().token().equals(expectedToken.value())) return Optional.empty();
        ItemStack original = current.orElseThrow().stack();
        if (!original.is(replacement.getItem()) || original.getCount() != replacement.getCount()) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(expectedToken.value(), replacement))));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    public Optional<LiveHands> releaseHand(String hand, java.util.UUID exactTarget) {
        if (revision == Long.MAX_VALUE || exactTarget == null) return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0 || !hands.get(index).reservation().equals(Optional.of(exactTarget))) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, hands.get(index).occupant()));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Returns a defensive snapshot, never a transferable ownership claim. */
    public Optional<ItemStack> stackCopy(String hand) {
        return hands.stream().filter(slot -> slot.id().equals(hand)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hand"))
                .occupant().map(value -> value.stack().copy());
    }

    public Optional<String> token(String hand) {
        return hands.stream().filter(slot -> slot.id().equals(hand)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hand"))
                .occupant().map(Occupant::token);
    }

    public Optional<ItemStack> equipmentStack(String slot) {
        return equipment.orElse(List.of()).stream().filter(value -> value.id().equals(slot)).findFirst()
                .flatMap(Slot::occupant).map(value -> value.stack().copy());
    }

    /** Pure atomic consume-and-place for body-owned crafting. The output is merged first, then
     * placed in a free hand (including the source hand if its last unit was consumed). */
    public Optional<LiveHands> consumeAndPlace(long expectedRevision, String sourceHand, String sourceToken,
            ItemStack expectedSource, int consumeCount, ItemStack output, String outputToken) {
        if (expectedRevision != revision || revision == Long.MAX_VALUE || sourceHand == null
                || sourceToken == null || expectedSource == null || expectedSource.isEmpty()
                || consumeCount <= 0 || output == null || output.isEmpty() || outputToken == null
                || output.getCount() > output.getMaxStackSize()) return Optional.empty();
        int sourceIndex = handIds().indexOf(sourceHand);
        if (sourceIndex < 0 || hands.get(sourceIndex).reservation().isPresent()) return Optional.empty();
        Occupant source = hands.get(sourceIndex).occupant().orElse(null);
        if (source == null || !source.token().equals(sourceToken)
                || !ItemStack.isSameItemSameComponents(source.stack(), expectedSource)
                || source.stack().getCount() != expectedSource.getCount()
                || source.stack().getCount() < consumeCount || allOccupants().stream()
                .anyMatch(value -> value.token().equals(outputToken))) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElse(List.of()));
        for (int i = 0; i < next.size(); i++) {
            Slot slot = next.get(i);
            if (i == sourceIndex) continue;
            Occupant occupant = slot.occupant().orElse(null);
            if (occupant != null && ItemStack.isSameItemSameComponents(occupant.stack(), output)
                    && occupant.stack().getCount() + output.getCount() <= occupant.stack().getMaxStackSize()) {
                ItemStack merged = occupant.stack(); merged.grow(output.getCount());
                next.set(i, new Slot(slot.id(), Optional.of(new Occupant(occupant.token(), merged))));
                output = ItemStack.EMPTY;
                break;
            }
        }
        if (!output.isEmpty()) {
            for (int i = 0; i < nextEquipment.size() && !output.isEmpty(); i++) {
                Slot slot = nextEquipment.get(i); Occupant occupant = slot.occupant().orElse(null);
                if (occupant != null) {
                    var grid = GridContents.read(occupant.stack());
                    if (grid.isPresent()) {
                        var geometry = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(
                                occupant.stack().is(ModItems.BAG) ? 7 : 8, occupant.stack().is(ModItems.BAG) ? 4 : 2));
                        for (var entry : grid.orElseThrow().entries()) geometry = geometry.place(entry.token(),
                                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(entry.stack()),
                                new GridGeometry.Cell(entry.x(), entry.y()), entry.rotation()).snapshot();
                        var shape = com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(output);
                        int width = occupant.stack().is(ModItems.BAG) ? 7 : 8;
                        int height = occupant.stack().is(ModItems.BAG) ? 4 : 2;
                        for (int y=0; y<height && !output.isEmpty(); y++) for (int x=0; x<width && !output.isEmpty(); x++) {
                            var cell = new GridGeometry.Cell(x,y);
                            var placed = geometry.place(outputToken, shape, cell, GridGeometry.Rotation.DEG_0);
                            if (placed.accepted()) {
                                var entries = new ArrayList<>(grid.orElseThrow().entries());
                                entries.add(new GridContents.Entry(outputToken, output, x, y, GridGeometry.Rotation.DEG_0));
                                var updated = GridContents.applyCopyWithGrid(occupant.stack(), GridContents.of(entries));
                                if (updated.isPresent()) { nextEquipment.set(i,new Slot(slot.id(),Optional.of(new Occupant(occupant.token(),updated.orElseThrow())))); output=ItemStack.EMPTY; }
                            }
                        }
                    }
                }
            }
        }
        ItemStack remaining = source.stack(); remaining.shrink(consumeCount);
        next.set(sourceIndex, remaining.isEmpty() ? new Slot(sourceHand, Optional.empty())
                : new Slot(sourceHand, Optional.of(new Occupant(sourceToken, remaining))));
        if (!output.isEmpty()) {
            int free = -1;
            for (int i=0;i<next.size();i++) if (next.get(i).occupant().isEmpty() && next.get(i).reservation().isEmpty()) { free=i; break; }
            if (free < 0) return Optional.empty();
            next.set(free,new Slot(next.get(free).id(),Optional.of(new Occupant(outputToken,output))));
        }
        return Optional.of(new LiveHands(next, active, revision+1, equipment.isPresent()?Optional.of(nextEquipment):Optional.empty()));
    }

    /** Selects one matching grid child (hands are deliberately searched first) and stages exact
     * consumption. The existing child token is retained for an unconsumed remainder. */
    public Optional<CraftSelection> consumeBodyMaterial(long expectedRevision, java.util.function.Predicate<ItemStack> matches,
            int units, ItemStack output, String outputToken) {
        if (expectedRevision != revision || revision == Long.MAX_VALUE || matches == null || units < 1
                || output == null || output.isEmpty() || output.getCount()>output.getMaxStackSize() || outputToken == null) return Optional.empty();
        if (allOccupants().stream().anyMatch(o -> o.token().equals(outputToken))) return Optional.empty();
        for(Slot slot:equipment.orElse(List.of())) { Occupant host=slot.occupant().orElse(null);
            if(host!=null) { var contents=GridContents.read(host.stack()); if(contents.isPresent()
                    && contents.orElseThrow().entries().stream().anyMatch(e->e.token().equals(outputToken))) return Optional.empty(); }
        }
        for (Slot hand : hands) {
            Occupant o = hand.occupant().orElse(null);
            if (o != null && matches.test(o.stack()) && o.stack().getCount() >= units) {
                var candidate = consumeAndPlace(expectedRevision, hand.id(), o.token(), o.stack(), units, output, outputToken);
                return candidate.map(state -> new CraftSelection(state, new CraftSource(false, hand.id(), o.token(), null)));
            }
        }
        // The capability-bound live consumer revalidates declared slots before publication.
        ArrayList<Slot> equipmentNext = new ArrayList<>(equipment.orElse(List.of()));
        ArrayList<Slot> handsNext = new ArrayList<>(hands);
        for (int i=0;i<equipmentNext.size();i++) {
            Slot hostSlot=equipmentNext.get(i); Occupant host=hostSlot.occupant().orElse(null);
            if (host==null || !(host.stack().is(ModItems.BAG)||host.stack().is(ModItems.BELT))) continue;
            GridContents grid=GridContents.read(host.stack()).orElse(null); if(grid==null) continue;
            for (GridContents.Entry child:grid.entries()) {
                ItemStack stack=child.stack();
                if (!matches.test(stack) || stack.getCount()<units) continue;
                ItemStack rem=stack.copy(); rem.shrink(units);
                ArrayList<GridContents.Entry> entries=new ArrayList<>(grid.entries());
                entries.removeIf(e->e.token().equals(child.token()));
                if(!rem.isEmpty()) entries.add(new GridContents.Entry(child.token(),rem,child.x(),child.y(),child.rotation()));
                Optional<ItemStack> changed=GridContents.applyCopyWithGrid(host.stack(),GridContents.of(entries));
                if(changed.isEmpty()) continue;
                equipmentNext.set(i,new Slot(hostSlot.id(),Optional.of(new Occupant(host.token(),changed.orElseThrow()))));
                ItemStack product=output.copy(); boolean placed=false;
                // Output goes into a free hand first. Grid insertion is subsequently attempted in the vacated source grid.
                for(int h=0;h<handsNext.size();h++) if(handsNext.get(h).occupant().isEmpty() && handsNext.get(h).reservation().isEmpty()) {
                    handsNext.set(h,new Slot(handsNext.get(h).id(),Optional.of(new Occupant(outputToken,product)))); placed=true; break;
                }
                if(!placed) {
                    Optional<GridContents> reduced=GridContents.read(changed.orElseThrow());
                    if(reduced.isPresent()) {
                        int w=host.stack().is(ModItems.BAG)?7:8, h=host.stack().is(ModItems.BAG)?4:2;
                        var geo=GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(w,h));
                        for(var e:reduced.orElseThrow().entries()) geo=geo.place(e.token(),com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(e.stack()),new GridGeometry.Cell(e.x(),e.y()),e.rotation()).snapshot();
                        var shape=com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(product);
                        outer: for(int y=0;y<h;y++) for(int x=0;x<w;x++) { var p=geo.place(outputToken,shape,new GridGeometry.Cell(x,y),GridGeometry.Rotation.DEG_0); if(!p.accepted()) continue;
                            var add=new ArrayList<>(reduced.orElseThrow().entries()); add.add(new GridContents.Entry(outputToken,product,x,y,GridGeometry.Rotation.DEG_0));
                            var finalHost=GridContents.applyCopyWithGrid(changed.orElseThrow(),GridContents.of(add));
                            if(finalHost.isPresent()){equipmentNext.set(i,new Slot(hostSlot.id(),Optional.of(new Occupant(host.token(),finalHost.orElseThrow())))); placed=true; break outer;}
                        }
                    }
                }
                if (!placed) return Optional.empty();
                return Optional.of(new CraftSelection(new LiveHands(handsNext,active,revision+1,
                        equipment.isPresent()?Optional.of(equipmentNext):Optional.empty()),
                        new CraftSource(true,hostSlot.id(),host.token(),child.token())));
            }
        }
        return Optional.empty();
    }
    public boolean hasAdequateBodyMaterial(java.util.function.Predicate<ItemStack> matches, int units) {
        if(matches==null||units<1) return false;
        for(Slot hand:hands) { Occupant o=hand.occupant().orElse(null); if(o!=null&&o.stack().getCount()>=units&&matches.test(o.stack())) return true; }
        for(Slot slot:equipment.orElse(List.of())) { Occupant host=slot.occupant().orElse(null); if(host==null) continue;
            var contents=GridContents.read(host.stack()); if(contents.isEmpty()) continue;
            for(var child:contents.orElseThrow().entries()) if(child.stack().getCount()>=units&&matches.test(child.stack())) return true;
        }
        return false;
    }

    /** Pure output-only preflight for a recipe whose source is outside body ownership. */
    public Optional<LiveHands> placeCraftOutput(long expectedRevision, ItemStack output, String outputToken) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || output == null || output.isEmpty()
                || output.getCount() > output.getMaxStackSize() || outputToken == null
                || allOccupants().stream().anyMatch(o -> o.token().equals(outputToken))) return Optional.empty();
        for (Slot slot : hands) {
            Occupant o = slot.occupant().orElse(null);
            if (o != null && ItemStack.isSameItemSameComponents(o.stack(), output)
                    && o.stack().getCount() + output.getCount() <= o.stack().getMaxStackSize()) {
                ArrayList<Slot> next = new ArrayList<>(hands);
                ItemStack merged = o.stack(); merged.grow(output.getCount());
                next.set(handIds().indexOf(slot.id()), new Slot(slot.id(), Optional.of(new Occupant(o.token(), merged))));
                return Optional.of(new LiveHands(next, active, revision + 1, equipment));
            }
        }
        for (int i=0;i<hands.size();i++) if (hands.get(i).occupant().isEmpty() && hands.get(i).reservation().isEmpty()) {
            ArrayList<Slot> next = new ArrayList<>(hands);
            next.set(i,new Slot(next.get(i).id(),Optional.of(new Occupant(outputToken,output))));
            return Optional.of(new LiveHands(next,active,revision+1,equipment));
        }
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElse(List.of()));
        for(int i=0;i<nextEquipment.size();i++) {
            Slot slot=nextEquipment.get(i); Occupant host=slot.occupant().orElse(null);
            if(host==null||!(host.stack().is(ModItems.BAG)||host.stack().is(ModItems.BELT))) continue;
            var grid=GridContents.read(host.stack()); if(grid.isEmpty()) continue;
            int w=host.stack().is(ModItems.BAG)?7:8,h=host.stack().is(ModItems.BAG)?4:2;
            var geo=GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(w,h));
            for(var entry:grid.orElseThrow().entries()) geo=geo.place(entry.token(),com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(entry.stack()),new GridGeometry.Cell(entry.x(),entry.y()),entry.rotation()).snapshot();
            var shape=com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(output);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                var pos=new GridGeometry.Cell(x,y); if(!geo.place(outputToken,shape,pos,GridGeometry.Rotation.DEG_0).accepted()) continue;
                var entries=new ArrayList<>(grid.orElseThrow().entries()); entries.add(new GridContents.Entry(outputToken,output,x,y,GridGeometry.Rotation.DEG_0));
                var updated=GridContents.applyCopyWithGrid(host.stack(),GridContents.of(entries));
                if(updated.isPresent()) { nextEquipment.set(i,new Slot(slot.id(),Optional.of(new Occupant(host.token(),updated.orElseThrow()))));
                    return Optional.of(new LiveHands(hands,active,revision+1,equipment.isPresent()?Optional.of(nextEquipment):Optional.empty())); }
            }
        }
        return Optional.empty();
    }
    public record CraftSource(boolean wornGrid,String hostId,String hostToken,String childToken) { }
    public record CraftSelection(LiveHands state,CraftSource source) { }

    /** Pure active-hand change; occupants and tokens stay in their original slots. */
    public Optional<LiveHands> selectActive(long expectedRevision, String chosen) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || Objects.equals(active, chosen) || !handIds().contains(chosen)) return Optional.empty();
        return Optional.of(new LiveHands(hands, chosen, revision + 1, equipment));
    }

    enum Rejection { STALE_REVISION, REVISION_OVERFLOW, UNKNOWN_HAND, HAND_OCCUPIED,
        HAND_EMPTY, TOKEN_MISMATCH, DUPLICATE_TOKEN, INVALID_STACK, DESTINATION_OCCUPIED }

    /** A detached result; rejected operations retain the exact original snapshot. */
    record WholeResult(LiveHands state, Optional<ItemStack> takenStack, Optional<Rejection> rejection) {
        WholeResult {
            Objects.requireNonNull(state);
            Objects.requireNonNull(takenStack);
            Objects.requireNonNull(rejection);
            takenStack = takenStack.map(ItemStack::copy);
        }

        @Override public Optional<ItemStack> takenStack() {
            return takenStack.map(ItemStack::copy);
        }

        boolean succeeded() { return rejection.isEmpty(); }
    }

    private WholeResult reject(Rejection reason) {
        return new WholeResult(this, Optional.empty(), Optional.of(reason));
    }

    /** Pure whole-stack calculation. The supplied stack is not proof of source ownership. */
    WholeResult putWhole(long expectedRevision, String hand, ItemToken token, ItemStack stack) {
        if (expectedRevision != revision) return reject(Rejection.STALE_REVISION);
        if (revision == Long.MAX_VALUE) return reject(Rejection.REVISION_OVERFLOW);
        int index = handIds().indexOf(hand);
        if (index < 0) return reject(Rejection.UNKNOWN_HAND);
        if (hands.get(index).occupant().isPresent() || hands.get(index).reservation().isPresent()) return reject(Rejection.HAND_OCCUPIED);
        if (token == null || stack == null || stack.isEmpty() || stack.getCount() <= 0
                || stack.getCount() > stack.getMaxStackSize()) return reject(Rejection.INVALID_STACK);
        if (allOccupants().stream().anyMatch(value -> value.token().equals(token.value())))
            return reject(Rejection.DUPLICATE_TOKEN);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(token.value(), stack))));
        return new WholeResult(new LiveHands(next, active, revision + 1, equipment), Optional.empty(), Optional.empty());
    }

    /** Pure whole-stack calculation; caller must publish atomically with the owning carrier. */
    WholeResult takeWhole(long expectedRevision, String hand, ItemToken expectedToken) {
        if (expectedRevision != revision) return reject(Rejection.STALE_REVISION);
        if (revision == Long.MAX_VALUE) return reject(Rejection.REVISION_OVERFLOW);
        int index = handIds().indexOf(hand);
        if (index < 0) return reject(Rejection.UNKNOWN_HAND);
        if (hands.get(index).reservation().isPresent()) return reject(Rejection.HAND_OCCUPIED);
        Optional<Occupant> occupant = hands.get(index).occupant();
        if (occupant.isEmpty()) return reject(Rejection.HAND_EMPTY);
        if (expectedToken == null || !occupant.orElseThrow().token().equals(expectedToken.value()))
            return reject(Rejection.TOKEN_MISMATCH);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.empty()));
        return new WholeResult(new LiveHands(next, active, revision + 1, equipment),
                Optional.of(occupant.orElseThrow().stack()), Optional.empty());
    }

    /** Pure consumption of exactly one unit from the exact active hand. */
    Optional<LiveHands> consumeOne(long expectedRevision, String hand, String expectedToken, ItemStack expectedItem) {
        if (expectedRevision != revision || revision == Long.MAX_VALUE || hand == null
                || !active.equals(hand) || expectedToken == null || expectedItem == null || expectedItem.isEmpty())
            return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0) return Optional.empty();
        Optional<Occupant> occupant = hands.get(index).occupant();
        if (occupant.isEmpty()) return Optional.empty();
        Occupant current = occupant.orElseThrow();
        ItemStack stack = current.stack();
        if (!current.token().equals(expectedToken) || !sameItem(stack, expectedItem)
                || stack.getCount() != expectedItem.getCount() || stack.getCount() > stack.getMaxStackSize())
            return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        if (stack.getCount() == 1) {
            next.set(index, new Slot(hand, Optional.empty()));
        } else {
            stack.shrink(1);
            next.set(index, new Slot(hand, Optional.of(new Occupant(current.token(), stack))));
        }
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }

    /** Pure bounded in-place use mutation; unlike insertion this cannot add a new item or increase quantity. */
    Optional<LiveHands> replaceActiveUseStack(long expectedRevision, String hand, String expectedToken,
                                               ItemStack expectedBefore, ItemStack after) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hand == null || expectedToken == null
                || expectedBefore == null || expectedBefore.isEmpty() || after == null
                || !active.equals(hand)) return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0) return Optional.empty();
        Optional<Occupant> occupant = hands.get(index).occupant();
        if (occupant.isEmpty()) return Optional.empty();
        Occupant current = occupant.orElseThrow();
        ItemStack original = current.stack();
        if (!current.token().equals(expectedToken) || !sameItem(original, expectedBefore)
                || original.getCount() != expectedBefore.getCount() || original.getCount() > original.getMaxStackSize())
            return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        if (after.isEmpty()) next.set(index, new Slot(hand, Optional.empty()));
        else {
            if (original.getItem() != after.getItem() || after.getCount() <= 0 || after.getCount() > original.getCount()
                    || after.getCount() > after.getMaxStackSize()) return Optional.empty();
            next.set(index, new Slot(hand, Optional.of(new Occupant(current.token(), after))));
        }
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Pure whole-occupant transfer between named hands; active selection is deliberately irrelevant. */
    WholeResult transferWhole(long expectedRevision, String source, String destination, ItemToken expectedToken) {
        if (expectedRevision != revision) return reject(Rejection.STALE_REVISION);
        if (revision == Long.MAX_VALUE) return reject(Rejection.REVISION_OVERFLOW);
        int from = handIds().indexOf(source), to = handIds().indexOf(destination);
        if (from < 0 || to < 0 || Objects.equals(source, destination)) return reject(Rejection.UNKNOWN_HAND);
        Optional<Occupant> occupant = hands.get(from).occupant();
        if (occupant.isEmpty()) return reject(Rejection.HAND_EMPTY);
        if (expectedToken == null || !occupant.orElseThrow().token().equals(expectedToken.value())) return reject(Rejection.TOKEN_MISMATCH);
        if (hands.get(from).reservation().isPresent() || hands.get(to).occupant().isPresent()
                || hands.get(to).reservation().isPresent()) return reject(Rejection.DESTINATION_OCCUPIED);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(from, new Slot(source, Optional.empty()));
        next.set(to, new Slot(destination, occupant));
        return new WholeResult(new LiveHands(next, active, revision + 1, equipment), Optional.empty(), Optional.empty());
    }

    /** Pure single-stack component replacement, preserving the exact owner's token. */
    Optional<LiveHands> replaceOwnedStack(long expectedRevision, String hand, ItemToken expectedToken,
                                          ItemStack replacement) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hand == null || expectedToken == null
                || replacement == null || replacement.isEmpty() || replacement.getCount() != 1
                || !replacement.has(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get())) return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0) return Optional.empty();
        Optional<Occupant> current = hands.get(index).occupant();
        if (current.isEmpty() || !current.orElseThrow().token().equals(expectedToken.value())) return Optional.empty();
        ItemStack original = current.orElseThrow().stack();
        if (!original.has(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get())
                || original.getCount() != 1) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(expectedToken.value(), replacement))));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Internal fuse progress only: records one monotonic beep flag without changing ownership revision. */
    Optional<LiveHands> advanceOwnedGrenadeBeep(long expectedRevision, String hand, ItemToken expectedToken,
                                                ItemStack replacement) {
        if (revision != expectedRevision || hand == null || expectedToken == null || replacement == null
                || replacement.isEmpty() || replacement.getCount() != 1
                || !replacement.has(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get()))
            return Optional.empty();
        int index = handIds().indexOf(hand);
        if (index < 0) return Optional.empty();
        Optional<Occupant> current = hands.get(index).occupant();
        if (current.isEmpty() || !current.orElseThrow().token().equals(expectedToken.value())) return Optional.empty();
        ItemStack original = current.orElseThrow().stack();
        if (!original.has(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get())
                || original.getCount() != 1) return Optional.empty();
        var beforeFuse = original.get(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get());
        var afterFuse = replacement.get(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get());
        if (beforeFuse == null || afterFuse == null
                || beforeFuse.phase() != com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse.Phase.ARMED
                || afterFuse.phase() != beforeFuse.phase() || afterFuse.deadline() != beforeFuse.deadline()
                || beforeFuse.beepedAtPrime() && !afterFuse.beepedAtPrime()
                || beforeFuse.beepedAtForty() && !afterFuse.beepedAtForty()
                || beforeFuse.beepedAtPrime() == afterFuse.beepedAtPrime()
                && beforeFuse.beepedAtForty() == afterFuse.beepedAtForty()) return Optional.empty();
        ItemStack normalized = replacement.copy();
        normalized.set(com.juicyslew.moonstation14.component.ModDataComponents.GRENADE_FUSE.get(), beforeFuse);
        if (!ItemStack.isSameItemSameComponents(original, normalized)) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(expectedToken.value(), replacement))));
        return Optional.of(new LiveHands(next, active, revision, equipment));
    }

    /**
     * Ticks finite tool state without changing ownership or action revision. This is passive owner state,
     * so a fuel decrement alone must not stale otherwise valid held-item requests.
     */
    public LiveHands tickWelderStacks(java.util.function.UnaryOperator<ItemStack> tick) {
        Objects.requireNonNull(tick, "tick");
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElse(List.of()));
        boolean changed = tickStacks(nextHands, tick) | tickStacks(nextEquipment, tick);
        return changed ? new LiveHands(nextHands, active, revision, equipment.isPresent()
                ? Optional.of(nextEquipment) : Optional.empty()) : this;
    }

    private static boolean tickStacks(ArrayList<Slot> slots, java.util.function.UnaryOperator<ItemStack> tick) {
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.occupant().isEmpty()) continue;
            Occupant owner = slot.occupant().orElseThrow();
            ItemStack before = owner.stack();
            ItemStack after = tick.apply(before);
            if (after == null || after.isEmpty() || after.getItem() != before.getItem()
                    || after.getCount() != before.getCount()) continue;
            if (ItemStack.isSameItemSameComponents(before, after)) continue;
            slots.set(i, new Slot(slot.id(), Optional.of(new Occupant(owner.token(), after))));
            changed = true;
        }
        return changed;
    }

    /** Pure, single-revision movement into the pouch component; no intermediate ownership state. */
    Optional<LiveHands> insertPouch(long expectedRevision, String pouchHand, String pouchToken,
                                    String sourceHand, String sourceToken) {
        int pouchIndex = handIds().indexOf(pouchHand);
        int sourceIndex = handIds().indexOf(sourceHand);
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || pouchIndex < 0 || sourceIndex < 0 || pouchIndex == sourceIndex
                || pouchToken == null || sourceToken == null || pouchToken.equals(sourceToken)) return Optional.empty();
        Optional<Occupant> pouch = hands.get(pouchIndex).occupant();
        Optional<Occupant> source = hands.get(sourceIndex).occupant();
        if (pouch.isEmpty() || source.isEmpty() || !pouch.orElseThrow().token().equals(pouchToken)
                || !source.orElseThrow().token().equals(sourceToken)) return Optional.empty();
        Optional<ItemStack> filled = PouchContents.put(pouch.orElseThrow().stack(), sourceToken,
                source.orElseThrow().stack());
        if (filled.isEmpty()) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(pouchIndex, new Slot(pouchHand, Optional.of(new Occupant(pouchToken, filled.orElseThrow()))));
        next.set(sourceIndex, new Slot(sourceHand, Optional.empty()));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Pure one-cell transfer into an already worn container; the equipment owner never changes. */
    Optional<LiveHands> insertEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                               String containerToken, String hand, String sourceToken) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)) return Optional.empty();
        int handIndex = handIds().indexOf(hand);
        if (handIndex < 0 || sourceToken == null || sourceToken.equals(containerToken)) return Optional.empty();
        Optional<Occupant> source = hands.get(handIndex).occupant();
        if (source.isEmpty() || !source.orElseThrow().token().equals(sourceToken)
                || allOccupants().stream().filter(value -> value.token().equals(sourceToken)).count() != 1)
            return Optional.empty();
        int equipmentIndex = declared.indexOf(slot);
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        if (container.stack().has(ModDataComponents.GRID_CONTENTS.get())) return Optional.empty();
        Optional<ItemStack> filled = PouchContents.put(container.stack(), sourceToken, source.orElseThrow().stack());
        if (filled.isEmpty()) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(handIndex, new Slot(hand, Optional.empty()));
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(equipmentIndex, new Slot(slot, Optional.of(new Occupant(containerToken, filled.orElseThrow()))));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    /** Pure one-cell transfer out of a worn container into the designated empty hand. */
    Optional<LiveHands> extractEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                                String containerToken, String hand, String childToken) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)) return Optional.empty();
        int handIndex = handIds().indexOf(hand);
        if (handIndex < 0 || hands.get(handIndex).occupant().isPresent() || hands.get(handIndex).reservation().isPresent() || childToken == null
                || childToken.equals(containerToken) || allOccupants().stream()
                .anyMatch(value -> value.token().equals(childToken))) return Optional.empty();
        int equipmentIndex = declared.indexOf(slot);
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        if (container.stack().has(ModDataComponents.GRID_CONTENTS.get())) return Optional.empty();
        Optional<PouchContents.Removal> removal = PouchContents.take(container.stack());
        if (removal.isEmpty() || !removal.orElseThrow().child().token().equals(childToken)) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(handIndex, new Slot(hand,
                Optional.of(new Occupant(childToken, removal.orElseThrow().child().stack()))));
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(equipmentIndex, new Slot(slot,
                Optional.of(new Occupant(containerToken, removal.orElseThrow().pouch()))));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    private boolean validEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                         String containerToken) {
        if (!validEquipmentTransition(expectedRevision, declared) || equipment.isEmpty()
                || slot == null || containerToken == null || !(slot.equals("belt") || slot.equals("back")))
            return false;
        int index = declared.indexOf(slot);
        if (index < 0) return false;
        return equipmentSlot(index).occupant().filter(value -> value.token().equals(containerToken))
                .map(value -> {
                    ItemStack stack = value.stack();
                    return (slot.equals("belt") && stack.is(ModItems.BELT)
                            || slot.equals("back") && stack.is(ModItems.BAG))
                            && (PouchContents.read(stack).isPresent() || GridContents.read(stack).isPresent());
                }).orElse(false);
    }

    /** Pure multi-cell transfer into a worn grid container. Caller owns authorization/publication. */
    Optional<LiveHands> insertEquippedGrid(long expectedRevision, List<String> declared, String slot,
                                           String containerToken, String hand, String sourceToken,
                                           GridGeometry.Cell anchor, GridGeometry.Rotation rotation) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)
                || anchor == null || rotation == null) return Optional.empty();
        int handIndex = handIds().indexOf(hand), equipmentIndex = declared.indexOf(slot);
        if (handIndex < 0 || sourceToken == null || sourceToken.equals(containerToken)) return Optional.empty();
        Optional<Occupant> source = hands.get(handIndex).occupant();
        if (source.isEmpty() || !source.orElseThrow().token().equals(sourceToken)
                || allOccupants().stream().filter(value -> value.token().equals(sourceToken)).count() != 1) return Optional.empty();
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        Optional<GridContents> current = GridContents.read(container.stack());
        if (current.isEmpty() || current.orElseThrow().entries().stream().anyMatch(e -> e.token().equals(sourceToken)))
            return Optional.empty();
        Set<String> aggregateTokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) aggregateTokens.add(occupant.token());
        for (GridContents.Entry entry : current.orElseThrow().entries())
            if (!aggregateTokens.add(entry.token())) return Optional.empty();
        List<GridContents.Entry> entries = new ArrayList<>(current.orElseThrow().entries());
        try {
            entries.add(new GridContents.Entry(sourceToken, source.orElseThrow().stack(), anchor.x(), anchor.y(), rotation));
            Optional<ItemStack> updated = GridContents.applyCopyWithGrid(container.stack(), GridContents.of(entries));
            if (updated.isEmpty()) return Optional.empty();
            ArrayList<Slot> nextHands = new ArrayList<>(hands);
            nextHands.set(handIndex, new Slot(hand, Optional.empty()));
            ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
            nextEquipment.set(equipmentIndex, new Slot(slot, Optional.of(new Occupant(containerToken, updated.orElseThrow()))));
            return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Pure multi-cell transfer from a worn grid container into the designated empty hand. */
    Optional<LiveHands> extractEquippedGrid(long expectedRevision, List<String> declared, String slot,
                                            String containerToken, String hand, String childToken,
                                            GridGeometry.Cell anchor) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken) || anchor == null) return Optional.empty();
        int handIndex = handIds().indexOf(hand), equipmentIndex = declared.indexOf(slot);
        if (handIndex < 0 || hands.get(handIndex).occupant().isPresent() || hands.get(handIndex).reservation().isPresent() || childToken == null
                || childToken.equals(containerToken) || allOccupants().stream().anyMatch(v -> v.token().equals(childToken)))
            return Optional.empty();
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        Optional<GridContents> contents = GridContents.read(container.stack());
        if (contents.isEmpty()) return Optional.empty();
        Set<String> aggregateTokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) aggregateTokens.add(occupant.token());
        for (GridContents.Entry entry : contents.orElseThrow().entries())
            if (!aggregateTokens.add(entry.token())) return Optional.empty();
        GridContents.Entry child = contents.orElseThrow().entries().stream()
                .filter(e -> e.token().equals(childToken) && e.x() == anchor.x() && e.y() == anchor.y()).findFirst().orElse(null);
        if (child == null) return Optional.empty();
        List<GridContents.Entry> entries = contents.orElseThrow().entries().stream().filter(e -> !e.token().equals(childToken)).toList();
        Optional<ItemStack> updated = GridContents.applyCopyWithGrid(container.stack(), GridContents.of(entries));
        if (updated.isEmpty()) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(handIndex, new Slot(hand, Optional.of(new Occupant(childToken, child.stack()))));
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(equipmentIndex, new Slot(slot, Optional.of(new Occupant(containerToken, updated.orElseThrow()))));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    /** Pure in-place relocation of one child in an already materialized worn grid. */
    Optional<LiveHands> moveEquippedGrid(long expectedRevision, List<String> declared, String slot,
                                         String containerToken, String childToken,
                                         GridGeometry.Cell expectedOldAnchor, GridGeometry.Rotation expectedOldRotation,
                                         GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)
                || expectedOldAnchor == null || expectedOldRotation == null || newAnchor == null || newRotation == null)
            return Optional.empty();
        int equipmentIndex = declared.indexOf(slot);
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        if (!container.stack().has(ModDataComponents.GRID_CONTENTS.get())) return Optional.empty();
        Optional<GridContents> contents = GridContents.read(container.stack());
        if (contents.isEmpty()) return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) if (!tokens.add(occupant.token())) return Optional.empty();
        List<GridContents.Entry> entries = new ArrayList<>(contents.orElseThrow().entries());
        for (GridContents.Entry entry : entries) if (!tokens.add(entry.token())) return Optional.empty();
        int childIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            GridContents.Entry entry = entries.get(i);
            if (entry.token().equals(childToken)) {
                if (entry.x() != expectedOldAnchor.x() || entry.y() != expectedOldAnchor.y()
                        || entry.rotation() != expectedOldRotation) return Optional.empty();
                childIndex = i;
            }
        }
        if (childIndex < 0) return Optional.empty();
        GridContents.Entry old = entries.get(childIndex);
        if (old.x() == newAnchor.x() && old.y() == newAnchor.y() && old.rotation() == newRotation) return Optional.empty();
        try {
            entries.set(childIndex, new GridContents.Entry(old.token(), old.stack(), newAnchor.x(), newAnchor.y(), newRotation));
            Optional<ItemStack> updated = GridContents.applyCopyWithGrid(container.stack(), GridContents.of(entries));
            if (updated.isEmpty()) return Optional.empty();
            ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
            nextEquipment.set(equipmentIndex, new Slot(slot, Optional.of(new Occupant(containerToken, updated.orElseThrow()))));
            return Optional.of(new LiveHands(hands, active, revision + 1, Optional.of(nextEquipment)));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Atomic worn-to-worn move; the child never occupies a hand or an intermediate attachment. */
    Optional<LiveHands> moveEquippedGridBetweenHosts(long expectedRevision, List<String> declared,
            String sourceSlot, String sourceHostToken, String destinationSlot, String destinationHostToken,
            String childToken, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        if (sourceSlot == null || destinationSlot == null || sourceSlot.equals(destinationSlot)
                || sourceHostToken == null || destinationHostToken == null || childToken == null
                || sourceHostToken.equals(destinationHostToken) || oldAnchor == null || oldRotation == null
                || newAnchor == null || newRotation == null
                || !validEquippedStorage(expectedRevision, declared, sourceSlot, sourceHostToken)
                || !validEquippedStorage(expectedRevision, declared, destinationSlot, destinationHostToken))
            return Optional.empty();
        int from = declared.indexOf(sourceSlot), to = declared.indexOf(destinationSlot);
        Occupant source = equipmentSlot(from).occupant().orElseThrow();
        Occupant destination = equipmentSlot(to).occupant().orElseThrow();
        Optional<GridContents> sourceGrid = GridContents.read(source.stack());
        Optional<GridContents> destinationGrid = GridContents.read(destination.stack());
        if (sourceGrid.isEmpty() || destinationGrid.isEmpty()) return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) if (!tokens.add(occupant.token())) return Optional.empty();
        GridContents.Entry child = null;
        for (GridContents.Entry entry : sourceGrid.orElseThrow().entries()) {
            if (!tokens.add(entry.token())) return Optional.empty();
            if (entry.token().equals(childToken)) {
                if (entry.x() != oldAnchor.x() || entry.y() != oldAnchor.y() || entry.rotation() != oldRotation)
                    return Optional.empty();
                child = entry;
            }
        }
        for (GridContents.Entry entry : destinationGrid.orElseThrow().entries())
            if (!tokens.add(entry.token())) return Optional.empty();
        if (child == null) return Optional.empty();
        try {
            List<GridContents.Entry> remaining = new ArrayList<>(sourceGrid.orElseThrow().entries());
            GridContents.Entry selected = child;
            remaining.removeIf(entry -> entry.token().equals(selected.token()));
            List<GridContents.Entry> appended = new ArrayList<>(destinationGrid.orElseThrow().entries());
            appended.add(new GridContents.Entry(child.token(), child.stack(), newAnchor.x(), newAnchor.y(), newRotation));
            Optional<ItemStack> newSource = GridContents.applyCopyWithGrid(source.stack(), GridContents.of(remaining));
            Optional<ItemStack> newDestination = GridContents.applyCopyWithGrid(destination.stack(), GridContents.of(appended));
            if (newSource.isEmpty() || newDestination.isEmpty()) return Optional.empty();
            ArrayList<Slot> next = new ArrayList<>(equipment.orElseThrow());
            next.set(from, new Slot(sourceSlot, Optional.of(new Occupant(sourceHostToken, newSource.orElseThrow()))));
            next.set(to, new Slot(destinationSlot, Optional.of(new Occupant(destinationHostToken, newDestination.orElseThrow()))));
            return Optional.of(new LiveHands(hands, active, revision + 1, Optional.of(next)));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Identity includes ownership kind, declared location and occupant token. */
    public record GridHost(boolean worn, String id, String token) { }

    /** Detached removal from a body-owned grid; the caller must publish both owners. */
    public Optional<LiveHands> removeGridChild(long expectedRevision, List<String> declared, GridHost host,
            String childToken, GridGeometry.Cell anchor, GridGeometry.Rotation rotation) {
        if (!validEquipmentTransition(expectedRevision, declared) || host == null || childToken == null
                || anchor == null || rotation == null) return Optional.empty();
        Occupant owner = gridHostOccupant(host, declared);
        if (owner == null) return Optional.empty();
        var grid = GridContents.read(owner.stack());
        if (grid.isEmpty() || grid.orElseThrow().entries().stream().noneMatch(e -> e.token().equals(childToken)
                && e.x() == anchor.x() && e.y() == anchor.y() && e.rotation() == rotation)) return Optional.empty();
        try {
            var remaining = grid.orElseThrow().entries().stream().filter(e -> !e.token().equals(childToken)).toList();
            var updated = GridContents.applyCopyWithGrid(owner.stack(), GridContents.of(remaining));
            if (updated.isEmpty()) return Optional.empty();
            var nextHands = new ArrayList<>(hands);
            var nextEquipment = new ArrayList<>(equipment.orElseThrow());
            setGridHost(nextHands, nextEquipment, host, updated.orElseThrow());
            return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
        } catch (IllegalArgumentException failure) { return Optional.empty(); }
    }

    /** Detached insertion; checks the token against every body-owned occupant and grid child. */
    public Optional<LiveHands> addGridChild(long expectedRevision, List<String> declared, GridHost host,
            String childToken, ItemStack child, GridGeometry.Cell anchor, GridGeometry.Rotation rotation) {
        if (!validEquipmentTransition(expectedRevision, declared) || host == null || childToken == null
                || child == null || anchor == null || rotation == null) return Optional.empty();
        Occupant owner = gridHostOccupant(host, declared);
        if (owner == null) return Optional.empty();
        var grid = GridContents.read(owner.stack());
        if (grid.isEmpty()) return Optional.empty();
        for (Occupant occupant : allOccupants()) {
            if (occupant.token().equals(childToken)) return Optional.empty();
            if (occupant.stack().is(ModItems.BAG) || occupant.stack().is(ModItems.BELT)) {
                var contents = GridContents.read(occupant.stack());
                if (contents.isEmpty() || contents.orElseThrow().entries().stream().anyMatch(e -> e.token().equals(childToken)))
                    return Optional.empty();
            }
        }
        try {
            var entries = new ArrayList<>(grid.orElseThrow().entries());
            entries.add(new GridContents.Entry(childToken, child, anchor.x(), anchor.y(), rotation));
            var updated = GridContents.applyCopyWithGrid(owner.stack(), GridContents.of(entries));
            if (updated.isEmpty()) return Optional.empty();
            var nextHands = new ArrayList<>(hands);
            var nextEquipment = new ArrayList<>(equipment.orElseThrow());
            setGridHost(nextHands, nextEquipment, host, updated.orElseThrow());
            return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
        } catch (IllegalArgumentException failure) { return Optional.empty(); }
    }

    Optional<LiveHands> moveBodyGrid(long expectedRevision, List<String> declared, GridHost sourceHost,
            GridHost destinationHost, String childToken, GridGeometry.Cell oldAnchor,
            GridGeometry.Rotation oldRotation, GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || sourceHost == null || destinationHost == null
                || sourceHost.equals(destinationHost) || childToken == null || oldAnchor == null || oldRotation == null
                || newAnchor == null || newRotation == null || !validEquipmentTransition(expectedRevision, declared))
            return Optional.empty();
        Occupant source = gridHostOccupant(sourceHost, declared);
        Occupant destination = gridHostOccupant(destinationHost, declared);
        if (source == null || destination == null || sourceHost.token().equals(destinationHost.token())) return Optional.empty();
        var fromGrid = GridContents.read(source.stack());
        var toGrid = GridContents.read(destination.stack());
        if (fromGrid.isEmpty() || toGrid.isEmpty()) return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) if (!tokens.add(occupant.token())) return Optional.empty();
        // Check all body-owned grids, not only the two selected hosts.
        for (Occupant occupant : allOccupants()) {
            if (!occupant.stack().is(ModItems.BAG) && !occupant.stack().is(ModItems.BELT)) continue;
            var grid = GridContents.read(occupant.stack());
            if (grid.isEmpty()) return Optional.empty();
            for (GridContents.Entry entry : grid.orElseThrow().entries())
                if (!tokens.add(entry.token())) return Optional.empty();
        }
        GridContents.Entry child = fromGrid.orElseThrow().entries().stream()
                .filter(entry -> entry.token().equals(childToken) && entry.x() == oldAnchor.x()
                        && entry.y() == oldAnchor.y() && entry.rotation() == oldRotation).findFirst().orElse(null);
        if (child == null) return Optional.empty();
        try {
            List<GridContents.Entry> remaining = new ArrayList<>(fromGrid.orElseThrow().entries());
            remaining.removeIf(entry -> entry.token().equals(childToken));
            List<GridContents.Entry> appended = new ArrayList<>(toGrid.orElseThrow().entries());
            appended.add(new GridContents.Entry(child.token(), child.stack(), newAnchor.x(), newAnchor.y(), newRotation));
            var newSource = GridContents.applyCopyWithGrid(source.stack(), GridContents.of(remaining));
            var newDestination = GridContents.applyCopyWithGrid(destination.stack(), GridContents.of(appended));
            if (newSource.isEmpty() || newDestination.isEmpty()) return Optional.empty();
            ArrayList<Slot> nextHands = new ArrayList<>(hands);
            ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
            setGridHost(nextHands, nextEquipment, sourceHost, newSource.orElseThrow());
            setGridHost(nextHands, nextEquipment, destinationHost, newDestination.orElseThrow());
            return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    private Occupant gridHostOccupant(GridHost host, List<String> declared) {
        if (host.id() == null || host.token() == null) return null;
        if (host.worn() && !(host.id().equals("back") || host.id().equals("belt"))) return null;
        int index = host.worn() ? declared.indexOf(host.id()) : handIds().indexOf(host.id());
        if (index < 0 || host.worn() && equipment.isEmpty()) return null;
        return (host.worn() ? equipment.orElseThrow() : hands).get(index).occupant()
                .filter(o -> o.token().equals(host.token()))
                .filter(o -> host.worn() ? host.id().equals("back") && o.stack().is(ModItems.BAG)
                        || host.id().equals("belt") && o.stack().is(ModItems.BELT)
                        : o.stack().is(ModItems.BAG) || o.stack().is(ModItems.BELT)).orElse(null);
    }

    private static void setGridHost(ArrayList<Slot> hands, ArrayList<Slot> equipment, GridHost host, ItemStack stack) {
        List<Slot> slots = host.worn() ? equipment : hands;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).id().equals(host.id())) {
            slots.set(i, new Slot(host.id(), Optional.of(new Occupant(host.token(), stack))));
            return;
        }
    }

    /** Store a whole child from the other hand in a bag/belt held in hostHand. */
    Optional<LiveHands> insertHeldGrid(long expectedRevision, String hostHand, String hostToken,
                                       String sourceHand, String childToken,
                                       GridGeometry.Cell anchor, GridGeometry.Rotation rotation) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || hostHand == null || sourceHand == null || hostHand.equals(sourceHand)
                || hostToken == null || childToken == null || anchor == null || rotation == null) return Optional.empty();
        int hostIndex = handIds().indexOf(hostHand), sourceIndex = handIds().indexOf(sourceHand);
        if (hostIndex < 0 || sourceIndex < 0 || hostToken.equals(childToken)) return Optional.empty();
        Optional<Occupant> host = hands.get(hostIndex).occupant(), source = hands.get(sourceIndex).occupant();
        if (host.isEmpty() || source.isEmpty() || !host.orElseThrow().token().equals(hostToken)
                || !source.orElseThrow().token().equals(childToken)
                || allOccupants().stream().filter(v -> v.token().equals(childToken)).count() != 1) return Optional.empty();
        ItemStack bag = host.orElseThrow().stack();
        Optional<GridContents> contents = GridContents.read(bag);
        if (contents.isEmpty() || contents.orElseThrow().entries().stream().anyMatch(e -> e.token().equals(childToken)))
            return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) tokens.add(occupant.token());
        for (GridContents.Entry entry : contents.orElseThrow().entries()) if (!tokens.add(entry.token())) return Optional.empty();
        try {
            List<GridContents.Entry> entries = new ArrayList<>(contents.orElseThrow().entries());
            entries.add(new GridContents.Entry(childToken, source.orElseThrow().stack(), anchor.x(), anchor.y(), rotation));
            Optional<ItemStack> updated = GridContents.applyCopyWithGrid(bag, GridContents.of(entries));
            if (updated.isEmpty()) return Optional.empty();
            ArrayList<Slot> next = new ArrayList<>(hands);
            next.set(hostIndex, new Slot(hostHand, Optional.of(new Occupant(hostToken, updated.orElseThrow()))));
            next.set(sourceIndex, new Slot(sourceHand, Optional.empty()));
            return Optional.of(new LiveHands(next, active, revision + 1, equipment));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Take a child from a held bag/belt into the designated other empty hand. */
    Optional<LiveHands> extractHeldGrid(long expectedRevision, String hostHand, String hostToken,
                                        String destinationHand, String childToken, GridGeometry.Cell anchor) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || hostHand == null || destinationHand == null || hostHand.equals(destinationHand)
                || hostToken == null || childToken == null || anchor == null || hostToken.equals(childToken))
            return Optional.empty();
        int hostIndex = handIds().indexOf(hostHand), destinationIndex = handIds().indexOf(destinationHand);
        if (hostIndex < 0 || destinationIndex < 0 || hands.get(destinationIndex).occupant().isPresent()
                || hands.get(destinationIndex).reservation().isPresent()) return Optional.empty();
        Optional<Occupant> host = hands.get(hostIndex).occupant();
        if (host.isEmpty() || !host.orElseThrow().token().equals(hostToken)
                || allOccupants().stream().anyMatch(v -> v.token().equals(childToken))) return Optional.empty();
        ItemStack bag = host.orElseThrow().stack();
        Optional<GridContents> contents = GridContents.read(bag);
        if (contents.isEmpty()) return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) if (!tokens.add(occupant.token())) return Optional.empty();
        for (GridContents.Entry entry : contents.orElseThrow().entries()) if (!tokens.add(entry.token())) return Optional.empty();
        GridContents.Entry child = contents.orElseThrow().entries().stream()
                .filter(e -> e.token().equals(childToken) && e.x() == anchor.x() && e.y() == anchor.y()).findFirst().orElse(null);
        if (child == null) return Optional.empty();
        List<GridContents.Entry> entries = contents.orElseThrow().entries().stream().filter(e -> !e.token().equals(childToken)).toList();
        Optional<ItemStack> updated = GridContents.applyCopyWithGrid(bag, GridContents.of(entries));
        if (updated.isEmpty()) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(hostIndex, new Slot(hostHand, Optional.of(new Occupant(hostToken, updated.orElseThrow()))));
        next.set(destinationIndex, new Slot(destinationHand, Optional.of(new Occupant(childToken, child.stack()))));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Relocate/rotate a child within a bag/belt held in a hand. */
    Optional<LiveHands> moveHeldGrid(long expectedRevision, String hostHand, String hostToken, String childToken,
                                     GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                                     GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hostHand == null || hostToken == null
                || childToken == null || oldAnchor == null || oldRotation == null || newAnchor == null || newRotation == null)
            return Optional.empty();
        int hostIndex = handIds().indexOf(hostHand);
        if (hostIndex < 0) return Optional.empty();
        Optional<Occupant> host = hands.get(hostIndex).occupant();
        if (host.isEmpty() || !host.orElseThrow().token().equals(hostToken) || hostToken.equals(childToken)) return Optional.empty();
        ItemStack bag = host.orElseThrow().stack();
        Optional<GridContents> contents = GridContents.read(bag);
        if (contents.isEmpty()) return Optional.empty();
        Set<String> tokens = new HashSet<>();
        for (Occupant occupant : allOccupants()) if (!tokens.add(occupant.token())) return Optional.empty();
        List<GridContents.Entry> entries = new ArrayList<>(contents.orElseThrow().entries());
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            GridContents.Entry entry = entries.get(i);
            if (!tokens.add(entry.token())) return Optional.empty();
            if (entry.token().equals(childToken)) {
                if (entry.x() != oldAnchor.x() || entry.y() != oldAnchor.y() || entry.rotation() != oldRotation)
                    return Optional.empty();
                index = i;
            }
        }
        if (index < 0) return Optional.empty();
        GridContents.Entry old = entries.get(index);
        if (old.x() == newAnchor.x() && old.y() == newAnchor.y() && old.rotation() == newRotation) return Optional.empty();
        try {
            entries.set(index, new GridContents.Entry(old.token(), old.stack(), newAnchor.x(), newAnchor.y(), newRotation));
            Optional<ItemStack> updated = GridContents.applyCopyWithGrid(bag, GridContents.of(entries));
            if (updated.isEmpty()) return Optional.empty();
            ArrayList<Slot> next = new ArrayList<>(hands);
            next.set(hostIndex, new Slot(hostHand, Optional.of(new Occupant(hostToken, updated.orElseThrow()))));
            return Optional.of(new LiveHands(next, active, revision + 1, equipment));
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Pure internal rearrangement, never a source-to-hand insertion or hand-to-world extraction. */
    public Optional<LiveHands> move(long expectedRevision, String source, String target) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || Objects.equals(source, target))
            return Optional.empty();
        int from = handIds().indexOf(source);
        int to = handIds().indexOf(target);
        if (from < 0 || to < 0 || hands.get(from).occupant().isEmpty() || hands.get(to).occupant().isPresent())
            return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(to, new Slot(target, hands.get(from).occupant()));
        next.set(from, new Slot(source, Optional.empty()));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    private List<Occupant> allOccupants() {
        return java.util.stream.Stream.concat(hands.stream(), equipment.orElse(List.of()).stream())
                .flatMap(slot -> slot.occupant().stream()).toList();
    }

    private boolean handReserved(int index) { return hands.get(index).reservation().isPresent(); }

    /** Runtime entry point: the body must own both the hand and the exact declared equipment layout. */
    Optional<LiveHands> equip(long expectedRevision, Entity body, String hand, String slot, String token) {
        return compatible(body) ? hostEquipmentIds(body).flatMap(ids -> equip(expectedRevision, ids, hand, slot, token))
                : Optional.empty();
    }

    Optional<LiveHands> unequip(long expectedRevision, Entity body, String slot, String hand, String token) {
        return compatible(body) ? hostEquipmentIds(body).flatMap(ids -> unequip(expectedRevision, ids, slot, hand, token))
                : Optional.empty();
    }

    /** Pure fixture seam; callers publishing a result must first verify the bound body. */
    Optional<LiveHands> equip(long expectedRevision, List<String> declared, String hand, String slot, String token) {
        if (!validEquipmentTransition(expectedRevision, declared)) return Optional.empty();
        int from = handIds().indexOf(hand), to = declared.indexOf(slot);
        if (from < 0 || to < 0 || materializedEquipment(declared).get(to).occupant().isPresent()) return Optional.empty();
        Optional<Occupant> occupant = hands.get(from).occupant();
        if (occupant.isEmpty() || token == null || !occupant.orElseThrow().token().equals(token)) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(from, new Slot(hand, Optional.empty()));
        ArrayList<Slot> nextEquipment = new ArrayList<>(materializedEquipment(declared));
        nextEquipment.set(to, new Slot(slot, occupant));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    Optional<LiveHands> unequip(long expectedRevision, List<String> declared, String slot, String hand, String token) {
        if (!validEquipmentTransition(expectedRevision, declared) || equipment.isEmpty()) return Optional.empty();
        int from = declared.indexOf(slot), to = handIds().indexOf(hand);
        if (from < 0 || to < 0 || hands.get(to).occupant().isPresent()) return Optional.empty();
        Optional<Occupant> occupant = equipmentSlot(from).occupant();
        if (occupant.isEmpty() || token == null || !occupant.orElseThrow().token().equals(token)) return Optional.empty();
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(from, new Slot(slot, Optional.empty()));
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(to, new Slot(hand, occupant));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    private Slot equipmentSlot(int index) { return equipment.orElseThrow().get(index); }

    private List<Slot> materializedEquipment(List<String> declared) {
        return equipment.orElseGet(() -> declared.stream().map(id -> new Slot(id, Optional.empty())).toList());
    }

    private boolean validEquipmentTransition(long expectedRevision, List<String> declared) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || declared == null || declared.isEmpty()
                || declared.size() > EquipmentSlotsPrototypeComponent.MAX_SLOTS
                || !EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.containsAll(declared)
                || new HashSet<>(declared).size() != declared.size()) return false;
        return equipment.isEmpty() || equipmentIds().equals(declared);
    }

    private record Raw(List<Slot> hands, String active, long revision, Optional<List<Slot>> equipment) { }
    private record RawOccupant(String token, ItemStack stack) { }

    private record Slot(String id, Optional<Occupant> occupant, Optional<java.util.UUID> reservation) {
        private Slot(String id, Optional<Occupant> occupant) { this(id, occupant, Optional.empty()); }
        private Slot {
            if (id == null || id.isBlank() || id.length() > HandState.MAX_ID_LENGTH || occupant == null || reservation == null
                    || occupant.isPresent() && reservation.isPresent())
                throw new IllegalArgumentException("invalid slot");
        }
    }

    private record Occupant(String token, ItemStack stack) {
        private Occupant {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH
                    || stack == null || stack.isEmpty() || stack.getCount() <= 0
                    || stack.getCount() > stack.getMaxStackSize())
                throw new IllegalArgumentException("invalid occupied stack");
            stack = stack.copy();
        }

        @Override public ItemStack stack() { return stack.copy(); }
    }
}
