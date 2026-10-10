package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.KnockdownRenderPolicy;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.mixin.LivingEntityImmobileInvoker;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle", "character"})
public final class CharacterControlGameTests {
    private CharacterControlGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 140)
    @GameTestTags({"posture_knockdown"})
    public static void authoritativeKnockdownDrivesRendererProjectionUntilExpiry(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "knockdown-projection"));
        player.setPos(2.5, 1, 2.5);
        require(helper.getLevel().addFreshEntity(player), "fake player must join");
        helper.runAfterDelay(1, () -> {
            require(CharacterControlSystem.applyParalyze(player, 3), "bound player accepts paralyze");
            require(CharacterControlSystem.isKnockedDown(player), "authoritative knockdown status is active");
            require(com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(player)
                            && player.getPose() == net.minecraft.world.entity.Pose.SWIMMING
                            && Math.abs(player.getBoundingBox().getYsize() - .6) < .001,
                    "actual PlayerCharacterHarness player is physically prone with lowered dimensions");
            require(KnockdownRenderPolicy.shouldRenderProne(player),
                    "production renderer adapter projects active synchronized knockdown");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 102, () -> {
                require(!CharacterControlSystem.isKnockedDown(player), "knockdown status expires");
                require(!KnockdownRenderPolicy.shouldRenderProne(player),
                        "renderer adapter restores standing rotation after status expiry");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 25, () -> {
                    require(!com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(player),
                            "posture auto-stands after finite hold and existing get-up timer");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void boundHumanPlayerAndVillagerShareTimedStunPolicy(GameTestHelper helper) {
        var tickets = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(
                helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1), 2);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        var cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 1));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stun-policy"));
        player.setPos(3.5, 1, 1.5);
        require(helper.getLevel().addFreshEntity(player), "fake player must join");
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(
                helper, tickets, 8, () -> helper.runAfterDelay(1, () -> {
            require(CharacterControlSystem.applyStun(villager, 3), "bound villager accepts stun");
            int villagerTickAtApply = villager.tickCount;
            require(CharacterControlSystem.applyStun(player, 3), "bound player accepts stun");
            require(CharacterControlSystem.isStunned(villager), "bound villager must report active stun");
            require(CharacterControlSystem.isStunned(player), "bound fake player must report active stun");
            require(!CharacterControlSystem.canAct(villager) && !CharacterControlSystem.canAct(player),
                    "active stun denies actions through the shared character policy");
            require(((LivingEntityImmobileInvoker) (Object) villager).moonstation14$callIsImmobile()
                            && ((LivingEntityImmobileInvoker) (Object) player).moonstation14$callIsImmobile(),
                    "isImmobile must include active stun");
            require(!CharacterControlSystem.applyStun(cow, 3)
                            && !((LivingEntityImmobileInvoker) (Object) cow).moonstation14$callIsImmobile()
                            && CharacterControlSystem.canAct(cow),
                    "unconfigured Cow remains inert and mobile");
            require(CharacterIdentitySystem.resolve(pig).isPresent()
                            && !CharacterControlSystem.applyStun(pig, 3)
                            && !pig.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                    "configured Pig resolves its local no-stun policy without materializing status");
            require(!cow.hasData(ModDataAttachments.STATUS_EFFECT.get())
                            && !cow.hasData(ModDataAttachments.CHARACTER_IDENTITY.get()),
                    "ineligible queries must not materialize attachments");

            // A malformed/dangling binding resolves fail-closed without changing status storage.
            var identity = new com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment();
            identity.bind(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "missing"));
            MS14Provider.update(cow, MS14Bridges.CHARACTER_IDENTITY, identity);
            require(!CharacterControlSystem.applyStun(cow, 3) && !CharacterControlSystem.isStunned(cow),
                    "malformed profile cannot receive or report a stun");
            require(!cow.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                    "failed profile resolution must not create status storage");

            // Status durations advance on native living ticks, not GameTest sequence ticks.
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 8,
                    () -> "stun expiry did not complete naturally: villagerNativeTickDelta="
                            + (villager.tickCount - villagerTickAtApply)
                            + ", liveActorTicker=LivingEntity.tickCount, villagerRemainingStatusInstance="
                            + MS14Provider.get(villager, MS14Bridges.STATUS_EFFECT)
                            .get(ModStatusEffects.createKey("statuseffectstunned")),
                    () -> villager.tickCount - villagerTickAtApply >= 3
                            && !CharacterControlSystem.isStunned(villager)
                            && !CharacterControlSystem.isStunned(player), () -> {
                require(!CharacterControlSystem.isStunned(villager), "finite status expiry clears villager stun");
                require(!CharacterControlSystem.isStunned(player), "finite status expiry clears fake player stun");
                require(CharacterControlSystem.canAct(villager) && CharacterControlSystem.canAct(player),
                        "status expiry restores actions through the shared character policy");
                require(!((LivingEntityImmobileInvoker) (Object) villager).moonstation14$callIsImmobile()
                                && !((LivingEntityImmobileInvoker) (Object) player).moonstation14$callIsImmobile(),
                        "expired stun must no longer affect isImmobile");
                require(MS14Provider.get(villager, MS14Bridges.STATUS_EFFECT)
                                .get(ModStatusEffects.createKey("statuseffectstunned")).isEmpty(),
                        "expired status attachment entry is removed");
                helper.succeed();
            });
        }), new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1), new BlockPos(4, 1, 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
