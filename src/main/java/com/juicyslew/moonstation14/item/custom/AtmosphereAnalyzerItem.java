package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereSampleFormatter;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Function;

/** A handheld, read-only probe of the atmosphere cell at a target position. */
public class AtmosphereAnalyzerItem extends Item {
    private static final String DISABLED_MESSAGE = "Atmosphere system is disabled.";
    private static final String UNAVAILABLE_MESSAGE = "Atmosphere sample unavailable.";

    public AtmosphereAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (level instanceof ServerLevel serverLevel) {
            BlockPos target = BlockPos.containing(player.getEyePosition());
            report(serverLevel, player, target, null);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (level instanceof ServerLevel serverLevel && context.getPlayer() != null) {
            report(serverLevel, context.getPlayer(), context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.sidedSuccess(false);
    }

    private static void report(ServerLevel level, Player player, BlockPos clicked, Direction face) {
        if (!AtmosphereService.INSTANCE.isEnabled()) {
            player.sendSystemMessage(Component.literal(DISABLED_MESSAGE));
            return;
        }

        TargetSample result = sampleTarget(true, clicked, face, pos -> AtmosphereService.INSTANCE.sample(level, pos));
        if (result.mixture().isPresent()) {
            player.sendSystemMessage(Component.literal(AtmosphereSampleFormatter.format(result.mixture().get())));
        } else {
            player.sendSystemMessage(Component.literal(UNAVAILABLE_MESSAGE));
        }
    }

    /** Pure target policy seam: samples clicked first, then at most its clicked-face neighbor. */
    public static TargetSample sampleTarget(boolean enabled, BlockPos clicked, Direction face,
                                            Function<BlockPos, Optional<GasMixture>> sampler) {
        if (!enabled) return new TargetSample(TargetStatus.DISABLED, Optional.empty());

        Optional<GasMixture> result = sampler.apply(clicked);
        if (result.isPresent() || face == null) {
            return new TargetSample(result.isPresent() ? TargetStatus.SAMPLED : TargetStatus.UNAVAILABLE, result);
        }

        result = sampler.apply(clicked.relative(face));
        return new TargetSample(result.isPresent() ? TargetStatus.SAMPLED : TargetStatus.UNAVAILABLE, result);
    }

    public enum TargetStatus { SAMPLED, DISABLED, UNAVAILABLE }

    public record TargetSample(TargetStatus status, Optional<GasMixture> mixture) { }
}
