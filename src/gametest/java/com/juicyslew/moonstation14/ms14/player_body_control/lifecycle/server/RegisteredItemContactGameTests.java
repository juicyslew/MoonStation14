package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.loose_objects.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"item-contact-damage", "registered-player"})
@PrefixGameTestTemplate(false)
public final class RegisteredItemContactGameTests {
    private RegisteredItemContactGameTests() { }

    @GameTest(template="atmos_large_empty",templateNamespace="moonstation14",batch="registered_item_contact_damage",timeoutTicks=140)
    public static void ordinaryPickupDamagesAndKeepsWorldSourceDuringTimer(GameTestHelper h) { run(h, false, false); }

    @GameTest(template="atmos_large_empty",templateNamespace="moonstation14",batch="registered_item_contact_protected",timeoutTicks=100)
    public static void actorProtectionAllowsPickupWithoutDamage(GameTestHelper h) { run(h, true, false); }

    @GameTest(template="atmos_large_empty",templateNamespace="moonstation14",batch="registered_item_contact_trusted_created",timeoutTicks=100)
    public static void trustedCreatedPickupSkipsSyntheticContact(GameTestHelper h) { run(h, false, true); }

    private static void run(GameTestHelper h, boolean protect, boolean trustedCreated) {
        var tomatoId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        ScopedPrototypeCatalogFixture.replace(h, ModCharacters.CHARACTER_TYPE, c -> {
            var id = ModCharacters.HUMAN_ID;
            var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, c.get(id)).getOrThrow().getAsJsonObject();
            var components = json.has("components") ? json.getAsJsonArray("components") : new com.google.gson.JsonArray();
            json.add("components", components);
            if (protect) com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,
                    com.google.gson.JsonParser.parseString("{\"type\":\"DamageOnInteractProtection\",\"profile\":{\"protection\":{\"coefficients\":{\"heat\":0,\"caustic\":0}},\"slots\":[\"GLOVES\"]}}").getAsJsonObject());
            c.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
            return c;
        });
        ScopedPrototypeCatalogFixture.replace(h, ModLooseObjectProfiles.PROFILE_TYPE, c -> {
            var profile = c.get(tomatoId);
            var encoded = LooseObjectProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, profile).getOrThrow();
            var json = encoded.getAsJsonObject();
            json.add("damage_on_interact", com.google.gson.JsonParser.parseString(
                    "{\"damage\":{\"heat\":4,\"caustic\":4},\"damage_active\":true,\"interact_timer_seconds\":2,\"throw_source\":false,\"stun_chance\":0}"));
            c.put(tomatoId, LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
            return c;
        });

        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), trustedCreated ? "contact-created" : protect ? "contact-guard" : "contact-source");
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        var sourceRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (sourceRef[0] != null && !sourceRef[0].isRemoved()) sourceRef[0].discard();
            if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard();
            fixture.close();
        };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void done(net.minecraft.gametest.framework.GameTestInfo info) {
                try { cleanup.run(); }
                catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { done(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { done(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo, net.minecraft.gametest.framework.GameTestInfo newInfo, net.minecraft.gametest.framework.GameTestRunner runner) { done(oldInfo); }
        });

        try {
            h.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    var body = active.body();
                    bodyRef[0] = body;
                    if (Math.abs(body.getHealth() - body.getMaxHealth()) > .011f)
                        throw new GameTestAssertException("Ready did not project the authoritative known-zero ledger before first damage: health="
                                + body.getHealth() + "/" + body.getMaxHealth());
                    BlockPos position = h.absolutePos(new BlockPos(3, 2, 3));
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        h.getLevel().setBlock(position.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                    body.teleportTo(position.getX() + .5, position.getY(), position.getZ() + .5);
                    body.setDeltaMovement(Vec3.ZERO);
                    body.tick();
                    var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var hand = hands.activeHand();
                    var source = com.juicyslew.moonstation14.entities.LooseItemEntity.create(h.getLevel(),
                            body.getEyePosition().add(body.getLookAngle().scale(.7)),
                            new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get()));
                    source.setNoGravity(true);
                    sourceRef[0] = source;
                    if (!h.getLevel().addFreshEntity(source)) throw new GameTestAssertException("contact tomato spawn failed");

                    float initialHealth = body.getHealth();
                    var initialDamage = damage(body);
                    var timerBefore = source.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ITEM_CONTACT_TIMER.get());
                    var stack = source.getItem().copy();
                    var result = trustedCreated
                            ? com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupTrustedCreated(
                                    com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow(), source, stack, () -> true)
                            : com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                    fixture.player(), source.getUUID(), hand, hands.revision(), ignored -> { });
                    var owned = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());

                    if (trustedCreated) {
                        if (result != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                            throw new GameTestAssertException("trusted-created pickup denied: " + result);
                        var timerAfter = source.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ITEM_CONTACT_TIMER.get());
                        if (Math.abs(body.getHealth() - initialHealth) > .011f || !damage(body).equals(initialDamage)
                                || timerAfter != timerBefore || source.isAlive()
                                || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(owned.stackCopy(hand).orElseThrow(), stack))
                            throw new GameTestAssertException("trusted output pickup synthesized contact damage/timer or lost exact stack");
                        h.succeed();
                        return;
                    }
                    if (protect) {
                        if (result != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS
                                || Math.abs(body.getHealth() - initialHealth) > .011f || !damage(body).equals(initialDamage)
                                || source.isAlive() || owned.stackCopy(hand).filter(s -> s.is(com.juicyslew.moonstation14.item.ModItems.TOMATO.get())).isEmpty())
                            throw new GameTestAssertException("fully protected contact should allow untouched pickup and transfer ownership");
                        h.succeed();
                        return;
                    }

                    if (result != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.DENIED)
                        throw new GameTestAssertException("positive contact damage should block ordinary pickup: " + result);
                    var afterFirst = damage(body);
                    long next = source.getData(com.juicyslew.moonstation14.component.ModDataAttachments.ITEM_CONTACT_TIMER).nextInteractionTick();
                    if (Math.abs((afterFirst.getOrDefault("heat", 0f) - initialDamage.getOrDefault("heat", 0f)) - 4f) > .011f
                            || Math.abs((afterFirst.getOrDefault("caustic", 0f) - initialDamage.getOrDefault("caustic", 0f)) - 4f) > .011f
                            || Math.abs((initialHealth - body.getHealth()) - 8f / com.juicyslew.moonstation14.ms14.damage.DamageSystem.TYPED_PER_HEALTH) > .011f
                            || !source.isAlive() || owned.stackCopy(hand).isPresent() || next <= h.getLevel().getGameTime())
                        throw new GameTestAssertException("ordinary contact damage/ownership/timer incorrect: heatDelta="
                                + (afterFirst.getOrDefault("heat", 0f) - initialDamage.getOrDefault("heat", 0f))
                                + ", causticDelta=" + (afterFirst.getOrDefault("caustic", 0f) - initialDamage.getOrDefault("caustic", 0f))
                                + ", healthDelta=" + (initialHealth - body.getHealth()) + ", expectedHealthDelta="
                                + (8f / com.juicyslew.moonstation14.ms14.damage.DamageSystem.TYPED_PER_HEALTH)
                                + ", alive=" + source.isAlive() + ", removed=" + source.isRemoved()
                                + ", held=" + owned.stackCopy(hand).map(s -> s.getItem().toString() + "x" + s.getCount()).orElse("<empty>")
                                + ", nextTick=" + next + ", now=" + h.getLevel().getGameTime()
                                + ", initialDamage=" + initialDamage + ", observedDamage=" + afterFirst);

                    h.runAfterDelay(1, () -> {
                        try {
                            float heldHealth = body.getHealth();
                            var heldDamage = damage(body);
                            var revision = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision();
                            var retry = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                    fixture.player(), source.getUUID(), hand, revision, ignored -> { });
                            if (retry != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.DENIED
                                    || Math.abs(body.getHealth() - heldHealth) > .011f || !damage(body).equals(heldDamage) || !source.isAlive())
                                throw new GameTestAssertException("contact cooldown retry changed outcome or applied extra damage");
                            h.runAfterDelay(40, () -> {
                                try {
                                    var retryHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                    float beforeRetryHealth = body.getHealth();
                                    var beforeRetryDamage = damage(body);
                                    var expiredRetry = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                            fixture.player(), source.getUUID(), hand, retryHands.revision(), ignored -> { });
                                    var renewed = source.getData(com.juicyslew.moonstation14.component.ModDataAttachments.ITEM_CONTACT_TIMER).nextInteractionTick();
                                    if (expiredRetry != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.DENIED
                                            || !source.isAlive() || retryHands.stackCopy(hand).isPresent()
                                            || damage(body).getOrDefault("heat", 0f) - beforeRetryDamage.getOrDefault("heat", 0f) < 3.99f
                                            || damage(body).getOrDefault("caustic", 0f) - beforeRetryDamage.getOrDefault("caustic", 0f) < 3.99f
                                            || Math.abs((beforeRetryHealth - body.getHealth()) - 8f / com.juicyslew.moonstation14.ms14.damage.DamageSystem.TYPED_PER_HEALTH) > .011f
                                            || renewed <= h.getLevel().getGameTime())
                                        throw new GameTestAssertException("expired contact retry failed to damage and renew cooldown while preserving world ownership");
                                    h.succeed();
                                } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
                            });
                        } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
                    });
                } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
            });
        } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
    }

    private static java.util.Map<String, Float> damage(net.minecraft.world.entity.LivingEntity entity) {
        var data = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return data == null ? java.util.Map.of() : data.getMap();
    }
}
