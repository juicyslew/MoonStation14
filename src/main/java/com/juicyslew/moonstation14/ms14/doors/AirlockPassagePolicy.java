package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared resolved-prototype passage decision. Unknown actors always retain vanilla collision. */
public final class AirlockPassagePolicy {
    private AirlockPassagePolicy() { }
    /** Actor-specific physical block shape; ray/outline queries intentionally keep BlockState semantics. */
    public static VoxelShape physicalShape(Entity actor, BlockState state, BlockGetter getter,
                                           BlockPos pos, CollisionContext context) {
        if (state.getBlock() instanceof AirlockBlock && actor != null && !collides(actor, pos))
            return Shapes.empty();
        return state.getCollisionShape(getter, pos, context);
    }

    public static boolean collides(Entity actor, BlockPos pos) {
        if (actor == null || pos == null || !(actor instanceof LivingEntity living)) return true;
        var data = CharacterIdentitySystem.resolveForActor(living).orElse(null);
        CollisionProfileComponent profile = data == null ? null : data.component(CollisionProfileComponent.class).orElse(null);
        if (profile == null) return true;
        Level level = actor.level();
        if (level == null) return true;
        if (!(level.getBlockState(pos).getBlock() instanceof AirlockBlock)) return true;
        BlockPos root = level.getBlockState(pos).getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door)) return true;
        var dataDoor = door.prototype();
        if (dataDoor == null) return true;
        return pairCollides(profile, dataDoor.collision());
    }
    public static boolean pairCollides(Entity actor, AirlockBlockEntity door) {
        if (!(actor instanceof LivingEntity living) || door == null || door.prototype() == null) return true;
        var profile = CharacterIdentitySystem.resolveForActor(living)
                .flatMap(data -> data.component(CollisionProfileComponent.class)).orElse(null);
        return profile == null || pairCollides(profile, door.prototype().collision());
    }
    private static boolean pairCollides(CollisionProfileComponent actor, com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData.Collision object) {
        return actor.collidesWith(new CollisionProfileComponent(object.mask(), object.layer()));
    }
}
