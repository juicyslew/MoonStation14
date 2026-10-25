package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"particle-accelerator-particle", "singularity-generator"})
@PrefixGameTestTemplate(false)
public final class ParticleAcceleratorParticleGameTests {
    private ParticleAcceleratorParticleGameTests() { }

    private static final class Cleanup {
        private final GameTestHelper h;
        private final java.util.List<Entity> entities = new java.util.ArrayList<>();
        private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new java.util.LinkedHashMap<>();
        Cleanup(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { clean(); }
            });
        }
        <T extends Entity> T own(T entity) { entities.add(entity); return entity; }
        void ownBlock(BlockPos pos) { blocks.putIfAbsent(pos, h.getLevel().getBlockState(pos)); }
        void clean() {
            for (Entity entity : entities) {
                if (entity instanceof SingularityEntity singularity)
                    com.juicyslew.moonstation14.ms14.radiation.RadiationService.INSTANCE.remove(h.getLevel(), singularity.identity());
                entity.discard();
            }
            blocks.forEach((pos, state) -> h.getLevel().setBlock(pos, state, 3));
        }
    }

    private static net.minecraft.world.entity.monster.Zombie unarmoredZombie(GameTestHelper h, Cleanup cleanup, BlockPos pos) {
        var zombie = cleanup.own(EntityType.ZOMBIE.create(h.getLevel()));
        if (zombie == null) throw new GameTestAssertException("native zombie type exists");
        zombie.moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,0,0);
        zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setDeltaMovement(Vec3.ZERO);
        zombie.setPersistenceRequired();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            zombie.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        if (!h.getLevel().addFreshEntity(zombie)) throw new GameTestAssertException("native unarmored zombie enters world");
        return zombie;
    }

    private static ParticleAcceleratorParticleEntity particle(GameTestHelper h, Cleanup cleanup,
            ParticleAcceleratorStrengthProfile strength, Vec3 position, Vec3 movement) {
        var particle = cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(), ModParticleAcceleratorParticles.DEFAULT_ID,
                strength, UUID.randomUUID()));
        particle.moveTo(position.x,position.y,position.z,0,0); particle.setDeltaMovement(movement);
        if (!h.getLevel().addFreshEntity(particle)) throw new GameTestAssertException("native particle enters world");
        return particle;
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=60)
    public static void movingResolvedParticleChargesOwnedSingularityGeneratorAndIsConsumed(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        for (int[] corner : new int[][]{{1,1,1},{5,1,1},{1,5,1},{5,5,1},{1,1,5},{5,1,5},{1,5,5},{5,5,5}})
            cleanup.ownBlock(h.absolutePos(new BlockPos(corner[0],corner[1],corner[2])));
        var corners = ContainmentFixture.placeBox(h);
        BlockPos localGen = new BlockPos(3,3,3);
        cleanup.ownBlock(h.absolutePos(localGen));
        h.setBlock(localGen, ModBlocks.SINGULARITY_GENERATOR.get());
        BlockPos genPos = h.absolutePos(localGen);
        if (!(h.getLevel().getBlockEntity(genPos) instanceof SingularityGeneratorBlockEntity gen))
            throw new GameTestAssertException("registered singularity generator adapter exists");
        var strengthProfile = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        var particleProfile = ModParticleAcceleratorParticles.catalog(h.getLevel()).get(ModParticleAcceleratorParticles.DEFAULT_ID);
        if (strengthProfile == null || particleProfile == null) throw new GameTestAssertException("resolved PA profiles exist");
        var strength = strengthProfile.strength(ParticleAcceleratorStrength.LEVEL_2);
        var particle = cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(), ModParticleAcceleratorParticles.DEFAULT_ID,
                strength, UUID.randomUUID()));
        particle.moveTo(h.absolutePos(new BlockPos(2,3,3)).getX()+.5, h.absolutePos(new BlockPos(2,3,3)).getY()+.5,
                h.absolutePos(new BlockPos(2,3,3)).getZ()+.5, 0, 0);
        particle.setDeltaMovement(2,0,0);
        if (!h.getLevel().addFreshEntity(particle)) throw new GameTestAssertException("native PA projectile enters world");
        h.runAfterDelay(2, () -> {
            if (gen.startupCharge() != 4) throw new GameTestAssertException("actual resolved LEVEL_2 generator charge delivered once");
            if (!particle.isRemoved()) throw new GameTestAssertException("special generator contact always consumes projectile");
            particle.discard(); h.setBlock(localGen, Blocks.AIR);
            for (var field : corners) h.getLevel().setBlock(field.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=60)
    public static void wallPassesAndLivingRadiationContactIsNotRepeatedWhileOverlapping(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        var particleProfile = ModParticleAcceleratorParticles.catalog(h.getLevel()).get(ModParticleAcceleratorParticles.DEFAULT_ID);
        var pa = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        var zombie = unarmoredZombie(h, cleanup, h.absolutePos(new BlockPos(4,3,3)));
        if (particleProfile == null || pa == null || zombie == null) throw new GameTestAssertException("resolved particle/profile and living target exist");
        BlockPos wall = h.absolutePos(new BlockPos(3,3,3)); cleanup.ownBlock(wall); h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        BlockPos target = h.absolutePos(new BlockPos(4,3,3));
        var particle = cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(), ModParticleAcceleratorParticles.DEFAULT_ID,
                pa.strength(ParticleAcceleratorStrength.LEVEL_2), UUID.randomUUID()));
        BlockPos origin=h.absolutePos(new BlockPos(3,3,3)); particle.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5,0,0);
        particle.setDeltaMovement(1,0,0);
        if (!h.getLevel().addFreshEntity(particle)) throw new GameTestAssertException("native particle enters world");
        // Advance the real entity consumer once so it enters the target's expanded bounds, then hold
        // it there: scheduled world ticks must not move it out before overlap assertions run.
        particle.tick();
        particle.setDeltaMovement(Vec3.ZERO);
        var profile = particle.resolvedProfile();
        if (profile == null || !zombie.getBoundingBox().inflate(profile.halfExtent()).contains(particle.position()))
            throw new GameTestAssertException("particle stopped inside living target contact bounds; pos="+particle.position()+" target="+zombie.getBoundingBox()+" profile="+profile);
        float radiation = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION,0f);
        traceContact(h, zombie, particle, "initial");
        if (Math.abs(radiation-25f) > .001f)
            throw new GameTestAssertException("unarmored native living contact records 25 radiation through wall; actual="+radiation+" health="+zombie.getHealth()+" armor="+zombie.getArmorValue()+" bounds="+zombie.getBoundingBox()+" particle="+particle.position()+" profile="+profile);
        for (int tick = 1; tick <= 12; tick++) {
            final int elapsed = tick;
            h.runAfterDelay(tick, () -> traceContact(h, zombie, particle, "overlap-" + elapsed));
        }
        h.runAfterDelay(12, () -> {
            float actual = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION,0f);
            if (Math.abs(actual-radiation) > .001f)
                throw new GameTestAssertException("persistent overlap does not repeat radiation; before="+radiation+" actual="+actual+" particle="+particle.position()+" target="+zombie.getBoundingBox());
            particle.setPos(particle.getX()+5, particle.getY(), particle.getZ());
            particle.tick();
            particle.setPos(target.getX()+.5, target.getY()+.5, target.getZ()+.5);
            particle.tick();
            float reentry = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION,0f);
            if (Math.abs(reentry-50f) > .001f)
                throw new GameTestAssertException("leaving and re-entering applies second native hit; actual="+reentry+" particle="+particle.position()+" target="+zombie.getBoundingBox());
            h.succeed();
        });
    }

    private static void traceContact(GameTestHelper h, net.minecraft.world.entity.LivingEntity actor,
                                     ParticleAcceleratorParticleEntity particle, String phase) {
        var data = actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        var trace = "PA_CONTACT_DIAG phase=" + phase + " tick=" + h.getLevel().getGameTime()
                + " actor=" + actor.getUUID() + " registered=" + actor.isAddedToLevel()
                + " alive=" + actor.isAlive() + " health=" + actor.getHealth()
                 + " medical=" + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(actor)
                + " persistent=" + (actor instanceof net.minecraft.world.entity.Mob fixtureMob && fixtureMob.isPersistenceRequired()) + " removalReason=" + actor.getRemovalReason()
                + " ledger=" + DamageSystem.existingOrVanillaBaseline(actor).getOrDefault(DamageKeys.RADIATION, 0f)
                + " attachment=" + (data == null ? "null" : System.identityHashCode(data) + ":" + data.getMap())
                + " particle=" + particle.getUUID() + " source=" + particle.sourceEmitterIdentity()
                + " removed=" + particle.isRemoved() + " pos=" + particle.position()
                + " delta=" + particle.getDeltaMovement() + " profile=" + particle.resolvedProfile();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(trace);
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void profileHalfExtentDetectsGrazingContactBelowNativeEntityBounds(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        var pa = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        var zombie = unarmoredZombie(h, cleanup, h.absolutePos(new BlockPos(4,3,3)));
        if (pa == null || zombie == null) throw new GameTestAssertException("resolved PA strength and grazing target exist");
        var particle = cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(), ModParticleAcceleratorParticles.DEFAULT_ID,
                pa.strength(ParticleAcceleratorStrength.LEVEL_2), UUID.randomUUID()));
        particle.moveTo(zombie.getX(),zombie.getY()-.25,zombie.getZ(),0,0);
        particle.setDeltaMovement(1,0,0);
        if (!h.getLevel().addFreshEntity(particle)) throw new GameTestAssertException("grazing particle enters world");
        particle.tick();
        float radiation = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION,0f);
        if (Math.abs(radiation-25f)>.001f)
            throw new GameTestAssertException("profile half extent admits grazing contact outside native particle box; radiation="+radiation+" particle="+particle.position()+" target="+zombie.getBoundingBox()+" profile="+particle.resolvedProfile());
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=60)
    public static void particleFeedsSingularityFromResolvedFoodStrengthOnce(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        var particleProfile=ModParticleAcceleratorParticles.catalog(h.getLevel()).get(ModParticleAcceleratorParticles.DEFAULT_ID);
        var pa=ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        SingularityEntity singularity=cleanup.own(ModEntities.SINGULARITY.get().create(h.getLevel()));
        if(particleProfile==null||pa==null||singularity==null)throw new GameTestAssertException("resolved particle, PA and singularity types exist");
        BlockPos center=h.absolutePos(new BlockPos(5,3,3)); singularity.moveTo(center.getX()+.5,center.getY()+.5,center.getZ()+.5,0,0);
        singularity.initializeFromGenerator(); singularity.setDeltaMovement(Vec3.ZERO);
        if(!h.getLevel().addFreshEntity(singularity))throw new GameTestAssertException("native singularity enters world");
        double before=singularity.energy();
        var strength=pa.strength(ParticleAcceleratorStrength.LEVEL_2);
        var particle=cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(),ModParticleAcceleratorParticles.DEFAULT_ID,
                strength,UUID.randomUUID()));
        BlockPos origin=h.absolutePos(new BlockPos(2,3,3));particle.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5,0,0);
        particle.setDeltaMovement(2.5,0,0);h.getLevel().addFreshEntity(particle);
        particle.tick();
        double delta=singularity.energy()-before;
        if(strength.singularityFood()!=30 || Math.abs(delta-strength.singularityFood())>.001 || !particle.isRemoved())
            throw new GameTestAssertException("native event-horizon contact transfers resolved food exactly once; expected="+strength.singularityFood()+" actual="+delta+" particle="+particle.position());
        h.runAfterDelay(2,()->{
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void nearestSphereContactWinsOverFartherLivingContact(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        var pa = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        var singularity = cleanup.own(ModEntities.SINGULARITY.get().create(h.getLevel()));
        if (pa == null || singularity == null) throw new GameTestAssertException("resolved PA profile and singularity exist");
        BlockPos sigPos = h.absolutePos(new BlockPos(3,3,3));
        singularity.moveTo(sigPos.getX()+.5,sigPos.getY()+.5,sigPos.getZ()+.5,0,0);
        singularity.initializeFromGenerator(); singularity.setDeltaMovement(Vec3.ZERO);
        if (!h.getLevel().addFreshEntity(singularity)) throw new GameTestAssertException("near singularity enters world");
        var zombie = unarmoredZombie(h, cleanup, h.absolutePos(new BlockPos(4,3,3)));
        var strength = pa.strength(ParticleAcceleratorStrength.LEVEL_2);
        double before = singularity.energy();
        BlockPos start = h.absolutePos(new BlockPos(0,3,3));
        var particle = particle(h, cleanup, strength, new Vec3(start.getX()+.5,start.getY()+.5,start.getZ()+.5), new Vec3(6,0,0));
        particle.tick();
        float radiation = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION,0f);
        double food = singularity.energy()-before;
        if (Math.abs(food-strength.singularityFood())>.001 || Math.abs(radiation)>.001 || !particle.isRemoved())
            throw new GameTestAssertException("nearest sphere entry consumes before farther living contact; food="+food+" expected="+strength.singularityFood()+" radiation="+radiation+" particle="+particle.position()+" sphere="+singularity.position()+" target="+zombie.getBoundingBox());
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void canceledLivingHitSpendsParticleButSpecialFoodContactStillWorks(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        var pa = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        if (pa == null) throw new GameTestAssertException("resolved PA strength exists");
        var blocked = unarmoredZombie(h, cleanup, h.absolutePos(new BlockPos(3,3,3)));
        var later = unarmoredZombie(h, cleanup, h.absolutePos(new BlockPos(4,3,3)));
        // Native LivingEntity hurt admission rejects the first hit; this exercises the actual
        // rejected-result path without injecting damage or mutating the particle's spent state.
        blocked.setInvulnerable(true);
        var strength = pa.strength(ParticleAcceleratorStrength.LEVEL_2);
        var particle = particle(h, cleanup, strength,
                new Vec3(h.absolutePos(new BlockPos(2,3,3)).getX()+.5,h.absolutePos(new BlockPos(2,3,3)).getY()+.5,h.absolutePos(new BlockPos(2,3,3)).getZ()+.5),
                new Vec3(1,0,0));
        particle.tick();
        if (Math.abs(DamageSystem.existingOrVanillaBaseline(blocked).getOrDefault(DamageKeys.RADIATION,0f))>.001)
            throw new GameTestAssertException("native invulnerability rejects first living hit");
        particle.setPos(h.absolutePos(new BlockPos(3,3,3)).getX()+.5,h.absolutePos(new BlockPos(3,3,3)).getY()+.5,h.absolutePos(new BlockPos(3,3,3)).getZ()+.5);
        particle.tick();
        if (Math.abs(DamageSystem.existingOrVanillaBaseline(later).getOrDefault(DamageKeys.RADIATION,0f))>.001)
            throw new GameTestAssertException("spent particle suppresses later ordinary living contact");

        var singularity = cleanup.own(ModEntities.SINGULARITY.get().create(h.getLevel()));
        BlockPos sigPos = h.absolutePos(new BlockPos(6,3,3));
        singularity.moveTo(sigPos.getX()+.5,sigPos.getY()+.5,sigPos.getZ()+.5,0,0);
        singularity.initializeFromGenerator(); singularity.setDeltaMovement(Vec3.ZERO);
        if (!h.getLevel().addFreshEntity(singularity)) throw new GameTestAssertException("special-contact singularity enters world");
        double before = singularity.energy();
        particle.setPos(h.absolutePos(new BlockPos(5,3,3)).getX()+.5,h.absolutePos(new BlockPos(5,3,3)).getY()+.5,h.absolutePos(new BlockPos(5,3,3)).getZ()+.5);
        particle.setDeltaMovement(1,0,0); particle.tick();
        double food = singularity.energy()-before;
        if (Math.abs(food-strength.singularityFood())>.001 || !particle.isRemoved())
            throw new GameTestAssertException("spent particle still transfers resolved food to special contact; food="+food+" expected="+strength.singularityFood()+" removed="+particle.isRemoved());
        h.succeed();
    }
}
