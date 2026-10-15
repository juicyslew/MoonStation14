package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Integration coverage for server travel of movement-owned retained corpses. */
@GameTestTags({"medical-state", "death", "posture", "containment", "movement", "corpse-damage"})
@PrefixGameTestTemplate(false)
public final class OwnedCorpsePhysicsGameTests {
    private OwnedCorpsePhysicsGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void nativeDamageEntersCanonicalLedgerForRetainedCorpse(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "harness is created");
        body.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        body.setNoAi(true);
        require(helper.getLevel().addFreshEntity(body), "harness joins world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "corpse-damage",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness binds as human");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body), "typed hit creates retained corpse");
        UUID id = body.getUUID();
        float before = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().get(DamageKeys.BLUNT);
        body.invulnerableTime = 0;
        require(body.hurt(helper.getLevel().damageSources().generic(), 2f), "native hit is admitted");
        float after = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().get(DamageKeys.BLUNT);
        require(after > before, "native hit is observed into canonical ledger");
        body.invulnerableTime = 0;
        float piercingBefore = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().getOrDefault(DamageKeys.PIERCING, 0f);
        require(body.hurt(helper.getLevel().damageSources().arrow(null, null), 1f), "native projectile hit is admitted");
        float piercingAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().getOrDefault(DamageKeys.PIERCING, 0f);
        require(piercingAfter > piercingBefore, "projectile damage is classified as piercing");
        body.invulnerableTime = 0;
        float heatBefore = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().getOrDefault(DamageKeys.HEAT, 0f);
        require(body.hurt(helper.getLevel().damageSources().onFire(), 1f), "native fire hit is admitted");
        float heatAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().getOrDefault(DamageKeys.HEAT, 0f);
        require(heatAfter > heatBefore, "fire damage is classified as heat");
        require(helper.getLevel().getEntity(id) == body && !body.isRemoved() && body.getHealth() == 0f
                        && MobStateSystem.isDead(body), "damage preserves retained native corpse identity and state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags("corpse-damage")
    public static void canceledTypedDamageDoesNotPartiallyCommitRetainedCorpse(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = retainedDeadHuman(helper, "canceled-corpse-damage");
        var ledger = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get()).getMap();
        require(ledger.containsKey(DamageKeys.HEAT), "initial ledger contains heat for exact whole-map comparison");
        float bluntBefore = ledger.getOrDefault(DamageKeys.BLUNT, 0f);
        Map<String, Float> before = Map.copyOf(ledger);
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity() == body) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            require(DamageSystem.applyHealthChange(body,
                    Map.of(DamageKeys.BLUNT, 10f, DamageKeys.HEAT, -5f), 1f, false)
                    == DamageSystem.Result.APPLIED, "typed mutation attempt completes despite native cancellation");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        require(before.equals(body.getExistingDataOrNull(
                        com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get()).getMap()),
                "canceled positive event leaves the entire typed ledger unchanged");
        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                        .getMap().getOrDefault(DamageKeys.BLUNT, 0f) == bluntBefore,
                "no positive partial commit");
        body.invulnerableTime = 0;
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 10f), 1f, false)
                == DamageSystem.Result.APPLIED, "accepted typed hit applies after listener removal");
        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                        .getMap().get(DamageKeys.BLUNT) == bluntBefore + 10f,
                "accepted hit increments blunt exactly once");
        require(body.getHealth() == 0f && MobStateSystem.isDead(body)
                        && helper.getLevel().getEntity(body.getUUID()) == body,
                "accepted typed hit preserves retained zero-health DEAD identity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags("corpse-damage")
    public static void nativeHitDoesNotAdmitUnretainedDeadCow(GameTestHelper helper) {
        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(3, 2, 3));
        require(DamageSystem.applyHealthChange(cow, Map.of(DamageKeys.BLUNT, 1000f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(cow), "native cow reaches dead state");
        Map<String, Float> before = Map.copyOf(cow.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get()).getMap());
        cow.invulnerableTime = 0;
        require(!cow.hurt(helper.getLevel().damageSources().generic(), 1f),
                "generic native hit is rejected for an unretained dead cow");
        require(before.equals(cow.getExistingDataOrNull(
                        com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get()).getMap()),
                "rejected native hit stores no typed increment");
        helper.succeed();
    }

    private static PlayerCharacterHarnessEntity retainedDeadHuman(GameTestHelper helper, String label) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "harness is created");
        body.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        body.setNoAi(true);
        require(helper.getLevel().addFreshEntity(body), "harness joins world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), label,
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness binds as human");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.HEAT, 20f), 1f, true)
                == DamageSystem.Result.APPLIED, "heat ledger is seeded");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body), "typed damage creates retained corpse");
        body.invulnerableTime = 0;
        return body;
    }

    @GameTest(templateNamespace = "moonstation14", template = "power_receiver_tall_clear", timeoutTicks = 80)
    public static void ownedMedicalCorpseFallsAndSettlesWithNativeTicks(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            helper.getLevel().setBlock(new BlockPos(floor.getX() + x, floor.getY(), floor.getZ() + z),
                    Blocks.STONE.defaultBlockState(), 3);

        BlockPos spawn = helper.absolutePos(new BlockPos(3, 7, 3));
        var tickets = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(15, 12, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 8, () -> {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), "harness joins the world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "corpse-physics",
                        UUID.randomUUID(), ModCharacters.HUMAN_ID),
                "harness resolves as a bound human character");
        bootstrapHands(body);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        UUID id = body.getUUID();
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                        == DamageSystem.Result.APPLIED,
                "typed damage reaches the native death boundary");
        require(MobStateSystem.isDead(body) && body.isDeadOrDying() && PostureSystem.isDown(body),
                "real damage produces a natively confirmed, downed medical corpse");
        require(((MindControlledMob) (Object) body).moonstation14$isMovementOwned() && body.isNoAi(),
                "death retains movement ownership and disabled AI");
        float bluntBeforeFall = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                .getMap().get(DamageKeys.BLUNT);

        int startingTick = body.tickCount;
        double initialY = body.getY();
        helper.runAfterDelay(30, () -> {
            float bluntAfterFall = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                    .getMap().get(DamageKeys.BLUNT);
            require(helper.getLevel().getEntity(id) == body && !body.isRemoved(),
                    "same corpse remains registered");
            require(bluntAfterFall > bluntBeforeFall, "native fall damage producer reaches retained corpse ledger");
            require(body.getY() < initialY - 4d, "normal server ticks move the corpse down from midair");
            require(body.onGround(), "corpse settles on supporting floor");
            require(body.tickCount - startingTick >= 30, "native actor ticks advance throughout fall");
            require(body.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.LEATHER_HELMET,
                    "corpse retains worn equipment");
            require(body.getHealth() == 0f && MobStateSystem.isDead(body),
                    "native fall preserves zero-health DEAD state");
            require(((MindControlledMob) (Object) body).moonstation14$isMovementOwned() && body.isNoAi(),
                    "settled corpse remains owned with AI disabled");
            tickets.close();
            helper.succeed();
        });
        }, spawn, helper.absolutePos(new BlockPos(3, 0, 3)));
    }

    @GameTestTags("suite-stabilization")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 50)
    public static void ownedCriticalBodyFallsOnNativeTicks(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 9, 3));
        var tickets = FixtureChunkTickets.around(helper, spawn, spawn.below(9), 1);
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), "harness joins world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "critical-physics",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness binds as human");
        bootstrapHands(body);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isCritical(body), "body enters typed Critical");
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 8, () -> {
            require(tickets.entityTicking(body.blockPosition()), "Critical body chunk is entity-ticking before native travel: "
                    + tickets.diagnostic(body.blockPosition()));
            double initialY = body.getY();
            UUID id = body.getUUID();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 12, () -> {
                require(tickets.entityTicking(body.blockPosition()), "Critical body remains entity-ticking: "
                        + tickets.diagnostic(body.blockPosition()));
                require(helper.getLevel().getEntity(id) == body && body.getY() < initialY - .5,
                        "native ticks passively move the owned Critical body");
                require(body.isNoAi() && ((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                        "passive motion retains disabled AI and ownership");
                tickets.close();
                helper.succeed();
            });
        }, body.blockPosition());
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void containedCriticalUnitBodyIsNotSteppedByCorpsePhysics(GameTestHelper helper) {
        containedCriticalBodyIsNotStepped(helper, ContainedBodyOwner.Stage.UNIT, "unit");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void containedCriticalTransitHolderBodyIsNotSteppedByCorpsePhysics(GameTestHelper helper) {
        containedCriticalBodyIsNotStepped(helper, ContainedBodyOwner.Stage.TRANSIT, "holder");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void unownedCriticalBodyReceivesExternalFlightWithoutDuplicateStep(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 8, 3));
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "critical harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        require(helper.getLevel().addFreshEntity(body), "harness joins the world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "unowned-flight",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness binds as human");
        bootstrapHands(body);
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isCritical(body), "body enters typed Critical");
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(false);
        double x = body.getX();
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(body,
                new net.minecraft.world.phys.Vec3(.2, 0, 0), 4);
        require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(body),
                "external flight tracks the unowned retained corpse");
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tick(helper.getLevel());
        require(body.getX() > x && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                .alreadyAdvancedThisTick(body), "corpse flight advances once through the passive physics consumer");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"merge-main-regression", "repair-native-residency"})
    public static void containedDeadUnitBodyRemainsPinnedAndResumesCorpsePhysicsOnExit(GameTestHelper helper) {
        containedDeadBodyRetainedAcrossDeath(helper, ContainedBodyOwner.Stage.UNIT, "unit");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    @GameTestTags({"merge-main-regression"})
    public static void containedDeadTransitHolderBodyRemainsPinnedAndResumesCorpsePhysicsOnExit(GameTestHelper helper) {
        containedDeadBodyRetainedAcrossDeath(helper, ContainedBodyOwner.Stage.TRANSIT, "holder");
    }

    private static void containedDeadBodyRetainedAcrossDeath(GameTestHelper helper, ContainedBodyOwner.Stage stage,
                                                              String label) {
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 8, 3));
        BlockPos exitPosition = spawn.offset(1, 0, 0);
        var ticket = FixtureChunkTickets.around(helper, spawn, exitPosition, 1);
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, label + " harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), label + " harness joins the world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "dead-contained-" + label,
                UUID.randomUUID(), ModCharacters.HUMAN_ID), label + " harness binds as human");
        bootstrapHands(body);
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isCritical(body), "body enters Critical before admission");
        boolean initiallyMovementOwned = ((MindControlledMob) (Object) body).moonstation14$isMovementOwned();
        BlockPos pin = spawn.offset(1, 0, 0);
        var owner = new ContainedBodyOwner() {
            private final UUID id = UUID.randomUUID();
            public UUID identity() { return id; }
            public net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
            public net.minecraft.world.phys.Vec3 position() { return new net.minecraft.world.phys.Vec3(
                    exitPosition.getX() + .5, exitPosition.getY(), exitPosition.getZ() + .5); }
            public Stage stage() { return stage; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        var containment = BodyContainmentService.forLevel(helper.getLevel());
        require(containment.bind(body, owner), label + " exact body binds while Critical");
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body) && !body.isAlive(),
                "bound body crosses native retained-death boundary");
        UUID id = body.getUUID();
        double pinnedY = body.getY();
        double[] beforeFall = new double[1];
        FixtureChunkTickets.awaitEntityTicking(helper, ticket, 20,
                () -> body.tickCount > 0, "body native tick advanced", () -> {
        helper.runAfterDelay(8, () -> {
            require(helper.getLevel().getEntity(id) == body && !body.isRemoved(), label + " dead body remains registered");
            require(containment.ownerOf(body).orElse(null) == owner && body.getY() == pinnedY,
                    label + " dead body remains contained and pinned");
            require(body.isNoGravity() && body.noPhysics, label + " containment flags remain active");
            require(containment.unbind(body, owner), label + " exit restores the body");
            require(!body.isNoGravity() && !body.noPhysics && OwnedCorpsePhysics.eligible(body),
                    "uncontained retained corpse returns to passive physics eligibility");
            beforeFall[0] = body.getY();
            int exitTick = body.tickCount;
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 12,
                    () -> "native post-exit tick did not advance", () -> body.tickCount > exitTick, () -> {
            require(helper.getLevel().getEntity(id) == body && body.getY() < beforeFall[0],
                    "native world ticks resume corpse physics after exit");
            require(MobStateSystem.isDead(body) && !body.isRemoved()
                            && ((MindControlledMob) (Object) body).moonstation14$isMovementOwned() == initiallyMovementOwned
                            && body.isNoAi(),
                    "native fall preserves retained DEAD identity and initial movement ownership");
            ticket.close();
            helper.succeed();
            });
        });
        }, spawn, exitPosition);
    }

    private static void containedCriticalBodyIsNotStepped(GameTestHelper helper, ContainedBodyOwner.Stage stage,
                                                           String label) {
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 8, 3));
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, label + " critical harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        require(helper.getLevel().addFreshEntity(body), label + " harness joins the world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "contained-corpse-" + label,
                UUID.randomUUID(), ModCharacters.HUMAN_ID), label + " harness binds as human");
        bootstrapHands(body);
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isCritical(body), "body enters typed Critical");
        var owner = new ContainedBodyOwner() {
            private final UUID id = UUID.randomUUID();
            public UUID identity() { return id; }
            public net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
            public net.minecraft.world.phys.Vec3 position() { return new net.minecraft.world.phys.Vec3(
                    spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5); }
            public Stage stage() { return stage; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        BodyContainmentService service = BodyContainmentService.forLevel(helper.getLevel());
        require(service.bind(body, owner), label + " exact-body containment binds");
        double pinnedY = body.getY();
        body.setDeltaMovement(0, -1, 0);
        require(!OwnedCorpsePhysics.eligible(body) && !OwnedCorpsePhysics.step(body),
                label + " contained critical body is excluded before passive movement");
        helper.runAfterDelay(8, () -> {
            require(body.getY() == pinnedY && service.ownerOf(body).orElse(null) == owner,
                    label + " containment remains pinned while normal death ticks run");
            service.unbind(body, owner);
            helper.succeed();
        });
    }

    private static void bootstrapHands(PlayerCharacterHarnessEntity body) {
        require(BodyHandBootstrap.ensure(body) != BodyHandBootstrap.Result.REJECTED,
                "bound harness bootstraps composed body hands");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
