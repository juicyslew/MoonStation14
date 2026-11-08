package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereSampleFormatter;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSample;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.ModGasAnalyzers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Function;

/** A handheld, read-only probe of the atmosphere cell at a target position. */
public class AtmosphereAnalyzerItem extends Item {
    private static final String UNAVAILABLE_MESSAGE = "Atmosphere sample unavailable.";

    public AtmosphereAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        // The carrier inventory is not body-hand authority. All active analyzer use is
        // dispatched through BodyHandRequestService and its authenticated held token.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Selects a probe cell from the represented body's eye position, not its carrier's camera. */
    public static BlockPos eyeTarget(net.minecraft.world.phys.Vec3 eyePosition) {
        return BlockPos.containing(eyePosition);
    }

    /** Pure target policy seam: reads clicked first, then at most its clicked-face neighbor. */
    public static <T> TargetSample<T> sampleTarget(boolean enabled, BlockPos clicked, Direction face,
                                                   Function<BlockPos, Optional<T>> sampler) {
        if (!enabled) return new TargetSample<>(TargetStatus.DISABLED, Optional.empty());

        Optional<T> result = sampler.apply(clicked);
        if (result.isPresent() || face == null) {
            return new TargetSample<>(result.isPresent() ? TargetStatus.SAMPLED : TargetStatus.UNAVAILABLE, result);
        }

        result = sampler.apply(clicked.relative(face));
        return new TargetSample<>(result.isPresent() ? TargetStatus.SAMPLED : TargetStatus.UNAVAILABLE, result);
    }

    public enum TargetStatus { SAMPLED, DISABLED, UNAVAILABLE }

    public record TargetSample<T>(TargetStatus status, Optional<T> sample) { }
}

