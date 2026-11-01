package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.AnimalAppearanceComponent;

/** Resolved, presentation-only anchors. They never affect the physical collider. */
public final class AnimalPresentationAnchors {
    private AnimalPresentationAnchors() { }

    public static double eyeHeight(AnimalAppearanceComponent appearance, double fallback) {
        return appearance.eyeHeight() == null ? fallback : appearance.eyeHeight();
    }

    public static double speechHeight(AnimalAppearanceComponent appearance, double fallback) {
        return appearance.speechHeight() == null ? fallback : appearance.speechHeight();
    }
}
