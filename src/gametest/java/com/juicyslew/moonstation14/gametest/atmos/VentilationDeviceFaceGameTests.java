package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.FunctionalVentilationDevicePlacement;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"ventilation"})
@PrefixGameTestTemplate(false)
public final class VentilationDeviceFaceGameTests {
    private VentilationDeviceFaceGameTests() { }

@GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void ventAndScrubberResolveAllSixSurfaceOrientations(GameTestHelper helper) {
        var level = helper.getLevel();
        var catalog = PrototypeRuntime.serverVentilationDevices();
        for (var kind : VentilationDeviceConfig.Kind.values()) {
            var id = kind == VentilationDeviceConfig.Kind.VENT ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER;
            var definition = catalog.get(id);
            if (definition == null || !definition.fixtureGeometry().allowVerticalMounts())
                throw new AssertionError("resolved inherited mount capability missing: " + kind);
            var block = (kind == VentilationDeviceConfig.Kind.VENT ? ModBlocks.GAS_VENT : ModBlocks.GAS_SCRUBBER).get();
            for (Direction face : Direction.values()) {
                if (!FunctionalVentilationDevicePlacement.allowsMount(definition.fixtureGeometry(), face))
                    throw new AssertionError("resolved prototype denied face " + face);
                var state = block.defaultBlockState().setValue(VentilationDeviceBlock.FACING, face);
                var pos = helper.absolutePos(new BlockPos(2 + face.get3DDataValue() % 3, 2, 2 + face.get3DDataValue() / 3));
                level.setBlock(pos, state, 3);
                var shape = state.getShape(level, pos, CollisionContext.empty()).bounds();
                double length = face.getAxis() == Direction.Axis.X ? shape.getXsize()
                        : face.getAxis() == Direction.Axis.Y ? shape.getYsize() : shape.getZsize();
                if (Math.abs(length - definition.fixtureGeometry().depth()) > 1e-6
                        || !(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity))
                    throw new AssertionError("incorrect face fixture/typed entity: " + kind + " " + face);
            }
        }
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_REGRESSION_PASS ventAndScrubberResolveAllSixSurfaceOrientations");
    }
}
