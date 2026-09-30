package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaSystem;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.organ.BodyState;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.organ.RespirationState;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HumanHarnessVacuumGameTests {
    private HumanHarnessVacuumGameTests() { }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void boundHarnessTakesPressureAndSuffocationOnlyInKnownVacuum(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(2, 1, 2));
        CompoundTag saved = new CompoundTag();
        CompoundTag owner = new CompoundTag();
        owner.putUUID("Account", UUID.randomUUID());
        owner.putString("Profile", "main");
        owner.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", owner);
        body.readAdditionalSaveData(saved);
        require(body.playerCharacterBinding() != null
                        && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID)
                        && CharacterIdentitySystem.resolveForActor(body)
                        .filter(data -> data == ModCharacters.require(level, ModCharacters.HUMAN_ID)).isPresent(),
                "bound lifecycle body must resolve the current canonical human actor prototype");

        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        for (int y = 1; y <= 2; y++) for (int i = 1; i <= 3; i++) {
            helper.setBlock(new BlockPos(1, y, i), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, i), Blocks.STONE);
            helper.setBlock(new BlockPos(i, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(i, y, 3), Blocks.STONE);
        }
        BlockPos eye = BlockPos.containing(body.getEyePosition());
        require(vacuum.sample(level, eye).isEmpty(), "covered unclaimed room is unknown, not vacuum");
        require(BodySystem.reconcile(body) && LungSystem.reconcile(body),
                "bound human harness must initialize its body and respiration");
        BodyState initialized = BodySystem.current(body).orElseThrow();
        require(initialized.find(OrganCategory.LUNGS).isPresent() && initialized.respiration().initialized(),
                "prototype initialization must provide a real human lung and respiration");
        require(BodySystem.updateRespiration(body, new RespirationState(1, true, initialized.respiration().phase())),
                "low-saturation fixture must update the enrolled body");
        BodyState beforeUnknown = BodySystem.current(body).orElseThrow();
        float initialHealth = body.getHealth();
        require(!BarotraumaSystem.tickIfDue(body, -body.getId(), vacuum)
                        && !LungSystem.tickIfDue(body, -body.getId(), vacuum)
                        && body.getHealth() == initialHealth
                        && BodySystem.current(body).orElseThrow().equals(beforeUnknown)
                        && !body.hasData(ModDataAttachments.DAMAGE.get()),
                "unknown atmosphere must not be interpreted as damaging vacuum");

        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        body.setPos(sky.getX() + .5, sky.getY() - body.getEyeHeight(), sky.getZ() + .5);
        var sample = vacuum.sample(level, BlockPos.containing(body.getEyePosition()));
        require(sample.isPresent() && sample.orElseThrow().pressureKpa(1) == 0,
                "exposure test requires proven strict exterior zero-pressure vacuum");
        require(BarotraumaSystem.tickIfDue(body, -body.getId(), vacuum),
                "bound human harness must receive its pressure hit");
        float pressureHealth = body.getHealth();
        require(pressureHealth < initialHealth
                        && body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) != null
                        && body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap()
                        .getOrDefault("blunt", 0f) > 0,
                "pressure damage must persist both on server health and typed ledger");
        long breathDue = Math.floorMod(-body.getId(), 40);
        require(LungSystem.tickIfDue(body, breathDue, vacuum), "known vacuum advances a due breath");
        require(BodySystem.current(body).orElseThrow().respiration().saturation() < 0
                        && body.getHealth() < pressureHealth
                        && body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap()
                        .getOrDefault("asphyxiation", 0f) > 0,
                "vacuum respiration must reach server health and asphyxiation ledger");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void ghostAndUnboundSpectatorDoNotInheritHumanHostExposure(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        var ghost = helper.spawn(GhostMobHarnessRegistration.getEntityType(), new BlockPos(2, 1, 2));
        ServerPlayer spectator = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "vacuum-carrier"), ClientInformation.createDefault()) {
            @Override public boolean isSpectator() { return true; }
        };
        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        ghost.setPos(sky.getX() + .5, sky.getY() - ghost.getEyeHeight(), sky.getZ() + .5);
        require(vacuum.sample(level, BlockPos.containing(ghost.getEyePosition())).isPresent(),
                "ghost negative control must sample known exterior vacuum");
        require(CharacterIdentitySystem.resolveForActor(ghost).isEmpty()
                        && CharacterIdentitySystem.resolveForActor(spectator).isEmpty(),
                "ghost and unbound Spectator carrier have no human actor identity");
        float ghostHealth = ghost.getHealth();
        float carrierHealth = spectator.getHealth();
        require(!BarotraumaSystem.tickIfDue(ghost, -ghost.getId(), vacuum)
                        && !LungSystem.tickIfDue(ghost, -ghost.getId(), vacuum)
                        && !BarotraumaSystem.tickIfDue(spectator, -spectator.getId(), vacuum)
                        && ghost.getHealth() == ghostHealth && spectator.getHealth() == carrierHealth
                        && !ghost.hasData(ModDataAttachments.BODY.get())
                        && !spectator.hasData(ModDataAttachments.BODY.get())
                        && !ghost.hasData(ModDataAttachments.DAMAGE.get())
                        && !spectator.hasData(ModDataAttachments.DAMAGE.get()),
                "human harness exposure cannot hurt the ghost or an unbound spectator carrier via pressure");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
