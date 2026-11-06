package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BodyDropOwnershipOracleTest {
    @Test
    void fixtureOwnershipOracleChecksCanonicalTokenRevisionHandAndFullStack() {
        LiveHands empty = LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}"))
                .getOrThrow(IllegalStateException::new);
        ItemStack expected = new ItemStack(Items.STICK, 4);
        expected.set(DataComponents.CUSTOM_NAME, Component.literal("named fixture stick"));
        LiveHands occupied = empty.putWhole(0, "left", new ItemToken("carrier-drop"), expected).state();

        assertTrue(BodyDropPlacementGameTests.retainsDropOwner(occupied, occupied.revision(), "carrier-drop", expected));
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(occupied, occupied.revision(), "wrong-token", expected));
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(occupied, occupied.revision() + 1, "carrier-drop", expected));
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(empty, occupied.revision(), "carrier-drop", expected));

        ItemStack wrongCount = expected.copy();
        wrongCount.setCount(3);
        LiveHands countChanged = empty.putWhole(0, "left", new ItemToken("carrier-drop"), wrongCount).state();
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(countChanged, countChanged.revision(), "carrier-drop", expected));

        ItemStack wrongComponents = expected.copy();
        wrongComponents.set(DataComponents.CUSTOM_NAME, Component.literal("different name"));
        LiveHands componentsChanged = empty.putWhole(0, "left", new ItemToken("carrier-drop"), wrongComponents).state();
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(componentsChanged, componentsChanged.revision(), "carrier-drop", expected));

        LiveHands otherHand = empty.putWhole(0, "right", new ItemToken("carrier-drop"), expected).state();
        assertFalse(BodyDropPlacementGameTests.retainsDropOwner(otherHand, otherHand.revision(), "carrier-drop", expected));
    }
}
