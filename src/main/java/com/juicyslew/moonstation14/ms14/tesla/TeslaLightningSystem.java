package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Server-only Tesla target discovery and bolt admission. Visual arc delivery is a separate presentation concern. */
public final class TeslaLightningSystem {
    public record Target(LivingEntity living, LightningConductor conductor, BlockEntity blockEntity, LightningTargetComponent data, Vec3 position) { }
    private TeslaLightningSystem() { }

    /** Emits the shared real presentation and transient contact hazard for live strikes and controlled fixtures. */
    public static void emitLiveBeam(ServerLevel level, Vec3 from, Vec3 to, TeslaBallProfile.Contact contact, java.util.UUID initial) {
        if (level == null || from == null || to == null || contact == null) return;
        TeslaLightningEffects.emit(level, from, to, contact.sourceLifetimeSeconds(), contact.beamHalfWidth());
        TeslaLightningBeamHazard.create(level, from, to, contact, initial);
    }

    /** Enumerates composed character targets and explicitly profiled machine block entities without loading chunks. */
    public static List<Target> targets(ServerLevel level, Vec3 center, double radius) {
        if (level == null || center == null || !Double.isFinite(radius) || radius <= 0) return List.of();
        AABB bounds = new AABB(center, center).inflate(radius);
        List<Target> out = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, e -> e.isAlive() && e.isAddedToLevel() && !e.isSpectator())) {
            Target resolved = resolveLivingTarget(level, entity);
            if (resolved != null) out.add(resolved);
        }
        int minX = (int)Math.floor(bounds.minX) >> 4, maxX = (int)Math.floor(bounds.maxX) >> 4;
        int minZ = (int)Math.floor(bounds.minZ) >> 4, maxZ = (int)Math.floor(bounds.maxZ) >> 4;
        for (int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            var chunk = level.getChunkSource().getChunkNow(x,z);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values()) {
                var pos=be.getBlockPos();
                if (!bounds.contains(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)) continue;
                Target resolved = resolveBlockTarget(level, be);
                if (resolved != null && bounds.contains(resolved.position())) out.add(resolved);
            }
        }
        return out;
    }

    /** Resolves current composed character eligibility without changing its target profile. */
    public static Target resolveLivingTarget(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null || !entity.isAlive() || !entity.isAddedToLevel() || entity.isSpectator()) return null;
        if (entity instanceof net.minecraft.server.level.ServerPlayer player && ActiveCharacterPolicy.isCarrier(player)) return null;
        var composed = CharacterIdentitySystem.resolveForActor(entity, com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(level));
        if (composed.isEmpty()) return null;
        var target = composed.get().component(LightningTargetComponent.class).orElse(null);
        return target == null ? null : new Target(entity, null, null, target, entity.position());
    }

    /** Resolves an already-loaded block entity using the same eligibility rules as target discovery. */
    public static Target resolveBlockTarget(ServerLevel level, BlockEntity be) {
        if (level == null || be == null || be.isRemoved()) return null;
        var pos = be.getBlockPos();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        Vec3 point = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var profiles = ModTeslaProfiles.machines(level);
        var state = level.getBlockState(pos);
        var blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        var profileId = ModTeslaProfiles.profileForBlock(blockId);
        if (profileId == null) return null;
        var profile = profiles.get(profileId);
        if (profile == null || profile.target() == null) return null;
        if (be instanceof LightningConductor conductor)
            return new Target(null, conductor, be, profile.target(), point);
        return new Target(null, null, be, profile.target(), point);
    }

    /** Emits the authored number of initial bolts and finite resistance-decreasing recursive bolts. */
    public static int shoot(ServerLevel level, Vec3 source, TeslaBallProfile.Shooter shooter,
                            TeslaBallProfile.Contact contact, Random random) {
        if(level==null||source==null||shooter==null||contact==null||random==null) return 0;
        int initial=TeslaLightningSelection.initialBoltCount(shooter.maxBolts(),random); int fired=0;
        var candidates=targetCandidates(level,source,shooter).stream().toList();
        for(var selected:TeslaLightningSelection.chooseMany(candidates,initial,random))
            fired+=apply(level,source,selected.value(),shooter,contact,shooter.depth(),random);
        return fired;
    }

    /** Maps authored target explosion values onto the shared blast planner; non-exploding targets have no proposal. */
    public static com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner.Proposal explosionProposal(LightningTargetComponent target) {
        if (target == null || !target.explodes()) return null;
        return new com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner.Proposal(
                target.intensity(), target.dropoff(), target.maximumPerTile());
    }

    private static List<TeslaLightningSelection.Candidate<Target>> targetCandidates(ServerLevel level,Vec3 source,TeslaBallProfile.Shooter shooter) {
        return targets(level,source,shooter.range()).stream()
                .filter(t -> t.position().distanceToSqr(source) <= shooter.range()*shooter.range())
                .map(t -> new TeslaLightningSelection.Candidate<>(t,t.data().priority(),t.data().probability(),t.data().resistance())).toList();
    }

    private static int shootOne(ServerLevel level, Vec3 source, TeslaBallProfile.Shooter shooter,
                                TeslaBallProfile.Contact contact, int depth, Random random) {
        if (depth <= 0) return 0;
        List<TeslaLightningSelection.Candidate<Target>> candidates = targetCandidates(level,source,shooter);
        var selected=TeslaLightningSelection.choose(candidates,random);
        if(selected==null) return 0;
        return apply(level,source,selected.value(),shooter,contact,depth,random);
    }

    private static int apply(ServerLevel level,Vec3 source,Target target,TeslaBallProfile.Shooter shooter,TeslaBallProfile.Contact contact,int depth,Random random) {
        Vec3 beamStart = beamStart(source, target.position());
        boolean hasBeam = hasPhysicalSegment(source, target.position());
        if (hasBeam) emitLiveBeam(level,beamStart,target.position(),contact,
                target.living() == null ? null : target.living().getUUID());
        if(target.conductor()!=null) target.conductor().receiveLightningStrike();
        else if(target.living()!=null && hasBeam) com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.applySourceShock(
                target.living(),(float)contact.shockDamage(),(float)contact.siemens(),(float)contact.stunSeconds());
        else if(target.blockEntity()!=null) applyBlockTargetResponse(level,target);
        if(TeslaLightningSelection.chains(depth,target.data().resistance()))
            return (hasBeam ? 1 : 0)+shootOne(level,target.position(),shooter,contact,(int)Math.floor(depth-target.data().resistance()),random);
        return hasBeam ? 1 : 0;
    }

    /** Shared live strike seam; structural damage consumes the same resolved counterpart profile as eligibility. */
    private static int applyBlockStructuralStrike(ServerLevel level, Target target) {
        if (level == null || target == null || target.blockEntity() == null || target.data() == null) return 0;
        var pos = target.blockEntity().getBlockPos();
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        var profileId = ModTeslaProfiles.profileForBlock(block);
        var profile = profileId == null ? null : ModTeslaProfiles.machines(level).get(profileId);
        return profile == null ? 0 : MachineryStructuralDamage.apply(target.blockEntity(), profile, target.data().structuralDamage());
    }

    /** Applies direct structural hit before its independent explosion proposal, retaining the original target identity/position. */
    public static int applyBlockTargetResponse(ServerLevel level, Target target) {
        if (level == null || target == null || target.blockEntity() == null) return 0;
        int damage = applyBlockStructuralStrike(level, target);
        applyGenericBlockResponse(level, target);
        return damage;
    }

    /** SS14's beam creator rejects coincident endpoints; preserve selected-target response without inventing a hit. */
    static boolean hasPhysicalSegment(Vec3 source, Vec3 target) {
        return source != null && target != null && source.distanceToSqr(target) > 0;
    }

    /** Robust beam source is offset one unit toward its target; short beams never reverse. */
    static Vec3 beamStart(Vec3 source, Vec3 target) {
        Vec3 delta = target.subtract(source);
        double length = delta.length();
        return length <= 1 ? target : source.add(delta.scale(1 / length));
    }

    private static void applyGenericBlockResponse(ServerLevel level,Target target) {
        var proposal=explosionProposal(target.data());
        if(proposal==null) return;
        var pos=target.blockEntity().getBlockPos();
        var origin=adjacentLoadedAir(level,pos);
        if(origin==null) return;
        var explosionId=com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions.DEFAULT_ID;
        com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService.execute(level,origin,explosionId,proposal);
    }

    /** Explosion planner requires an open origin; consider only adjacent already-loaded air cells. */
    private static net.minecraft.core.BlockPos adjacentLoadedAir(ServerLevel level,net.minecraft.core.BlockPos target) {
        int[][] faces={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for(int[] face:faces) {
            var pos=target.offset(face[0],face[1],face[2]);
            if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()) continue;
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) continue;
            if(level.getBlockState(pos).isAir()) return pos.immutable();
        }
        return null;
    }
}
