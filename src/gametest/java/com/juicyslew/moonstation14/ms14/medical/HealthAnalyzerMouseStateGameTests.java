package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"health-analyzer", "medical-state", "prototype"})
@PrefixGameTestTemplate(false)
public final class HealthAnalyzerMouseStateGameTests {
    private HealthAnalyzerMouseStateGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void analyzerReadsResolvedMouseStateAcrossTypedDamageAndHealing(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        MouseMob mouse = ModEntities.MOUSE.get().create(level);
        require(mouse != null, "NPC mouse created");
        mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(mouse), "NPC mouse added");
        require(CharacterIdentitySystem.enroll(mouse, level, ResourceLocation.parse("moonstation14:mouse")),
                "NPC mouse enrolled without humanoid profile");

        require(MobStateSystem.policy(mouse) != null, "resolved mouse has medical policy");
        require(mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get()) == null,
                "freshly enrolled healthy mouse has no materialized state attachment");

        var alive = read(mouse);
        require(alive.species().equals("Mouse") && alive.state().equals("Alive") && !alive.unrevivable(),
                "resolved mouse starts Alive without a state attachment (policy=" + MobStateSystem.policy(mouse)
                        + ", stateAttachment=" + mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get())
                        + ", damageAttachment=" + mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get())
                        + ", actual=" + alive.state() + ")");
        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLUNT, 12f), 1f, true)
                == DamageSystem.Result.APPLIED, "typed damage applied");
        require(MobStateSystem.isCritical(mouse), "resolved mob-state system enters Critical");
        var critical = read(mouse);
        require(critical.state().equals("Critical") && !critical.unrevivable(), "analyzer reports Critical without permanent unrevivable warning");

        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLUNT, -4f), 1f, true)
                == DamageSystem.Result.APPLIED, "typed healing applied");
        require(MobStateSystem.isAlive(mouse), "resolved mob-state system heals to Alive");
        var healed = read(mouse);
        require(healed.state().equals("Alive") && !healed.unrevivable(), "analyzer reflects typed healing without permanent unrevivable warning");

        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLUNT, 20f), 1f, true)
                == DamageSystem.Result.APPLIED, "lethal typed damage applied");
        require(MobStateSystem.isDead(mouse), "resolved mob-state system enters Dead");
        var dead = read(mouse);
        require(dead.state().equals("Dead") && !dead.unrevivable(), "dead mouse is not reported permanently unrevivable from ordinary healing policy");
        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLUNT, -4f), 1f, true)
                == DamageSystem.Result.APPLIED, "ordinary typed healing applies to dead mouse");
        require(MobStateSystem.isDead(mouse) && read(mouse).state().equals("Dead")
                        && !read(mouse).unrevivable(), "ordinary healing does not revive dead mouse or imply permanent unrevivability");

        var unsupported = EntityType.COW.create(level);
        require(unsupported != null, "unsupported native entity created");
        unsupported.setPos(pos.getX(), pos.getY(), pos.getZ());
        require(level.addFreshEntity(unsupported), "unsupported entity added");
        require(read(unsupported).state().equals("Unknown"), "entity without resolved medical policy remains Unknown");
        helper.succeed();
    }

    private static HealthAnalyzerSnapshot read(net.minecraft.world.entity.LivingEntity entity) {
        return HealthAnalyzerReadings.read(UUID.randomUUID(), 1, UUID.randomUUID(), entity, 1, true, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
