package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LiveEquipmentStateTest {
    private static final List<String> HUMAN = List.of("belt", "back");

    private static LiveHands decode(String json) {
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static JsonElement encode(LiveHands state) {
        return LiveHands.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
    }

    @Test void oldPopulatedHandsMigrateOnlyOnFirstEquippedTransfer() {
        LiveHands old = decode("{\"hands\":[{\"id\":\"left\",\"occupant\":{\"token\":\"gem\",\"stack\":{\"id\":\"minecraft:diamond\",\"count\":3}}},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":9}");
        assertFalse(encode(old).getAsJsonObject().has("equipment"));
        assertTrue(old.equip(9, List.of(), "left", "belt", "gem").isEmpty());
        LiveHands equipped = old.equip(9, HUMAN, "left", "belt", "gem").orElseThrow();
        assertEquals(10, equipped.revision());
        assertTrue(equipped.stackCopy("left").isEmpty());
        assertEquals("right", equipped.activeHand());
        assertEquals(3, old.stackCopy("left").orElseThrow().getCount());
        assertEquals("gem", encode(equipped).getAsJsonObject().getAsJsonArray("equipment")
                .get(0).getAsJsonObject().getAsJsonObject("occupant").get("token").getAsString());
        assertEquals(encode(equipped), encode(decode(encode(equipped).toString())));
        LiveHands back = equipped.unequip(10, HUMAN, "belt", "right", "gem").orElseThrow();
        assertEquals(11, back.revision());
        assertEquals(Optional.of("gem"), back.token("right"));
        assertEquals(3, back.stackCopy("right").orElseThrow().getCount());
        assertTrue(encode(back).getAsJsonObject().has("equipment"));
    }

    @Test void handTransitionsKeepEquipmentAndRejectDuplicateToken() {
        LiveHands old = decode("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0,\"equipment\":[{\"id\":\"belt\",\"occupant\":{\"token\":\"worn\",\"stack\":{\"id\":\"minecraft:diamond\",\"count\":2}}},{\"id\":\"back\"}]}");
        assertEquals(LiveHands.Rejection.DUPLICATE_TOKEN, old.putWhole(0, "left", new ItemToken("worn"),
                new ItemStack(Items.DIAMOND)).rejection().orElseThrow());
        LiveHands placed = old.putWhole(0, "left", new ItemToken("other"), new ItemStack(Items.EMERALD)).state();
        assertTrue(placed.equip(1, HUMAN, "left", "belt", "other").isEmpty());
        assertTrue(placed.unequip(1, HUMAN, "belt", "left", "worn").isEmpty());
        assertEquals(encode(old).getAsJsonObject().get("equipment"), encode(placed).getAsJsonObject().get("equipment"));
        assertTrue(placed.selectActive(1, "right").isPresent());
        assertEquals(encode(old).getAsJsonObject().get("equipment"),
                encode(placed.move(1, "left", "right").orElseThrow()).getAsJsonObject().get("equipment"));
        assertTrue(placed.equip(1, List.of("back", "belt"), "left", "back", "other").isEmpty());
        assertTrue(placed.equip(0, HUMAN, "left", "back", "other").isEmpty());
        assertTrue(placed.equip(1, HUMAN, "left", "back", "bad").isEmpty());
        assertTrue(placed.equip(1, HUMAN, "missing", "back", "other").isEmpty());
        LiveHands moved = placed.equip(1, HUMAN, "left", "back", "other").orElseThrow();
        assertTrue(moved.stackCopy("left").isEmpty());
        assertEquals(2, moved.revision());
        assertEquals(1, placed.revision());
        assertEquals(LiveHands.Rejection.DUPLICATE_TOKEN, moved.putWhole(2, "left", new ItemToken("other"),
                new ItemStack(Items.EMERALD)).rejection().orElseThrow());
    }

    @Test void corruptEquipmentFailsAtCodecBoundaryAndPigHasNoFabricatedEquipment() {
        String base = "\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":0";
        String occupant = ",\"occupant\":{\"token\":\"dup\",\"stack\":{\"id\":\"minecraft:diamond\",\"count\":1}}";
        for (String equipment : new String[]{
                "[{\"id\":\"belt\"},{\"id\":\"belt\"}]",
                "[{\"id\":\"head\"}]",
                "[{\"id\":\"belt\"},{\"id\":\"back\"},{\"id\":\"belt\"}]",
                "[{\"id\":\"belt\"" + occupant + "},{\"id\":\"back\"" + occupant + "}]"})
            assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{" + base + ",\"equipment\":" + equipment + "}")).error().isPresent());
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"" + occupant + "}],\"active\":\"left\",\"revision\":0,"
                        + "\"equipment\":[{\"id\":\"belt\"" + occupant + "}]}")).error().isPresent());
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{" + base + ",\"equipment\":[{\"id\":\"belt\",\"occupant\":{\"token\":\"x\","
                        + "\"stack\":{\"id\":\"minecraft:diamond\",\"count\":65}}}]}"))
                .error().isPresent());
        LiveHands pig = decode("{" + base + "}");
        assertFalse(encode(pig).getAsJsonObject().has("equipment"));
        assertTrue(pig.equip(0, List.of(), "left", "belt", "dup").isEmpty());
        assertTrue(decode("{" + base + ",\"equipment\":[]}").equip(0, HUMAN, "left", "belt", "dup").isEmpty());
    }
}
