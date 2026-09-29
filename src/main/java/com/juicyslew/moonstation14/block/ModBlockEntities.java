package com.juicyslew.moonstation14.block;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.block_entity.JugBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.AtmosphereTestDeviceBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JugBlockEntity>> JUG = BLOCK_ENTITY_TYPES.register("jug",
            () -> BlockEntityType.Builder.of(JugBlockEntity::new, ModBlocks.JUG.get()).build(null)
    );
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

    public static void register(IEventBus eventBus){
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
