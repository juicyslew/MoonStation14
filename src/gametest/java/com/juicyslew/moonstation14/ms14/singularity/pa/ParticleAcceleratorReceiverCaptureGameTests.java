package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestTags({"particle-accelerator-firing", "tesla"})
@PrefixGameTestTemplate(false)
public final class ParticleAcceleratorReceiverCaptureGameTests {
    private ParticleAcceleratorReceiverCaptureGameTests() { }

    private static final class Cleanup {
        private final GameTestHelper h;
        private final List<Entity> entities = new ArrayList<>();
        Cleanup(GameTestHelper h) { this.h = h; }
        <T extends Entity> T own(T entity) { entities.add(entity); return entity; }
        void clean() {
            for (Entity entity : entities) {
                if (entity instanceof SingularityEntity singularity)
                    com.juicyslew.moonstation14.ms14.radiation.RadiationService.INSTANCE.remove(h.getLevel(), singularity.identity());
                entity.discard();
            }
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static BlockPos at(GameTestHelper h, int x, int y, int z) {
        return h.absolutePos(new BlockPos(x, y, z));
    }

    private static ParticleAcceleratorStrengthProfile strength(GameTestHelper h) {
        var pa = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId());
        var particles = ModParticleAcceleratorParticles.catalog(h.getLevel()).get(ModParticleAcceleratorParticles.DEFAULT_ID);
        require(pa != null && particles != null, "resolved PA and particle profiles exist");
        var result = pa.strength(ParticleAcceleratorStrength.LEVEL_2);
        require(result != null && particles.energyReceiverVerticalPadding() == 1,
                "resolved normal PA strength and approved one-block receiver vertical padding");
        return result;
    }

    private static ParticleAcceleratorParticleEntity projectile(GameTestHelper h, Cleanup cleanup,
            ParticleAcceleratorStrengthProfile strength, Vec3 from, Vec3 motion) {
        var particle = cleanup.own(new ParticleAcceleratorParticleEntity(h.getLevel(), ModParticleAcceleratorParticles.DEFAULT_ID,
                strength, UUID.randomUUID()));
        particle.moveTo(from.x, from.y, from.z, 0, 0);
        particle.setDeltaMovement(motion);
        require(h.getLevel().addFreshEntity(particle), "native PA particle enters server world");
        return particle;
    }

    private static void captureAtVerticalOffset(GameTestHelper h, Cleanup cleanup, boolean tesla) {
        var strength = strength(h);
        var receiverPos = at(h, 5, 5, 3);
        Entity receiver;
        double before;
        if (tesla) {
            var ball = cleanup.own(ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel()));
            require(ball != null && ball.initializeSourceProfile(ModTeslaProfiles.MAIN_ID), "resolved normal Tesla profile initializes");
            ball.moveTo(receiverPos.getX()+.5, receiverPos.getY()+.5, receiverPos.getZ()+.5, 0, 0);
            require(h.getLevel().addFreshEntity(ball), "native Tesla energy ball enters server world");
            receiver = ball;
            before = ball.energy();
        } else {
            var singularity = cleanup.own(ModEntities.SINGULARITY.get().create(h.getLevel()));
            require(singularity != null, "registered singularity entity creates");
            singularity.moveTo(receiverPos.getX()+.5, receiverPos.getY()+.5, receiverPos.getZ()+.5, 0, 0);
            singularity.initializeFromGenerator();
            singularity.setDeltaMovement(Vec3.ZERO);
            require(h.getLevel().addFreshEntity(singularity), "native singularity enters server world");
            receiver = singularity;
            before = singularity.energy();
        }

        var origin = at(h, 2, 3, 3);
        double beamY = receiver.position().y - 1.5;
        require(Math.abs(receiver.position().y - beamY - 1.5) < .001,
                "receiver sweep fixture uses exact 1.5-block vertical center offset");
        var particle = projectile(h, cleanup, strength,
                new Vec3(origin.getX()+.5, beamY, origin.getZ()+.5), new Vec3(3, 0, 0));
        particle.enableCommissioningDiagnostics();
        particle.tick();
        require(particle.isRemoved(), "receiver sweep consumes the vertically offset particle");
        if (tesla) {
            var ball = (TeslaEnergyBallEntity) receiver;
            require(ball.lastEnergyInputKind() == TeslaEnergyBallEntity.EnergyInput.PA
                            && ball.lastEnergyInputAmount() == strength.singularityFood()
                            && ball.lastEnergyInputTick() == h.getLevel().getGameTime(),
                    "Tesla records one authoritative PA input using resolved food amount");
            require(Math.abs(ball.energy() - before - strength.singularityFood()) < .001,
                    "Tesla receives exactly one resolved PA food credit");
        } else {
            var singularity = (SingularityEntity) receiver;
            var receipt = particle.commissioningContactReceipt();
            require(receipt != null && receipt.outcome().equals("singularity-feed")
                            && receipt.target().equals(singularity.getUUID()),
                    "singularity records authoritative native feed receipt");
            require(Math.abs(receipt.sourceEnergyAfter() - receipt.sourceEnergyBefore() - strength.singularityFood()) < .001
                            && Math.abs(receipt.sourceEnergyAfter() - before - strength.singularityFood()) < .001,
                    "singularity receives exactly one resolved PA food credit");
        }
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void teslaCapturesNativeParticleAtOneAndHalfBlockVerticalOffset(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try { captureAtVerticalOffset(h, cleanup, true); h.succeed(); }
        finally { cleanup.clean(); }
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void singularityCapturesNativeParticleAtOneAndHalfBlockVerticalOffset(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try { captureAtVerticalOffset(h, cleanup, false); h.succeed(); }
        finally { cleanup.clean(); }
    }

    private static void capturesAfterBroadBoxOnlyOverlap(GameTestHelper h, Cleanup cleanup, boolean tesla) {
        var strength = ModParticleAccelerators.catalog(h.getLevel()).get(ModParticleAccelerators.TYPE.typeId())
                .strength(ParticleAcceleratorStrength.LEVEL_1);
        var pos = at(h, 5, 5, 3);
        Entity receiver;
        if (tesla) {
            var ball = cleanup.own(ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel()));
            require(ball != null && ball.initializeSourceProfile(ModTeslaProfiles.MAIN_ID), "resolved Tesla profile initializes");
            receiver = ball;
        } else {
            var singularity = cleanup.own(ModEntities.SINGULARITY.get().create(h.getLevel()));
            require(singularity != null, "singularity creates");
            singularity.initializeFromGenerator();
            receiver = singularity;
        }
        receiver.moveTo(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 0, 0);
        receiver.setDeltaMovement(Vec3.ZERO);
        require(h.getLevel().addFreshEntity(receiver), "stationary receiver enters world");
        double before = ((com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorEnergyReceiver)receiver).particleEnergy();
        var particle = projectile(h, cleanup, strength, receiver.position().add(1.9, 1.5, 0), new Vec3(-1, 0, 0));
        particle.tick();
        require(receiver.getBoundingBox().inflate(.48).intersects(particle.getBoundingBox())
                        && !particle.isRemoved() && ((com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorEnergyReceiver)receiver).particleEnergy() == before,
                "first step overlaps broad physical box only; no premature feed");
        particle.tick();
        require(particle.isRemoved(), "second step enters exact receiver ellipsoid and consumes particle");
        var target = (com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorEnergyReceiver)receiver;
        require(Math.abs(target.particleEnergy() - before - strength.singularityFood()) < .001,
                "later exact contact feeds exactly once");
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void teslaCaptureAfterBroadBoxOnlyOverlap(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try { capturesAfterBroadBoxOnlyOverlap(h, cleanup, true); h.succeed(); }
        finally { cleanup.clean(); }
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void singularityCaptureAfterBroadBoxOnlyOverlap(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try { capturesAfterBroadBoxOnlyOverlap(h, cleanup, false); h.succeed(); }
        finally { cleanup.clean(); }
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void receiverHorizontalNearMissRemainsUnfed(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try {
            var strength = strength(h);
            var ball = cleanup.own(ModEntities.TESLA_ENERGY_BALL.get().create(h.getLevel()));
            require(ball != null && ball.initializeSourceProfile(ModTeslaProfiles.MAIN_ID), "resolved normal Tesla profile initializes");
            var pos = at(h, 5, 3, 5);
            ball.moveTo(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 0, 0);
            require(h.getLevel().addFreshEntity(ball), "native Tesla enters world");
            double before = ball.energy();
            var origin = at(h, 2, 3, 3);
            var particle = projectile(h, cleanup, strength,
                    new Vec3(origin.getX()+.5, origin.getY()+.5, origin.getZ()+.5), new Vec3(3, 0, 0));
            particle.tick();
            require(!particle.isRemoved() && ball.energy() == before
                            && ball.lastEnergyInputKind() == TeslaEnergyBallEntity.EnergyInput.NONE,
                    "horizontal separation outside resolved receiver radius is not captured");
            h.succeed();
        } finally { cleanup.clean(); }
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=30)
    public static void verticalReceiverPaddingDoesNotExpandLivingMobHitboxes(GameTestHelper h) {
        Cleanup cleanup = new Cleanup(h);
        try {
            var strength = strength(h);
            var mobPos = at(h, 5, 5, 3);
            var zombie = cleanup.own(EntityType.ZOMBIE.create(h.getLevel()));
            require(zombie != null, "native living mob type creates");
            zombie.moveTo(mobPos.getX()+.5, mobPos.getY()+.5, mobPos.getZ()+.5, 0, 0);
            zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setDeltaMovement(Vec3.ZERO); zombie.setPersistenceRequired();
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                zombie.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            require(h.getLevel().addFreshEntity(zombie), "native living mob enters server world");
            var origin = at(h, 2, 3, 3);
            var particle = projectile(h, cleanup, strength,
                    new Vec3(origin.getX()+.5, origin.getY()+.5, origin.getZ()+.5), new Vec3(3, 0, 0));
            particle.tick();
            float radiation = DamageSystem.existingOrVanillaBaseline(zombie).getOrDefault(DamageKeys.RADIATION, 0f);
            require(Math.abs(radiation) < .001f && !particle.isRemoved(),
                    "receiver-only vertical padding leaves living mob collision/radiation unchanged");
            h.succeed();
        } finally { cleanup.clean(); }
    }
}
