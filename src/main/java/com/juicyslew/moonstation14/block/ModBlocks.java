package com.juicyslew.moonstation14.block;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.custom.JugBlock;
import com.juicyslew.moonstation14.block.custom.MagicBlock;
import com.juicyslew.moonstation14.block.custom.PuddleBlock;
import com.juicyslew.moonstation14.block.custom.AtmosphereTestDeviceBlock;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereDeviceRules;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MoonStation14.MOD_ID);

    public static final DeferredBlock<Block> STEEL_WALL_BLOCK = registerBlock("steel_wall_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER_GRATE)));

    public static final DeferredBlock<StationFloorBlock> STATION_FLOOR = registerBlock("station_floor",
            () -> new StationFloorBlock(BlockBehaviour.Properties.of()
                    .strength(3f).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredBlock<Block> STEEL_WALL_GIRDER_BLOCK = registerBlock("steel_wall_girder_block",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion()
                    .isViewBlocking(ModBlocks::never)
                    .strength(2f).requiresCorrectToolForDrops().sound(SoundType.COPPER_GRATE)));

    public static final DeferredBlock<Block> MAGIC_BLOCK = registerBlock("magic_block",
            () -> new MagicBlock(BlockBehaviour.Properties.of()
                    .strength(2f).requiresCorrectToolForDrops()));


    // Custom Register (No Item, or Item needs to be custom-made)
    public static final DeferredBlock<JugBlock> JUG = BLOCKS.register("jug",
            () -> new JugBlock(Block.Properties.ofFullCopy(Blocks.GLASS)));

    public static final DeferredBlock<PuddleBlock> PUDDLE = BLOCKS.register("puddle",
            () -> new PuddleBlock(BlockBehaviour.Properties.of()
                    .destroyTime(72000f).noCollission().noLootTable()));

    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_AIR_PRODUCER = registerBlock("atmos_air_producer",
            () -> new AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device.PRODUCER, BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_OXYGEN_PRODUCER = registerGasProducer(GasType.OXYGEN);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_NITROGEN_PRODUCER = registerGasProducer(GasType.NITROGEN);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_CARBON_DIOXIDE_PRODUCER = registerGasProducer(GasType.CARBON_DIOXIDE);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_PLASMA_PRODUCER = registerGasProducer(GasType.PLASMA);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_TRITIUM_PRODUCER = registerGasProducer(GasType.TRITIUM);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_WATER_VAPOR_PRODUCER = registerGasProducer(GasType.WATER_VAPOR);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_AMMONIA_PRODUCER = registerGasProducer(GasType.AMMONIA);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_NITROUS_OXIDE_PRODUCER = registerGasProducer(GasType.NITROUS_OXIDE);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_FREZON_PRODUCER = registerGasProducer(GasType.FREZON);
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_GAS_SINK = registerBlock("atmos_gas_sink",
            () -> new AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device.SINK, BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_HEATER = registerBlock("atmos_heater",
            () -> new AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device.HEATER, BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));
    public static final DeferredBlock<AtmosphereTestDeviceBlock> ATMOS_COOLER = registerBlock("atmos_cooler",
            () -> new AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device.COOLER, BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));

    public static final DeferredBlock<PowerDeviceBlock> HV_SOURCE = registerPowerDevice(PowerDeviceKind.HV_SOURCE);
    public static final DeferredBlock<PowerDeviceBlock> HV_MV_SUBSTATION = registerPowerDevice(PowerDeviceKind.HV_MV_SUBSTATION);
    public static final DeferredBlock<PowerDeviceBlock> APC = registerPowerDevice(PowerDeviceKind.APC);
    public static final DeferredBlock<PowerDeviceBlock> POWER_LAMP = registerPowerDevice(PowerDeviceKind.LAMP);

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static DeferredBlock<AtmosphereTestDeviceBlock> registerGasProducer(GasType gas) {
        return registerBlock("atmos_" + gas.id() + "_producer", () -> new AtmosphereTestDeviceBlock(
                AtmosphereDeviceRules.Device.PRODUCER, gas, BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));
    }

    private static DeferredBlock<PowerDeviceBlock> registerPowerDevice(PowerDeviceKind kind) {
        return registerBlock(kind.id(), () -> new PowerDeviceBlock(kind,
                BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER).noOcclusion()
                        .lightLevel(state -> kind == PowerDeviceKind.LAMP && state.getValue(PowerDeviceBlock.LIT) ? 14 : 0)));
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus){
        BLOCKS.register(eventBus);
    }

    private static boolean always(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return true;
    }

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return false;
    }
}
