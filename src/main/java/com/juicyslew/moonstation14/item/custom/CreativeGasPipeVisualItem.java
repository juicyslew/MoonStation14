package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;

/** Creative-only visual fixture; deliberately has no gas-network behavior. */
public class CreativeGasPipeVisualItem extends BlockItem {
    private final int lane;

    public CreativeGasPipeVisualItem(Block block, Properties properties, int lane) {
        super(checkedBlock(block, lane), properties);
        this.lane = lane;
    }

    private static Block checkedBlock(Block block, int lane) {
        if (lane != VisualPipeBlockEntity.PRIMARY && lane != VisualPipeBlockEntity.SECONDARY
                && lane != VisualPipeBlockEntity.TERTIARY) throw new IllegalArgumentException("Invalid visual pipe lane");
        return block;
    }

    public int getLane() { return lane; }

    @Override
    public String getDescriptionId() {
        return lane == VisualPipeBlockEntity.PRIMARY ? super.getDescriptionId()
                : "item.moonstation14.gas_pipe_visual_" + (lane == VisualPipeBlockEntity.SECONDARY ? "secondary" : "tertiary");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.moonstation14.gas_pipe_visual.tooltip.visual_only"));
        tooltip.add(Component.translatable("item.moonstation14.gas_pipe_visual.tooltip.lane_" + lane));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            boolean extend = context.isSecondaryUseActive()
                    && context.getLevel().getBlockState(context.getClickedPos()).is(getBlock());
            // Suppress vanilla's predicted adjacent placement; the server decides whether extension succeeds.
            return extend ? InteractionResult.SUCCESS : super.useOn(context);
        }
        var level = context.getLevel();
        var clicked = context.getClickedPos();
        if (!level.hasChunkAt(clicked)) return InteractionResult.FAIL;
        boolean clickedPipe = level.getBlockState(clicked).is(getBlock());
        boolean extend = context.isSecondaryUseActive() && clickedPipe;
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer) || !mayUse(serverPlayer, context, !extend))
            return InteractionResult.FAIL;
        if (!extend) {
            var placementContext = new BlockPlaceContext(context);
            var target = clicked.relative(context.getClickedFace());
            if (!level.hasChunkAt(target)) return InteractionResult.FAIL;
            var before = level.getBlockState(target);
            if (!placementContext.getClickedPos().equals(target) || !before.isAir()
                    || !before.canBeReplaced(placementContext)) return InteractionResult.FAIL;
            InteractionResult result = super.useOn(context);
            if (!result.consumesAction()) return result;
            if (!level.hasChunkAt(target) || !level.getBlockState(target).is(getBlock())
                    || !(level.getBlockEntity(target) instanceof VisualPipeBlockEntity placed)) {
                if (before.isAir() && level.hasChunkAt(target) && level.getBlockState(target).is(getBlock())
                        && level.getBlockEntity(target) instanceof VisualPipeBlockEntity) level.removeBlock(target, false);
                return InteractionResult.FAIL;
            }
            if (lane == VisualPipeBlockEntity.PRIMARY) return result;
            if (!placed.setLayersForPlacement(lane)) {
                if (level.hasChunkAt(target) && level.getBlockState(target).is(getBlock())
                        && level.getBlockEntity(target) == placed) level.removeBlock(target, false);
                return InteractionResult.FAIL;
            }
            return result;
        }

        if (!(level.getBlockEntity(clicked) instanceof VisualPipeBlockEntity pipe))
            return InteractionResult.FAIL;
        if (pipe.hasLayer(lane)) {
            player.displayClientMessage(Component.literal("This lane is already present"), true);
            return InteractionResult.FAIL;
        }
        if (!pipe.addLayer(lane)) return InteractionResult.FAIL;
        player.displayClientMessage(Component.literal("Visual pipe lane added"), true);
        return InteractionResult.SUCCESS;
    }

    private boolean mayUse(ServerPlayer player, UseOnContext context, boolean placement) {
        if (player instanceof FakePlayer || player.isRemoved() || !player.isAlive() || player.isSpectator()
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE || !player.server.isSameThread()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || context.getPlayer() != player || context.getItemInHand().isEmpty()
                || context.getItemInHand().getItem() != this || context.getLevel() != player.serverLevel()
                || player.getItemInHand(context.getHand()) != context.getItemInHand()
                || LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent()
                || GhostMobHarnessControl.activeHarness(player).isPresent()) return false;
        boolean marker = player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        boolean parked = player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != null;
        if (marker || parked || !CharacterControlSystem.canAct(player)) return false;
        var level = player.serverLevel();
        var clicked = context.getClickedPos();
        if (!level.hasChunkAt(clicked) || !level.mayInteract(player, clicked)
                || !player.mayUseItemAt(clicked, context.getClickedFace(), context.getItemInHand())) return false;
        var distanceSquared = Math.pow(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 2);
        if (!placement) return player.distanceToSqr(clicked.getCenter()) <= distanceSquared;
        var target = clicked.relative(context.getClickedFace());
        return level.hasChunkAt(target)
                && player.distanceToSqr(target.getCenter()) <= distanceSquared
                && level.mayInteract(player, target)
                && player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand());
    }
}

