package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.Optional;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LiveHandsTest {
    private static final ItemToken TOKEN = new ItemToken("live-token");
    private java.util.Map<ResourceLocation, ItemFootprintProfile> previousFootprints;

    @BeforeEach
    void provideLoadedFootprintCatalogForGridFixtures() throws IOException {
        var manager = PrototypeRuntime.serverManager();
        var current = manager.snapshot(ModItemFootprints.TYPE);
        previousFootprints = current.asMap();
        manager.reload(ModItemFootprints.TYPE, loadFootprintResources());
    }

    @AfterEach
    void restoreFootprintCatalog() {
        if (previousFootprints != null)
            PrototypeRuntime.serverManager().publishDecoded(ModItemFootprints.TYPE, previousFootprints);
        previousFootprints = null;
    }

    private static java.util.Map<ResourceLocation, JsonObject> loadFootprintResources() throws IOException {
        String rootName = "data/moonstation14/moonstation14/item_footprint";
        var root = LiveHandsTest.class.getClassLoader().getResource(rootName);
        if (root == null) throw new IOException("Missing item footprint resources");
        Path directory;
        if ("file".equals(root.getProtocol())) directory = Path.of(URI.create(root.toString()));
        else if ("union".equals(root.getProtocol())) {
            String value = root.toString();
            int marker = value.indexOf("/build/");
            if (marker < 0) throw new IOException("Cannot locate item footprint resources");
            String project = URLDecoder.decode(value.substring("union:".length(), marker), StandardCharsets.UTF_8);
            if (project.startsWith("/") && project.length() > 2 && project.charAt(2) == ':') project = project.substring(1);
            directory = Path.of(project).resolve("src/main/resources").resolve(rootName);
        } else throw new IOException("Unsupported item footprint resource URL: " + root);
        var result = new LinkedHashMap<ResourceLocation, JsonObject>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = path.getFileName().toString();
                try (var reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    result.put(ResourceLocation.fromNamespaceAndPath("moonstation14", name.substring(0, name.length() - 5)),
                            JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static LiveHands parse(String json) {
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void emptyLayoutHasIndependentOrderedIdentityAndRevision() {
        LiveHands state = parse("{" +
                "\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}]," +
                "\"active\":\"right\",\"revision\":12}");
        assertEquals(java.util.List.of("left", "right"), state.handIds());
        assertEquals("right", state.activeHand());
        assertEquals(12, state.revision());
        assertTrue(state.stackCopy("left").isEmpty());
        assertTrue(state.move(12, "left", "right").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.stackCopy("missing"));
        assertEquals(state.handIds(), LiveHands.CODEC.parse(JsonOps.INSTANCE,
                LiveHands.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow().handIds());
    }

    @Test
    void invalidLayoutsFailAtCodecBoundary() {
        for (String hands : new String[]{"[]", "[{\"id\":\"left\"},{\"id\":\"left\"}]",
                "[{\"id\":\"\"}]", "[{\"id\":\"left\",\"occupant\":{\"token\":\"x\"}}]"}) {
            assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"hands\":" + hands + ",\"active\":\"left\",\"revision\":0}")).error().isPresent());
        }
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"other\",\"revision\":0}"))
                .error().isPresent());
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":-1}"))
                .error().isPresent());
    }

    @Test
    void wholeTransitionsCopyBothWaysAndKeepActiveHandIndependent() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":3}");
        ItemStack source = new ItemStack(Items.DIAMOND, 7);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        LiveHands.WholeResult placed = empty.putWhole(3, "left", TOKEN, source);
        assertTrue(placed.succeeded());
        LiveHands occupied = placed.state();
        source.setCount(1);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertEquals(7, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals("original", occupied.stackCopy("left").orElseThrow().getHoverName().getString());
        assertEquals(Optional.of(TOKEN.value()), occupied.token("left"));
        assertEquals(4, occupied.revision());
        assertEquals("right", occupied.activeHand());
        assertEquals("right", empty.activeHand());
        assertTrue(empty.stackCopy("left").isEmpty());

        LiveHands.WholeResult taken = occupied.takeWhole(4, "left", TOKEN);
        assertTrue(taken.succeeded());
        ItemStack firstRead = taken.takenStack().orElseThrow();
        assertEquals(7, firstRead.getCount());
        assertEquals("original", firstRead.getHoverName().getString());
        firstRead.setCount(2);
        firstRead.set(DataComponents.CUSTOM_NAME, Component.literal("mutated return"));
        assertEquals(7, taken.takenStack().orElseThrow().getCount());
        assertEquals("original", taken.takenStack().orElseThrow().getHoverName().getString());
        assertTrue(taken.state().stackCopy("left").isEmpty());
        assertEquals(5, taken.state().revision());
        assertEquals("right", taken.state().activeHand());
        assertEquals(7, occupied.stackCopy("left").orElseThrow().getCount());
    }

    @Test
    void wholeTransitionsRejectWithoutChangingSnapshots() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack source = new ItemStack(Items.DIAMOND, 4);
        assertRejected(empty.putWhole(1, "left", TOKEN, source), empty, LiveHands.Rejection.STALE_REVISION);
        assertRejected(empty.putWhole(0, "missing", TOKEN, source), empty, LiveHands.Rejection.UNKNOWN_HAND);
        assertRejected(empty.takeWhole(0, "left", TOKEN), empty, LiveHands.Rejection.HAND_EMPTY);
        assertRejected(empty.putWhole(0, "left", TOKEN, ItemStack.EMPTY), empty, LiveHands.Rejection.INVALID_STACK);
        assertRejected(empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 65)),
                empty, LiveHands.Rejection.INVALID_STACK);
        LiveHands occupied = empty.putWhole(0, "left", TOKEN, source).state();
        assertRejected(occupied.putWhole(1, "left", new ItemToken("other"), source),
                occupied, LiveHands.Rejection.HAND_OCCUPIED);
        assertRejected(occupied.putWhole(1, "right", TOKEN, source), occupied, LiveHands.Rejection.DUPLICATE_TOKEN);
        assertRejected(occupied.takeWhole(0, "left", TOKEN), occupied, LiveHands.Rejection.STALE_REVISION);
        assertRejected(occupied.takeWhole(1, "missing", TOKEN), occupied, LiveHands.Rejection.UNKNOWN_HAND);
        assertRejected(occupied.takeWhole(1, "left", new ItemToken("other")),
                occupied, LiveHands.Rejection.TOKEN_MISMATCH);
        assertRejected(occupied.takeWhole(1, "right", TOKEN), occupied, LiveHands.Rejection.HAND_EMPTY);
        assertEquals(4, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, occupied.revision());
        assertEquals(4, source.getCount());

        LiveHands max = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":9223372036854775807}");
        assertRejected(max.putWhole(Long.MAX_VALUE, "left", TOKEN, source), max,
                LiveHands.Rejection.REVISION_OVERFLOW);
        assertRejected(max.takeWhole(Long.MAX_VALUE, "left", TOKEN), max,
                LiveHands.Rejection.REVISION_OVERFLOW);
    }

    private static void assertRejected(LiveHands.WholeResult result, LiveHands state, LiveHands.Rejection reason) {
        assertFalse(result.succeeded());
        assertSame(state, result.state());
        assertEquals(Optional.of(reason), result.rejection());
        assertTrue(result.takenStack().isEmpty());
    }

    @Test
    void stagedCraftConsumesMultipleUnitsAndUsesFreedSourceHand() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":4}");
        ItemStack steel = new ItemStack(Items.IRON_INGOT, 3);
        LiveHands stocked = empty.putWhole(4, "left", new ItemToken("steel-owner"), steel).state();
        ItemStack rods = new ItemStack(Items.STICK, 2);
        LiveHands crafted = stocked.consumeAndPlace(5,"left","steel-owner",steel,3,rods,"rod-output").orElseThrow();
        assertTrue(crafted.stackCopy("left").orElseThrow().is(Items.STICK));
        assertEquals(2, crafted.stackCopy("left").orElseThrow().getCount());
        assertEquals("rod-output", crafted.token("left").orElseThrow());
        assertTrue(crafted.stackCopy("right").isEmpty());
        // The recipe consumes all three units; the source snapshot remains unchanged.
        assertEquals(3, stocked.stackCopy("left").orElseThrow().getCount());
        assertEquals(6, crafted.revision());
    }

    @Test
    void stagedCraftMergesMatchingOutputAndCapacityFailureIsNoMutation() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        LiveHands stocked = empty.putWhole(0,"left",new ItemToken("steel"),new ItemStack(Items.IRON_INGOT)).state();
        stocked = stocked.putWhole(1,"right",new ItemToken("sticks"),new ItemStack(Items.STICK, 5)).state();
        var merged = stocked.consumeAndPlace(2,"left","steel",new ItemStack(Items.IRON_INGOT),1,
                new ItemStack(Items.STICK,2),"new-rods").orElseThrow();
        assertEquals(7,merged.stackCopy("right").orElseThrow().getCount());
        assertEquals("sticks",merged.token("right").orElseThrow());
        assertTrue(merged.stackCopy("left").isEmpty());

        // One unit would be fully consumed and free the source hand for output.
        // Two units leave the source occupied, making this an actual capacity failure.
        LiveHands full = empty.putWhole(0,"left",new ItemToken("steel"),new ItemStack(Items.IRON_INGOT, 2)).state()
                .putWhole(1,"right",new ItemToken("other"),new ItemStack(Items.DIAMOND)).state();
        assertTrue(full.consumeAndPlace(2,"left","steel",new ItemStack(Items.IRON_INGOT,2),1,
                new ItemStack(Items.STICK),"out").isEmpty());
        assertEquals(2,full.stackCopy("left").orElseThrow().getCount());
        assertEquals(2,full.revision());
    }

    @Test
    void stagedCraftRejectsStaleAndMismatchedSourceWithoutChangingInput() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        LiveHands stocked = empty.putWhole(0,"left",new ItemToken("steel"),new ItemStack(Items.IRON_INGOT,2)).state();
        assertTrue(stocked.consumeAndPlace(0,"left","steel",new ItemStack(Items.IRON_INGOT,2),1,
                new ItemStack(Items.STICK),"out").isEmpty());
        assertTrue(stocked.consumeAndPlace(1,"left","wrong",new ItemStack(Items.IRON_INGOT,2),1,
                new ItemStack(Items.STICK),"out").isEmpty());
        assertTrue(stocked.consumeAndPlace(1,"left","steel",new ItemStack(Items.IRON_INGOT,3),1,
                new ItemStack(Items.STICK),"out").isEmpty());
        assertEquals(2,stocked.stackCopy("left").orElseThrow().getCount());
    }

    @Test
    void worldCraftOutputPreflightDoesNotMutateAndChecksCapacity() {
        LiveHands empty=parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        LiveHands full=empty.putWhole(0,"left",new ItemToken("a"),new ItemStack(Items.DIAMOND)).state()
                .putWhole(1,"right",new ItemToken("b"),new ItemStack(Items.EMERALD)).state();
        assertTrue(full.placeCraftOutput(2,new ItemStack(Items.STICK),"out").isEmpty());
        assertEquals(2,full.revision());
        assertTrue(full.stackCopy("left").orElseThrow().is(Items.DIAMOND));
        var staged=full.placeCraftOutput(2,new ItemStack(Items.DIAMOND),"unused").orElseThrow();
        assertEquals(1,full.stackCopy("left").orElseThrow().getCount());
        assertEquals(2,staged.stackCopy("left").orElseThrow().getCount());
        assertEquals(3,staged.revision());
    }

    @Test
    void worldOutputFitsWornGridWithoutChangingInputSnapshot() {
        LiveHands empty=parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack bag=GridContents.applyCopyWithGrid(new ItemStack(ModItems.BAG.get()),GridContents.empty()).orElseThrow();
        LiveHands state=empty.putWhole(0,"left",new ItemToken("bag"),bag).state();
        state=state.equip(1,java.util.List.of("belt","back"),"left","back","bag").orElseThrow();
        state=state.putWhole(2,"left",new ItemToken("l"),new ItemStack(Items.DIAMOND)).state()
                .putWhole(3,"right",new ItemToken("r"),new ItemStack(Items.EMERALD)).state();
        var placed=state.placeCraftOutput(4,new ItemStack(Items.STICK,2),"rods").orElseThrow();
        assertEquals(4,state.revision());
        assertTrue(GridContents.read(state.equipmentStack("back").orElseThrow()).orElseThrow().entries().isEmpty());
        assertTrue(GridContents.read(placed.equipmentStack("back").orElseThrow()).orElseThrow().entries()
                .stream().anyMatch(e->e.token().equals("rods")&&e.stack().getCount()==2));
        assertEquals(5,placed.revision());
    }

    @Test
    void adequateHandMaterialCapacityFailureDoesNotFallThroughToWornGridSource() {
        LiveHands state=parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack bag=GridContents.applyCopyWithGrid(new ItemStack(ModItems.BAG.get()),GridContents.empty()).orElseThrow();
        var packed = new java.util.ArrayList<GridContents.Entry>();
        packed.add(new GridContents.Entry("grid-steel", new ItemStack(Items.IRON_INGOT), 0, 0,
                GridGeometry.Rotation.DEG_0));
        for (int y = 0; y < 4; y++) for (int x = 0; x < 7; x++)
            if (x != 0 || y != 0) packed.add(new GridContents.Entry("grid-fill-" + x + "-" + y,
                    new ItemStack(Items.DIAMOND), x, y, GridGeometry.Rotation.DEG_0));
        bag = GridContents.applyCopyWithGrid(bag, GridContents.of(packed)).orElseThrow();
        state=state.putWhole(0,"left",new ItemToken("bag"),bag).state();
        state=state.equip(1,java.util.List.of("belt","back"),"left","back","bag").orElseThrow();
        state=state.putWhole(2,"left",new ItemToken("hand-steel"),new ItemStack(Items.IRON_INGOT,2)).state();
        state=state.putWhole(3,"right",new ItemToken("full"),new ItemStack(Items.DIAMOND)).state();
        assertTrue(state.consumeBodyMaterial(4,s->s.is(Items.IRON_INGOT),1,new ItemStack(Items.STICK),"out").isEmpty());
        assertEquals(2,state.stackCopy("left").orElseThrow().getCount());
        assertTrue(GridContents.read(state.equipmentStack("back").orElseThrow()).orElseThrow().entries()
                .stream().anyMatch(e->e.token().equals("grid-steel")));
    }

    @Test
    void bodyMaterialSelectionConsumesWornGridChildAndFitsOutputIntoFreedGridCell() {
        LiveHands empty=parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack bag=new ItemStack(ModItems.BAG.get());
        bag=GridContents.applyCopyWithGrid(bag,GridContents.empty()).orElseThrow();
        LiveHands state=empty.putWhole(0,"left",new ItemToken("bag"),bag).state();
        state=state.equip(1,java.util.List.of("belt","back"),"left","back","bag").orElseThrow();
        state=state.addGridChild(2,java.util.List.of("belt","back"),new LiveHands.GridHost(true,"back","bag"),
                "steel",new ItemStack(Items.IRON_INGOT),new GridGeometry.Cell(0,0),GridGeometry.Rotation.DEG_0).orElseThrow();
        state=state.putWhole(3,"left",new ItemToken("occupied-left"),new ItemStack(Items.DIAMOND)).state();
        state=state.putWhole(4,"right",new ItemToken("occupied-right"),new ItemStack(Items.EMERALD)).state();
        var selection=state.consumeBodyMaterial(5,s->s.is(Items.IRON_INGOT),1,new ItemStack(Items.STICK,2),"rods").orElseThrow();
        LiveHands result=selection.state();
        assertTrue(selection.source().wornGrid());
        assertEquals("back",selection.source().hostId());
        assertEquals("bag",selection.source().hostToken());
        assertEquals("steel",selection.source().childToken());
        assertEquals(6,result.revision());
        var contents=GridContents.read(result.equipmentStack("back").orElseThrow());
        assertTrue(contents.orElseThrow().entries().stream().anyMatch(e->e.token().equals("rods")&&e.stack().getCount()==2));
        assertTrue(contents.orElseThrow().entries().stream().noneMatch(e->e.token().equals("steel")));
        assertTrue(state.consumeBodyMaterial(4,s->s.is(Items.IRON_INGOT),1,new ItemStack(Items.STICK,2),"stale").isEmpty());
        assertEquals(1,GridContents.read(state.equipmentStack("back").orElseThrow()).orElseThrow().entries().size(),
                "stale host/child snapshot leaves original material in place");
        assertEquals(5,state.revision());
    }

    @Test
    void componentReplacementPreservesOwnedItemAndToken() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":4}");
        LiveHands owned = empty.putWhole(4, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state();
        ItemStack changed = new ItemStack(Items.DIAMOND, 3);
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("liquid"));
        LiveHands updated = owned.replaceOwnedComponents(5, "left", TOKEN, changed).orElseThrow();
        assertEquals(6, updated.revision());
        assertEquals(Optional.of(TOKEN.value()), updated.token("left"));
        assertEquals("liquid", updated.stackCopy("left").orElseThrow().getHoverName().getString());
        assertTrue(owned.replaceOwnedComponents(5, "left", TOKEN, new ItemStack(Items.EMERALD, 3)).isEmpty());
        assertTrue(owned.replaceOwnedComponents(5, "left", TOKEN, new ItemStack(Items.DIAMOND, 2)).isEmpty());
    }

    @Test
    void clearHeldWholeRemovesEntireCountAndPreservesOtherOwners() {
        LiveHands state = parse("{\"hands\":[{\"id\":\"left\",\"occupant\":{\"token\":\"owned\",\"stack\":{\"id\":\"minecraft:diamond\",\"count\":5}}},{\"id\":\"right\",\"occupant\":{\"token\":\"other\",\"stack\":{\"id\":\"minecraft:iron_ingot\",\"count\":2}}}],\"active\":\"right\",\"revision\":4,\"equipment\":[{\"id\":\"gloves\",\"occupant\":{\"token\":\"gear\",\"stack\":{\"id\":\"minecraft:leather_chestplate\",\"count\":1}}}]}");
        LiveHands cleared = state.clearHeldWhole(4, "left", new ItemToken("owned")).orElseThrow();
        assertTrue(cleared.stackCopy("left").isEmpty());
        assertEquals(2, cleared.stackCopy("right").orElseThrow().getCount());
        assertEquals(1, cleared.equipmentStack("gloves").orElseThrow().getCount());
        assertEquals(5, state.stackCopy("left").orElseThrow().getCount());
        assertTrue(state.clearHeldWhole(4, "left", new ItemToken("wrong")).isEmpty());
        assertTrue(state.clearHeldWhole(5, "left", new ItemToken("owned")).isEmpty());
    }

    @Test
    void equipmentComponentReplacementPreservesIdentityAndRejectsStaleOrChangedOwner() {
        LiveHands state = parse("{\"hands\":[{\"id\":\"left\",\"occupant\":{\"token\":\"hand-token\",\"stack\":{\"id\":\"minecraft:diamond\",\"count\":2}}},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":7,\"equipment\":[{\"id\":\"gloves\",\"occupant\":{\"token\":\"glove-token\",\"stack\":{\"id\":\"minecraft:leather_chestplate\",\"count\":1}}}]}");
        ItemStack glove = state.equipmentStack("gloves").orElseThrow();
        ItemStack changed = glove.copy();
        changed.set(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get(), new com.juicyslew.moonstation14.ms14.combat.MeleeCooldownData(42));
        LiveHands updated = state.replaceEquipmentOwnedComponents(7, "gloves", new ItemToken("glove-token"), changed).orElseThrow();
        assertEquals(8, updated.revision());
        assertEquals("minecraft:leather_chestplate", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(updated.equipmentStack("gloves").orElseThrow().getItem()).toString());
        assertEquals(42, updated.equipmentStack("gloves").orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get()).nextAttackTick());
        assertTrue(net.minecraft.world.item.ItemStack.matches(state.stackCopy("left").orElseThrow(), updated.stackCopy("left").orElseThrow()));
        assertEquals(state.equipmentStack("gloves").orElseThrow().getCount(), updated.equipmentStack("gloves").orElseThrow().getCount());
        assertTrue(state.replaceEquipmentOwnedComponents(6, "gloves", new ItemToken("glove-token"), changed).isEmpty());
        assertTrue(state.replaceEquipmentOwnedComponents(7, "gloves", new ItemToken("wrong-token"), changed).isEmpty());
        assertTrue(state.replaceEquipmentOwnedComponents(7, "gloves", new ItemToken("glove-token"), new ItemStack(Items.DIAMOND, 1)).isEmpty());
        assertTrue(state.replaceEquipmentOwnedComponents(7, "gloves", new ItemToken("glove-token"), new ItemStack(Items.LEATHER_CHESTPLATE, 2)).isEmpty());
        assertEquals(7, state.revision());
    }

    @Test
    void consumeOneConsumesOnlyExactActiveHandItemAndKeepsRemainingOwnershipToken() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack one = new ItemStack(Items.DIAMOND);
        LiveHands single = empty.putWhole(0, "left", TOKEN, one).state();
        LiveHands removed = single.consumeOne(1, "left", TOKEN.value(), one).orElseThrow();
        assertTrue(removed.stackCopy("left").isEmpty());
        assertEquals(2, removed.revision());
        assertEquals(1, single.stackCopy("left").orElseThrow().getCount());

        ItemStack three = new ItemStack(Items.DIAMOND, 3);
        LiveHands stacked = empty.putWhole(0, "left", TOKEN, three).state();
        LiveHands remainder = stacked.consumeOne(1, "left", TOKEN.value(), three).orElseThrow();
        assertEquals(2, remainder.stackCopy("left").orElseThrow().getCount());
        assertEquals(Optional.of(TOKEN.value()), remainder.token("left"));
        assertEquals(3, stacked.stackCopy("left").orElseThrow().getCount());
        assertEquals("left", remainder.activeHand());
    }

    @Test
    void consumeOneRejectsMismatchWrongHandOverflowAndOversizedExpectedStackWithoutMutation() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        LiveHands occupied = empty.putWhole(0, "left", TOKEN, stack).state();
        assertTrue(occupied.consumeOne(1, "right", TOKEN.value(), stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", "wrong-token", stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.EMERALD, 3)).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.DIAMOND, 2)).isEmpty());
        assertTrue(occupied.consumeOne(Long.MAX_VALUE, "left", TOKEN.value(), stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.DIAMOND, 65)).isEmpty());
        assertEquals(3, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, occupied.revision());

        LiveHands overflow = parse("{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":9223372036854775807}");
        assertTrue(overflow.consumeOne(Long.MAX_VALUE, "left", TOKEN.value(), stack).isEmpty());
    }

    @Test
    void activeUseMutationPreservesOwnerAndOtherHandsAndBoundsQuantityAndType() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        LiveHands original = empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state()
                .putWhole(1, "right", new ItemToken("other"), new ItemStack(Items.EMERALD)).state();
        ItemStack before = original.stackCopy("left").orElseThrow();
        ItemStack after = before.copy();
        after.set(DataComponents.CUSTOM_NAME, Component.literal("used"));
        LiveHands updated = original.replaceActiveUseStack(2, "left", TOKEN.value(), before, after).orElseThrow();
        assertEquals(3, updated.stackCopy("left").orElseThrow().getCount());
        assertEquals("used", updated.stackCopy("left").orElseThrow().getHoverName().getString());
        assertEquals(Optional.of(TOKEN.value()), updated.token("left"));
        assertTrue(ItemStack.isSameItemSameComponents(original.stackCopy("right").orElseThrow(),
                updated.stackCopy("right").orElseThrow()));
        assertEquals(original.token("right"), updated.token("right"));
        assertEquals("left", updated.activeHand());
        assertEquals(3, updated.revision());
        ItemStack smaller = before.copy(); smaller.setCount(2);
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before, smaller).isPresent());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before, ItemStack.EMPTY).isPresent());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before,
                new ItemStack(Items.DIAMOND, 4)).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before,
                new ItemStack(Items.EMERALD)).isEmpty());
        assertTrue(empty.replaceActiveUseStack(0, "left", "new-token", ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(original.replaceActiveUseStack(1, "left", TOKEN.value(), before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "right", TOKEN.value(), before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", "wrong", before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), new ItemStack(Items.EMERALD, 3), after).isEmpty());
    }

    @Test
    void passiveOwnedStackProgressPreservesTokenAndActionRevision() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":4}");
        LiveHands before = empty.putWhole(4, "left", TOKEN, new ItemStack(Items.DIAMOND)).state();
        LiveHands after = before.tickWelderStacks(stack -> {
            ItemStack progress = stack.copy();
            progress.set(DataComponents.CUSTOM_NAME, Component.literal("passive progress"));
            return progress;
        });
        assertEquals(before.revision(), after.revision());
        assertEquals(before.token("left"), after.token("left"));
        assertEquals("passive progress", after.stackCopy("left").orElseThrow().getHoverName().getString());
        assertFalse(before.stackCopy("left").orElseThrow().has(DataComponents.CUSTOM_NAME));
    }

    @Test void switchingAllowsIndependentPickupSlotsWithoutMovingStacks() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        assertTrue(empty.stackCopy(empty.activeHand()).isEmpty());
        LiveHands first = empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state();
        assertEquals("left", first.activeHand());
        LiveHands switched = first.selectActive(1, "right").orElseThrow();
        assertEquals(2, switched.revision());
        assertEquals("right", switched.activeHand());
        assertTrue(switched.stackCopy(switched.activeHand()).isEmpty());
        assertEquals(Optional.of(TOKEN.value()), switched.token("left"));
        LiveHands both = switched.putWhole(2, "right", new ItemToken("second"),
                new ItemStack(Items.EMERALD, 2)).state();
        LiveHands back = both.selectActive(3, "left").orElseThrow();
        assertEquals(4, back.revision());
        assertEquals(Optional.of(TOKEN.value()), back.token("left"));
        assertEquals(Optional.of("second"), back.token("right"));
        assertEquals(3, back.stackCopy("left").orElseThrow().getCount());
        assertEquals(2, back.stackCopy("right").orElseThrow().getCount());
        assertTrue(first.selectActive(0, "right").isEmpty());
        assertTrue(first.selectActive(1, "left").isEmpty());
        assertTrue(first.selectActive(1, "missing").isEmpty());
        assertTrue(parse("{\"hands\":[{\"id\":\"only\"}],\"active\":\"only\",\"revision\":0}")
                .selectActive(0, "other").isEmpty());
        assertTrue(parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":9223372036854775807}")
                .selectActive(Long.MAX_VALUE, "right").isEmpty());
    }

    @Test
    void reservationClaimsOnlyItsEmptyHandAndIsTransientAcrossCodec() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        UUID target = UUID.randomUUID();
        LiveHands reserved = empty.reserveHand(0, "left", target).orElseThrow();
        assertEquals(1, reserved.revision());
        assertEquals(Optional.of(target), reserved.reservation("left"));
        assertTrue(reserved.putWhole(1, "left", TOKEN, new ItemStack(Items.DIAMOND)).state() == reserved);
        assertTrue(reserved.takeWhole(1, "left", TOKEN).state() == reserved);
        assertTrue(reserved.transferWhole(1, "left", "right", TOKEN).state() == reserved);
        assertTrue(reserved.putWhole(1, "right", TOKEN, new ItemStack(Items.DIAMOND)).succeeded());
        LiveHands unrelated = reserved.selectActive(1, "right").orElseThrow();
        assertEquals(Optional.of(target), unrelated.reservation("left"));
        assertEquals(2, unrelated.revision());
        assertTrue(unrelated.releaseHand("left", UUID.randomUUID()).isEmpty());
        LiveHands released = unrelated.releaseHand("left", target).orElseThrow();
        assertTrue(released.reservation("left").isEmpty());
        assertEquals(3, released.revision());

        var encoded = LiveHands.CODEC.encodeStart(JsonOps.INSTANCE, reserved).getOrThrow();
        LiveHands restored = LiveHands.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.reservation("left").isEmpty());
        assertEquals(reserved.revision(), restored.revision());
    }
}
