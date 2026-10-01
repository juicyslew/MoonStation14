package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyGrenadePrimeTest {
    private static final ItemToken TOKEN = new ItemToken("grenade-owner");

    private static LiveHands occupied(String item, long revision) {
        String json = "{\"hands\":[{\"id\":\"left\",\"occupant\":{\"token\":\"grenade-owner\",\"stack\":"
                + item + "}},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":" + revision + "}";
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static LiveHands occupiedInactive(String item, long revision) {
        String json = "{\"hands\":[{\"id\":\"left\",\"occupant\":{\"token\":\"grenade-owner\",\"stack\":"
                + item + "}},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":" + revision + "}";
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test void primedComponentRecordsPrimeBeepAndSchedulesOnlySecondBeepAtForty() {
        long now = 100;
        GrenadeFuse fuse = GrenadeFuse.safe().prime(now).evaluate(now).state();
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get());
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(fuse));

        GrenadeFuse stored = grenade.get(ModDataComponents.GRENADE_FUSE.get()).toFuse();
        assertTrue(stored.beepedAtPrime());
        assertFalse(stored.beepedAtForty());
        assertFalse(stored.evaluate(now + 1).beepDue());
        assertFalse(stored.evaluate(now + 39).beepDue());
        assertTrue(stored.evaluate(now + 40).beepDue());
    }

    @Test void replaceOwnedGrenadeRetainsTokenAndAdvancesOneRevision() {
        LiveHands original = occupied("{\"id\":\"moonstation14:grenade\",\"count\":1}", 4);
        ItemStack replacement = new ItemStack(ModItems.GRENADE.get());
        LiveHands replaced = original.replaceOwnedStack(4, "left", TOKEN, replacement).orElseThrow();
        assertEquals(5, replaced.revision());
        assertEquals(original.token("left"), replaced.token("left"));
        assertSame(ModItems.GRENADE.get(), replaced.stackCopy("left").orElseThrow().getItem());
        assertSame(ModItems.GRENADE.get(), original.stackCopy("left").orElseThrow().getItem());
    }

    @Test void replacementRejectsStaleTokenHandItemAndOverflow() {
        LiveHands grenade = occupied("{\"id\":\"moonstation14:grenade\",\"count\":1}", 4);
        ItemStack replacement = new ItemStack(ModItems.GRENADE.get());
        assertTrue(grenade.replaceOwnedStack(3, "left", TOKEN, replacement).isEmpty());
        assertTrue(grenade.replaceOwnedStack(4, "left", new ItemToken("wrong"), replacement).isEmpty());
        assertTrue(grenade.replaceOwnedStack(4, "right", TOKEN, replacement).isEmpty());
        assertTrue(grenade.replaceOwnedStack(4, "left", TOKEN, new ItemStack(Items.STICK)).isEmpty());
        assertTrue(occupied("{\"id\":\"moonstation14:grenade\",\"count\":1}", Long.MAX_VALUE)
                .replaceOwnedStack(Long.MAX_VALUE, "left", TOKEN, replacement).isEmpty());
        LiveHands other = occupied("{\"id\":\"minecraft:stick\",\"count\":1}", 4);
        assertTrue(other.replaceOwnedStack(4, "left", TOKEN, replacement).isEmpty());
        assertEquals(4, grenade.revision());
    }

    @Test void replaceOwnedGrenadeAllowsInactiveHandForFuseTransition() {
        LiveHands original = occupiedInactive("{\"id\":\"moonstation14:grenade\",\"count\":1}", 4);
        ItemStack replacement = new ItemStack(ModItems.GRENADE.get());
        LiveHands replaced = original.replaceOwnedStack(4, "left", TOKEN, replacement).orElseThrow();
        assertEquals(5, replaced.revision());
        assertEquals("right", replaced.activeHand());
        assertEquals(original.token("left"), replaced.token("left"));
    }

    @Test void secondBeepKeepsRevisionAndAcceptsPriorRevisionButRejectsInvalidProgress() {
        ItemStack armed = new ItemStack(ModItems.GRENADE.get());
        armed.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED, 100, true, false));
        LiveHands before = occupied("{\"id\":\"moonstation14:grenade\",\"count\":1,\"components\":{\"moonstation14:grenade_fuse\":{\"phase\":\"ARMED\",\"deadline\":100,\"beeped_at_prime\":true,\"beeped_at_forty\":false}}}", 4);
        ItemStack beeped = armed.copy();
        beeped.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED, 100, true, true));
        LiveHands next = before.advanceOwnedGrenadeBeep(4, "left", TOKEN, beeped).orElseThrow();
        assertEquals(4, next.revision());
        assertEquals(before.token("left"), next.token("left"));
        assertTrue(next.stackCopy("left").orElseThrow().get(ModDataComponents.GRENADE_FUSE.get()).beepedAtForty());
        assertTrue(next.advanceOwnedGrenadeBeep(4, "left", TOKEN, beeped).isEmpty());
        assertTrue(next.selectActive(4, "right").isPresent());
        assertTrue(next.replaceOwnedStack(4, "left", TOKEN, beeped).isPresent());
        assertTrue(next.advanceOwnedGrenadeBeep(3, "left", TOKEN, beeped).isEmpty());
    }
}
