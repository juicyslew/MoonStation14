package com.juicyslew.moonstation14.block;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.AtmosphereTestDeviceBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.block.custom.MagicBlock;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MoonStation14.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PuddleBlockEntity>> PUDDLE = BLOCK_ENTITY_TYPES.register("puddle",
            () -> BlockEntityType.Builder.of(PuddleBlockEntity::new, ModBlocks.PUDDLE.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphereTestDeviceBlockEntity>> ATMOSPHERE_TEST_DEVICE = BLOCK_ENTITY_TYPES.register("atmosphere_test_device",
            () -> BlockEntityType.Builder.of(AtmosphereTestDeviceBlockEntity::new,
                    ModBlocks.ATMOS_AIR_PRODUCER.get(), ModBlocks.ATMOS_OXYGEN_PRODUCER.get(), ModBlocks.ATMOS_NITROGEN_PRODUCER.get(),
                    ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER.get(), ModBlocks.ATMOS_PLASMA_PRODUCER.get(), ModBlocks.ATMOS_TRITIUM_PRODUCER.get(),
                    ModBlocks.ATMOS_WATER_VAPOR_PRODUCER.get(), ModBlocks.ATMOS_AMMONIA_PRODUCER.get(), ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER.get(),
                    ModBlocks.ATMOS_FREZON_PRODUCER.get(), ModBlocks.ATMOS_GAS_SINK.get(), ModBlocks.ATMOS_HEATER.get(), ModBlocks.ATMOS_COOLER.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerDeviceBlockEntity>> POWER_DEVICE = BLOCK_ENTITY_TYPES.register("power_device",
            () -> BlockEntityType.Builder.of(PowerDeviceBlockEntity::new,
                    ModBlocks.HV_SOURCE.get(), ModBlocks.HV_MV_SUBSTATION.get(), ModBlocks.APC.get(),
                    ModBlocks.POWER_LAMP.get(), ModBlocks.HIGH_LOAD_TEST_LAMP.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VisualPipeBlockEntity>> VISUAL_PIPE = BLOCK_ENTITY_TYPES.register("visual_pipe",
            () -> BlockEntityType.Builder.of(VisualPipeBlockEntity::new, ModBlocks.GAS_PIPE_VISUAL.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VentilationDeviceBlockEntity>> VENTILATION_DEVICE = BLOCK_ENTITY_TYPES.register("ventilation_device",
            () -> BlockEntityType.Builder.of(VentilationDeviceBlockEntity::new,
                    ModBlocks.GAS_VENT.get(), ModBlocks.GAS_SCRUBBER.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirAlarmBlockEntity>> AIR_ALARM = BLOCK_ENTITY_TYPES.register("air_alarm",
            () -> BlockEntityType.Builder.of(AirAlarmBlockEntity::new, ModBlocks.AIR_ALARM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisposalUnitBlockEntity>> DISPOSAL_UNIT = BLOCK_ENTITY_TYPES.register("disposal_unit",
            () -> BlockEntityType.Builder.of(DisposalUnitBlockEntity::new, ModBlocks.DISPOSAL_UNIT.get()).build(null));

    public static void register(IEventBus eventBus){
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
