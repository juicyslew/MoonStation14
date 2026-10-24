package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"movement", "character", "registered-intent"})
@PrefixGameTestTemplate(false)
public final class RegisteredIntentMovementBaselineGameTests {
    private RegisteredIntentMovementBaselineGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void admittedIntentMovesRegisteredBody(GameTestHelper helper) {
        var level = helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "intent-move");
        net.minecraft.world.entity.LivingEntity[] ownedBody = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                try {
                    if (ownedBody[0] != null && ownedBody[0].isAlive()) ownedBody[0].discard();
                    fixture.close();
                } catch (Throwable cleanup) {
                    Throwable original = info.getError();
                    if (original != null) original.addSuppressed(cleanup); else info.fail(cleanup);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(oldTest); }
        });
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    ownedBody[0] = active.body();
                    BlockPos bodyPos = helper.absolutePos(new BlockPos(3, 2, 3));
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlock(bodyPos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                    active.body().teleportTo(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
                    active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    var start = active.body().position();
                    active.body().tick();
                    LifecycleCharacterSessionControl.onPayload(new GhostControlPayloads.Intent(
                            active.epoch(), 1, (short) 0, (short) 1000, (byte) 0, 0, 0f, 0f), fixture.player());
                    helper.runAfterDelay(8, () -> {
                        try {
                            if (active.body().position().distanceToSqr(start) < .01)
                                throw new GameTestAssertException("admitted intent did not move registered body: " + start + " -> " + active.body().position());
                            helper.succeed();
                        } catch (RuntimeException | Error failure) { throw failure; }
                    });
                } catch (RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", batch = "intent_step_bare", timeoutTicks = 100)
    public static void admittedIntentTriggersProfileStepDamage(GameTestHelper helper) {
        runProfileStepDamage(helper, false);
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", batch = "intent_step_noslip", timeoutTicks = 100)
    public static void admittedIntentTriggersProfileStepDamageWithNoSlip(GameTestHelper helper) {
        runProfileStepDamage(helper, true);
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", batch = "intent_step_protected", timeoutTicks = 100)
    public static void admittedIntentProtectedFromStepTriggersBlocksSlipAndDamage(GameTestHelper helper) {
        runProfileStepDamage(helper, false, true);
    }

    private static void runProfileStepDamage(GameTestHelper helper, boolean noSlip) {
        runProfileStepDamage(helper, noSlip, false);
    }

    private static void runProfileStepDamage(GameTestHelper helper, boolean noSlip, boolean protectedFromStepTriggers) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var characterType = com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE;
        var oldChars = new java.util.HashMap<>(manager.snapshot(characterType).asMap());
        var type = com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.PROFILE_TYPE;
        var old = new java.util.HashMap<>(manager.snapshot(type).asMap());
        var fixtureRef = new RegisteredServerPlayerFixture[1];
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        var sourceRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var restored = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (restored.compareAndSet(false, true)) {
                manager.publishDecoded(type, old);
                manager.publishDecoded(characterType, oldChars);
            }
            if (sourceRef[0] != null && sourceRef[0].isAlive()) sourceRef[0].discard();
            if (bodyRef[0] != null && bodyRef[0].isAlive()) bodyRef[0].discard();
            if (fixtureRef[0] != null) fixtureRef[0].close();
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void done(net.minecraft.gametest.framework.GameTestInfo info) {
                try { cleanup.run(); } catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { done(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { done(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { done(oldTest); }
        });
        try {
            if (noSlip || protectedFromStepTriggers) {
                var candidate = new java.util.HashMap<>(oldChars);
                var humanId = com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID;
                var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(
                        com.mojang.serialization.JsonOps.INSTANCE, oldChars.get(humanId)).getOrThrow().getAsJsonObject();
                var components = json.has("components") && json.get("components").isJsonArray()
                        ? json.getAsJsonArray("components") : new com.google.gson.JsonArray();
                json.add("components", components);
                boolean hasNoSlip = false;
                for (var component : components) if (component.isJsonObject() && component.getAsJsonObject().has("type")
                        && component.getAsJsonObject().get("type").getAsString().equals("NoSlip")) hasNoSlip = true;
                if (!hasNoSlip) components.add(com.google.gson.JsonParser.parseString("{\"type\":\"NoSlip\"}"));
                if (protectedFromStepTriggers) components.add(com.google.gson.JsonParser.parseString(
                        "{\"type\":\"ProtectedFromStepTriggers\",\"profile\":{\"slot_flags\":[]}}"));
                candidate.put(humanId, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
                manager.publishDecoded(characterType, candidate);
            }
            fixtureRef[0] = RegisteredServerPlayerFixture.register(level, protectedFromStepTriggers ? "intent-step-protected" : noSlip ? "intent-step-noslip" : "intent-step");
            var fixture = fixtureRef[0];
            var profiles = new java.util.HashMap<>(old);
            var genericId = com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.GENERIC_ID;
            var generic = profiles.get(genericId);
            var encoded = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.CODEC.encodeStart(
                    net.minecraft.nbt.NbtOps.INSTANCE, generic).getOrThrow();
            var json = net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, encoded).getAsJsonObject();
            json.add("step_trigger", com.google.gson.JsonParser.parseString("{\"active\":true,\"intersection_ratio\":0.2,\"required_speed\":0.1,\"ignore_weightless\":false,\"preventable_step_trigger\":true}"));
            json.add("step_damage", com.google.gson.JsonParser.parseString("{\"damage\":{\"piercing\":4.5},\"delete_source\":true}"));
            json.add("slippery", com.google.gson.JsonParser.parseString("{\"stun_seconds\":2.0,\"knockdown_seconds\":2.0,\"launch_velocity_multiplier\":1.0,\"friction_factor\":0.5,\"friction_duration_seconds\":1.0,\"stamina_damage\":10.0}"));
            json.addProperty("physical_width", .8f); json.addProperty("physical_height", .1f);
            var customId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_step_damage");
            var custom = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            profiles.put(customId, custom);
            manager.publishDecoded(type, profiles);
            helper.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture); var body = active.body(); bodyRef[0] = body;
                    var resolvedActor = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body);
                    if ((noSlip || protectedFromStepTriggers) && resolvedActor.map(data -> data.components().stream().noneMatch(component -> component.type().equals("NoSlip"))).orElse(true))
                        throw new GameTestAssertException("active character policy did not resolve NoSlip");
                    if (protectedFromStepTriggers && resolvedActor.map(data -> data.components().stream().noneMatch(component -> component.type().equals("ProtectedFromStepTriggers"))).orElse(true))
                        throw new GameTestAssertException("active character policy did not resolve ProtectedFromStepTriggers");
                    BlockPos floor = helper.absolutePos(new BlockPos(3, 2, 3));
                    for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.setBlock(floor.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
                    body.teleportTo(floor.getX()+.5, floor.getY(), floor.getZ()+.5); body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); body.tick();
                    var damage = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                    float before = damage == null ? 0f : damage.getMap().getOrDefault("piercing", 0f);
                    var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.FOOD_COCOA_BEANS.get());
                    stack.set(com.juicyslew.moonstation14.component.ModDataComponents.LOOSE_OBJECT_PROFILE.get(), customId);
                    var source = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, 0, .35), stack); sourceRef[0] = source;
                    if (!level.addFreshEntity(source)) throw new GameTestAssertException("step source spawn failed");
                    var start = body.position();
                    var observedContact = new java.util.concurrent.atomic.AtomicBoolean();
                    Runnable[] observe = new Runnable[1];
                    observe[0] = () -> {
                        if (!body.isAlive() || !source.isAlive()) return;
                        var feet = com.juicyslew.moonstation14.ms14.slip.ItemStepContactGeometry.feetBand(body.getBoundingBox());
                        if (com.juicyslew.moonstation14.ms14.slip.ItemStepContactGeometry.intersectionRatio(feet, source.getBoundingBox()) >= .2)
                            observedContact.set(true);
                        helper.runAfterDelay(1, observe[0]);
                    };
                    observe[0].run();
                    LifecycleCharacterSessionControl.onPayload(new GhostControlPayloads.Intent(active.epoch(), 1, (short)0, (short)1000, (byte)0, 0, 0f, 0f), fixture.player());
                    helper.runAfterDelay(8, () -> {
                        var current = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                        float after = current == null ? 0f : current.getMap().getOrDefault("piercing", 0f);
                        var stamina = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.STAMINA.get());
                        boolean protectedCase = protectedFromStepTriggers;
                        boolean protectionPolicy = !protectedCase || com.juicyslew.moonstation14.ms14.equipment.EquipmentStepTriggerProtectionPolicy.protects(body);
                        if (body.position().distanceToSqr(start) < .01 || !observedContact.get() || !protectionPolicy
                                || (protectedCase ? source.isAlive() || Math.abs(after-before) > .011f : !source.isAlive() || Math.abs((after-before)-4.5f) > .011f)
                                || ((noSlip || protectedCase) && ((stamina != null && stamina.state().damage() != 0f)
                                || com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(body))))
                            throw new GameTestAssertException("intent step damage failed: moved="+body.position().distanceToSqr(start)+" sourceAlive="+source.isAlive()+" delta="+(after-before));
                        helper.succeed();
                    });
                } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
            });
        } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
    }
}
