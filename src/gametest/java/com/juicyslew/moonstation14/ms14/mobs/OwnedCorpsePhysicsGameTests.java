package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/** Integration coverage for server travel of movement-owned retained corpses. */
@GameTestTags({"medical-state", "death", "posture"})
@PrefixGameTestTemplate(false)
public final class OwnedCorpsePhysicsGameTests {
    private OwnedCorpsePhysicsGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void ownedMedicalCorpseFallsAndSettlesWithNativeTicks(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            helper.getLevel().setBlock(new BlockPos(floor.getX() + x, floor.getY(), floor.getZ() + z),
                    Blocks.STONE.defaultBlockState(), 3);

        BlockPos spawn = helper.absolutePos(new BlockPos(3, 7, 3));
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), "harness joins the world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "corpse-physics",
                        UUID.randomUUID(), ModCharacters.HUMAN_ID),
                "harness resolves as a bound human character");
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

        double initialY = body.getY();
        helper.runAfterDelay(30, () -> {
            require(helper.getLevel().getEntity(id) == body && !body.isRemoved(),
                    "same corpse remains registered");
            require(body.getY() < initialY - 4d, "normal server ticks move the corpse down from midair");
            require(body.onGround(), "corpse settles on supporting floor");
            require(body.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.LEATHER_HELMET,
                    "corpse retains worn equipment");
            require(((MindControlledMob) (Object) body).moonstation14$isMovementOwned() && body.isNoAi(),
                    "settled corpse remains owned with AI disabled");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    public static void ownedCriticalBodyFallsOnNativeTicks(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 9, 3));
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered harness is created");
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), "harness joins world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "critical-physics",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness binds as human");
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isCritical(body), "body enters typed Critical");
        double initialY = body.getY();
        UUID id = body.getUUID();
        helper.runAfterDelay(12, () -> {
            require(helper.getLevel().getEntity(id) == body && body.getY() < initialY - .5,
                    "native ticks passively move the owned Critical body");
            require(body.isNoAi() && ((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                    "passive motion retains disabled AI and ownership");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
