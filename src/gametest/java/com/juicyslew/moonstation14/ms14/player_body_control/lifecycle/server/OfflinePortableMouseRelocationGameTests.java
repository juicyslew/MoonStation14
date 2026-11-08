package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying;
import com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual portable-carrier containment relocates a canonically OFFLINE player-owned mouse. */
@PrefixGameTestTemplate(false)
public final class OfflinePortableMouseRelocationGameTests {
    private OfflinePortableMouseRelocationGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 240)
    @GameTestTags({"living-carry", "persistence", "player-lifecycle", "portable-escape", "offline-relocation", "mouse-ai"})
    public static void realPortableCarrierContainmentUpdatesOfflineMouseLocator(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture carrierFixture = RegisteredServerPlayerFixture.register(level, "relocationcarrier");
        RegisteredServerPlayerFixture ownerFixture = RegisteredServerPlayerFixture.register(level, "relocationowner");
        Mob mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical mouse role entity creates");
        mouse.setPos(ownerFixture.player().position().add(2, 0, 0));
        require(level.addFreshEntity(mouse), "mouse enters loaded fixture level");
        try {
            CommittedMouseRoleSetup.start(helper, ownerFixture, (com.juicyslew.moonstation14.entities.MouseMob) mouse,
                    committed -> {
                        try {
                            var carrierReady = RegisteredPlayerReadyDriver.readyAndQuery(carrierFixture);
                            var binding = PlayerCharacterHarnessBinder.binding(committed);
                            require(binding != null, "canonical role setup persisted exact account/profile/Mind/body binding");
                            makeOffline(ownerFixture, level);
                            var oldContext = LifecycleStartupRuntime.contextFor(level.getServer()).orElseThrow();
                            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope persistedEnvelope;
                            try { persistedEnvelope = oldContext.primaryStore().read().orElseThrow(); }
                            catch (java.io.IOException failure) {
                                throw new GameTestAssertException("cold lifecycle envelope read failed: " + failure);
                            }
                            LifecycleStartupRuntime.discardContext(level.getServer());
                            var coldContext = new LifecycleServerContext(oldContext.primaryStore(), persistedEnvelope);
                            LifecycleStartupRuntime.rememberContext(level.getServer(), coldContext);
                            require(coldContext.lifecycle().profile(binding.accountId()).isEmpty()
                                            && coldContext.ownership().registeredHarness(
                                                    new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(
                                                            committed.getUUID())).isEmpty(),
                                    "fresh lifecycle context begins with durable OFFLINE row but empty registries");
                            SavedLifecycleProfile before = currentProfile(level, binding.accountId());
                            require(before.state() == SavedLifecycleProfile.State.OFFLINE
                                            && before.bodyId().equals(committed.getUUID())
                                            && before.mindId().equals(binding.mindId())
                                            && LifecycleStartupRuntime.isExactOfflineOwnerBody(committed, binding),
                                    "ordinary Creative park leaves canonical original mouse as exact OFFLINE owner");

                            var carrier = carrierReady.body();
                            String hand = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                                    .LIVE_HANDS.get()).activeHand();
                            String token = "offline-mouse-relocation-key";
                            var key = PortableBodyCarrying.createKey(carrier, committed, java.util.UUID.randomUUID());
                            var hands = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                                    .LIVE_HANDS.get());
                            PortableBodyGameTestAccess.holdPortableKey(carrier, hand, token, key);
                            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
                            var owner = containment.ownerOf(committed).orElseThrow();
                            require(owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carried
                                            && carried.carrier() == carrier && carried.isValid(),
                                    "same offline role mouse has actual held-key carrier custody");
                            var beforeAutonomyProbe = committed.position();
                            assertOfflineMouseRemainsAutonomousInert(helper, level, committed, binding, owner,
                                    containment, beforeAutonomyProbe, 0, 120, () -> {
                                        try {
                                            var beforePos = committed.position();
                                            var beforeChunk = committed.blockPosition();
                                            double destinationX = ((carrier.blockPosition().getX() >> 4) + 1) * 16 + 0.5;
                                            carrier.setPos(destinationX, carrier.getY(), carrier.getZ());
                                            require((carrier.blockPosition().getX() >> 4) != (beforeChunk.getX() >> 4),
                                                    "carrier crosses a real X chunk boundary");
                                            containment.tick();
                                            SavedLifecycleProfile after = currentProfile(level, binding.accountId());
                                            require(level.getEntity(committed.getUUID()) == committed
                                                            && containment.ownerOf(committed).orElse(null) == owner
                                                            && Math.abs(committed.getX() - carrier.getX()) < 1.0e-8
                                                            && Math.abs(committed.getZ() - carrier.getZ()) < 1.0e-8
                                                            && committed.getY() > carrier.getY()
                                                            && (committed.blockPosition().getX() >> 4) != (beforeChunk.getX() >> 4)
                                                            && !committed.position().equals(beforePos)
                                                            && after.location().x() == committed.getX()
                                                            && after.location().y() == committed.getY()
                                                            && after.location().z() == committed.getZ()
                                                            && after.state() == SavedLifecycleProfile.State.OFFLINE
                                                            && after.accountId().equals(before.accountId())
                                                            && after.profileKey().equals(before.profileKey())
                                                            && after.mindId().equals(before.mindId())
                                                            && after.bodyId().equals(before.bodyId())
                                                            && after.connectionEpoch().equals(before.connectionEpoch())
                                                            && after.connectionGeneration() == before.connectionGeneration()
                                                            && java.util.Objects.equals(after.offlineSinceMillis(), before.offlineSinceMillis())
                                                            && after.dimension().equals(level.dimension().location().toString())
                                                            && lifecycleFieldsUnchanged(before, after)
                                                            && LifecycleStartupRuntime.isExactOfflineOwnerBody(committed, binding),
                                                    "real containment pin persists moved OFFLINE mouse location without changing lifecycle identity: before="
                                                            + before + ", after=" + after + ", body=" + committed.position()
                                                            + ", carrier=" + carrier.position());
                                            helper.succeed();
                                        } finally {
                                            carrierFixture.close(); ownerFixture.close(); committed.discard();
                                        }
                                    }, () -> {
                                        carrierFixture.close(); ownerFixture.close(); committed.discard();
                                    });
                        } catch (RuntimeException | Error failure) {
                            carrierFixture.close(); ownerFixture.close(); committed.discard();
                            throw failure;
                        }
                    });
        } catch (RuntimeException | Error failure) {
            carrierFixture.close(); ownerFixture.close(); mouse.discard(); throw failure;
        }
    }

    private static void assertOfflineMouseRemainsAutonomousInert(GameTestHelper helper, ServerLevel level,
            Mob mouse, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding binding,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner,
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            Vec3 originalPosition, int elapsed, int duration, Runnable complete, Runnable cleanup) {
        try {
            require(elapsed < duration, "OFFLINE autonomy window completed");
            require(level.getEntity(mouse.getUUID()) == mouse
                            && java.util.Objects.equals(PlayerCharacterHarnessBinder.binding(mouse), binding)
                            && LifecycleStartupRuntime.isExactOfflineOwnerBody(mouse, binding)
                            && containment.ownerOf(mouse).orElse(null) == owner && owner.isValid(),
                    "same original OFFLINE mouse/Mind and exact portable custody persist throughout autonomy window");
            com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.tick(mouse, level);
            var timed = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
            require(timed == null || timed.operations().stream().noneMatch(operation ->
                            operation.kind().equals(com.juicyslew.moonstation14.ms14.hands.live.PortableBodyEscapeService.OPERATION_KIND)),
                    "OFFLINE-owned mouse has no portable-escape operation at tick " + elapsed);
            require(mouse.position().equals(originalPosition) && !com.juicyslew.moonstation14.ms14.mobs
                            .NpcBehaviorGameTestAccess.hasAutonomousFlee(mouse),
                    "OFFLINE-owned mouse does not move or choose flee at tick " + elapsed);
            if (elapsed + 1 >= duration) {
                complete.run();
                return;
            }
            helper.runAfterDelay(1, () -> assertOfflineMouseRemainsAutonomousInert(helper, level, mouse, binding,
                    owner, containment, originalPosition, elapsed + 1, duration, complete, cleanup));
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            helper.fail("OFFLINE autonomous mouse escape/flee exclusion: " + failure);
        }
    }

    private static void makeOffline(RegisteredServerPlayerFixture fixture, ServerLevel level) {
        var player = fixture.player();
        level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(
                player.getGameProfile(), 4, true));
        try {
            var dispatcher = level.getServer().getCommands().getDispatcher();
            dispatcher.execute(dispatcher.parse("ms14dev creative", player.createCommandSourceStack()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("canonical Creative park command failed: " + failure);
        }
        require(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                "production development command committed ordinary Creative park");
    }

    private static SavedLifecycleProfile currentProfile(ServerLevel level, java.util.UUID account) {
        try {
            return LifecycleStartupRuntime.contextFor(level.getServer()).orElseThrow()
                    .currentAccountProfile(account).orElseThrow();
        } catch (java.io.IOException failure) {
            throw new GameTestAssertException("durable primary profile read failed: " + failure);
        }
    }

    private static boolean lifecycleFieldsUnchanged(SavedLifecycleProfile before, SavedLifecycleProfile after) {
        return before.schemaVersion() == after.schemaVersion()
                && before.appearance().equals(after.appearance())
                && before.state() == after.state()
                && before.revision() <= after.revision();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
