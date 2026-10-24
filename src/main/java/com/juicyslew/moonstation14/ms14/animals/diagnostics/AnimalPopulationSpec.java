package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import java.util.List;

/** Bounded dimensions and registered animal population for an owned fixture. */
record AnimalPopulationSpec(int width, int height, List<String> species, int count, int cellSpacing) {
    static final int HEIGHT = 4;
    static final AnimalPopulationSpec KOBOLD_PIT = new AnimalPopulationSpec(8, HEIGHT, List.of("kobold"), 100, 1);
    static AnimalPopulationSpec showcase() {
        var ids = new java.util.ArrayList<>(com.juicyslew.moonstation14.ms14.animals.AnimalCatalog.entries().stream().map(e -> e.id()).toList());
        ids.add("mouse"); // Legacy typed MouseMob is already registered separately from the custom-animal catalog.
        return new AnimalPopulationSpec(24, HEIGHT, ids, ids.size(), 3);
    }
    AnimalPopulationSpec(int width,int height,List<String> species,int count) { this(width,height,species,count,1); }
    static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.juicyslew.moonstation14.entities.AnimalMob>> registeredFactory(String species) {
        if (species.equals("mouse")) return null;
        var entry = com.juicyslew.moonstation14.ms14.animals.AnimalCatalog.entries().stream()
                .filter(candidate -> candidate.id().equals(species)).findFirst().orElse(null);
        return entry == null ? null : com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get(entry.modelId());
    }
    static net.minecraft.world.entity.EntityType<?> showcaseType(String species) {
        if (species.equals("mouse")) return com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get();
        var factory = registeredFactory(species);
        return factory == null ? null : factory.get();
    }
    private static boolean hasRegisteredFactory(String species) {
        if (species.equals("mouse")) return com.juicyslew.moonstation14.entities.ModEntities.MOUSE != null;
        return registeredFactory(species) != null;
    }
    static AnimalPopulationSpec automated(AnimalPopulationDiagnostic.Scenario scenario) {
        return switch (scenario) {
            case MIXED -> new AnimalPopulationSpec(16, HEIGHT, List.of("bat", "kobold", "parrot", "hamster", "cow"), 40,1);
            case PARROTS -> new AnimalPopulationSpec(16, HEIGHT, List.of("parrot"), 100,1);
            case KOBOLDS -> new AnimalPopulationSpec(16, HEIGHT, List.of("kobold"), 100,1);
        };
    }

    AnimalPopulationSpec {
        species = List.copyOf(species);
        if (width < 6 || width > 24 || height != HEIGHT || count < 1 || count > 200 || species.isEmpty() || cellSpacing < 1 || cellSpacing > 3)
            throw new IllegalArgumentException("invalid bounded animal fixture spec");
        if (species.stream().anyMatch(s -> !hasRegisteredFactory(s)))
            throw new IllegalArgumentException("unsupported registered fixture species");
    }

    String speciesAt(int index) { return species.get(index % species.size()); }
}
