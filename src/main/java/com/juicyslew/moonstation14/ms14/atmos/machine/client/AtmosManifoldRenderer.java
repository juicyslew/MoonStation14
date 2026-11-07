package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosManifoldAppearance;
import com.juicyslew.moonstation14.ms14.doors.client.AirlockBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class AtmosManifoldRenderer implements BlockEntityRenderer<AtmosMachineBlockEntity> {
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");
    public AtmosManifoldRenderer(BlockEntityRendererProvider.Context context) { }

    public static List<ModelResourceLocation> additionalModels() {
        ArrayList<ModelResourceLocation> result = new ArrayList<>();
        for (String facing : FACINGS) for (int bit = 0; bit < AtmosManifoldAppearance.endpointCount(); bit++)
            result.add(model(facing + "_" + AtmosManifoldAppearance.endpointSuffix(bit)));
        return List.copyOf(result);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
                "block/atmos_manifold/" + name));
    }

    /** Ordered by the six appearance bits: north primary/secondary/tertiary, then south. */
    public static List<ModelResourceLocation> connectedModels(int mask, Direction facing) {
        ArrayList<ModelResourceLocation> result = new ArrayList<>();
        String facingName = facing.getName();
        for (String path : AtmosManifoldAppearance.connectedModelPaths(mask, facingName))
            result.add(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path)));
        return List.copyOf(result);
    }

    @Override public AABB getRenderBoundingBox(AtmosMachineBlockEntity entity) {
        return new AABB(entity.getBlockPos());
    }

    @Override public void render(AtmosMachineBlockEntity entity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        AtmosMachineProfile profile = entity.resolvedProfile();
        if (profile == null || profile.kind() != AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD) return;
        for (ModelResourceLocation location : connectedModels(entity.manifoldAppearanceMask(),
                entity.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING))) {
            var baked = Minecraft.getInstance().getModelManager().getModel(location);
            AirlockBlockEntityRenderer.drawComponent(baked, pose, buffers, packedLight, packedOverlay, 0, 0, false, false);
        }
    }
}
