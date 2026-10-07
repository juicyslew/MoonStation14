package com.juicyslew.moonstation14.block;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenu;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenu;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenu;
import com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Menu registrations shared by the server and the future client screen. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MoonStation14.MOD_ID);
    public static final Supplier<MenuType<ApcMenu>> APC = MENUS.register("apc",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ApcMenu::new));
    public static final Supplier<MenuType<AirAlarmMenu>> AIR_ALARM = MENUS.register("air_alarm",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AirAlarmMenu::new));
    public static final Supplier<MenuType<DisposalBuildMenu>> DISPOSAL_BUILD = MENUS.register("disposal_build",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(DisposalBuildMenu::new));
    public static final Supplier<MenuType<DisposalUnitMenu>> DISPOSAL_UNIT = MENUS.register("disposal_unit",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(DisposalUnitMenu::new));

    private ModMenus() { }
}
