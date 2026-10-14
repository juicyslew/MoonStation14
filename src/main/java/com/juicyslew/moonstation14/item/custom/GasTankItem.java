package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One physical portable tank item; contents/profile distinguish empty and filled prototypes. */
public final class GasTankItem extends Item {
    private final ResourceLocation emptyProfile;
    public GasTankItem(Properties properties) { this(properties, ModGasTanks.PLASMA_ID); }
    public GasTankItem(Properties properties, ResourceLocation emptyProfile) {
        super(properties.stacksTo(1));
        this.emptyProfile = java.util.Objects.requireNonNull(emptyProfile);
    }
    public ResourceLocation emptyProfile() { return emptyProfile; }
    @Override public ItemStack getDefaultInstance() {
        return createStack(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverGasTanks(), emptyProfile);
    }
    public static ItemStack createStack(Level level, ResourceLocation profile) { return createStack(ModGasTanks.catalog(level), profile); }
    public static ItemStack createStack(PrototypeCatalog<GasTankData> catalog, ResourceLocation profile) {
        GasTankContents contents = createContents(catalog, profile);
        ItemStack stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.PLASMA_TANK.get());
        stack.set(ModDataComponents.GAS_TANK_CONTENTS.get(), contents);
        return stack;
    }
    public static GasTankContents createContents(PrototypeCatalog<GasTankData> catalog, ResourceLocation profile) {
        GasTankData data = catalog.get(profile);
        if (data == null) throw new IllegalArgumentException("Missing gas tank prototype '" + profile + "'");
        return new GasTankContents(profile, data.initialMixture());
    }
    /** Read-only effective inventory: raw native stacks project the resolved empty profile without writes. */
    public static java.util.Optional<GasTankContents> contents(ItemStack stack) {
        return contents(stack, com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverGasTanks());
    }
    public static java.util.Optional<GasTankContents> contents(ItemStack stack, Level level) {
        if (level == null) return java.util.Optional.empty();
        return contents(stack, ModGasTanks.catalog(level));
    }
    public static java.util.Optional<GasTankContents> contents(ItemStack stack, PrototypeCatalog<GasTankData> catalog) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GasTankItem tank)) return java.util.Optional.empty();
        GasTankContents stored = stack.get(ModDataComponents.GAS_TANK_CONTENTS.get());
        if (stored != null) return isValidContents(stored, catalog) ? java.util.Optional.of(stored) : java.util.Optional.empty();
        return optionalProfile(catalog, tank.emptyProfile);
    }

    private static java.util.Optional<GasTankContents> optionalProfile(PrototypeCatalog<GasTankData> catalog, ResourceLocation profile) {
        if (catalog == null) return java.util.Optional.empty();
        GasTankData data = catalog.get(profile);
        if (data == null) return java.util.Optional.empty();
        return java.util.Optional.of(new GasTankContents(profile, data.initialMixture()));
    }

    private static boolean isValidContents(GasTankContents contents, PrototypeCatalog<GasTankData> catalog) {
        if (catalog == null) return false;
        GasTankData profile = catalog.get(contents.prototypeId());
        return profile != null && Math.abs(profile.volumeCubicMeters() - contents.mixture().volumeCubicMeters()) <= 1e-9;
    }
    /** Copies the stack before mutation so metadata and other stack snapshots remain untouched. */
    public static ItemStack withMixture(ItemStack source, GasMixture mixture) {
        GasTankContents current = contents(source).orElseThrow(() -> new IllegalArgumentException("Stack is not a valid gas tank"));
        ItemStack changed = source.copy();
        changed.set(ModDataComponents.GAS_TANK_CONTENTS.get(), current.withMixture(mixture));
        return changed;
    }
}

