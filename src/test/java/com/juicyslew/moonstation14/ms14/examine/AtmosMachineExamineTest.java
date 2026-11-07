package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Focused translation/collector contract for authored atmos-machine examination text. */
class AtmosMachineExamineTest {
    @Test
    void receiverPowerProjectionDistinguishesDemandDeliveryAndUnknown() {
        assertKey("examine.moonstation14.power.on", AtmosMachinePowerExamine.receiver(5000d, true, 5000));
        assertKey("examine.moonstation14.power.off", AtmosMachinePowerExamine.receiver(200d, true, 0));
        assertKey("examine.moonstation14.power.insufficient", AtmosMachinePowerExamine.receiver(200d, true, 100));
        assertKey("examine.moonstation14.power.unavailable", AtmosMachinePowerExamine.receiver(null, false, 0));
        assertKey("examine.moonstation14.power.unavailable", AtmosMachinePowerExamine.receiver(Double.NaN, true, 100));
        assertKey("examine.moonstation14.power.standby", AtmosMachinePowerExamine.receiver(0d, false, 0));
    }

    @Test
    void tegOutputAndModeledCapacityRemainDistinctAndUnknownHasNoNumericWattValue() {
        var actual = AtmosMachinePowerExamine.tegDelivered(true, 1234.567);
        var capacity = AtmosMachinePowerExamine.tegCapacity(5000);
        assertKey("examine.moonstation14.atmos.teg-delivered", actual);
        assertEquals("1.2 kW", ((net.minecraft.network.chat.contents.TranslatableContents) actual.getContents()).getArgs()[0]);
        assertKey("examine.moonstation14.atmos.teg-capacity", capacity);
        assertEquals("5.0 kW", ((net.minecraft.network.chat.contents.TranslatableContents) capacity.getContents()).getArgs()[0]);
        var unknown = AtmosMachinePowerExamine.tegDelivered(false, 0);
        assertKey("examine.moonstation14.atmos.teg-delivery-unknown", unknown);
        assertEquals(0, ((net.minecraft.network.chat.contents.TranslatableContents) unknown.getContents()).getArgs().length);
        assertKey("examine.moonstation14.atmos.teg-delivery-unknown", AtmosMachinePowerExamine.tegCapacity(Double.POSITIVE_INFINITY));
        assertEquals("0.0 W", ((net.minecraft.network.chat.contents.TranslatableContents)
                AtmosMachinePowerExamine.tegDelivered(true, 0).getContents()).getArgs()[0]);
    }

    private static void assertKey(String key, Component component) {
        assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, component.getContents());
        assertEquals(key, ((net.minecraft.network.chat.contents.TranslatableContents) component.getContents()).getKey());
    }
    @Test
    void descriptionsUseNamedTranslationsAndDoNotCreateGenericMachineRows() {
        BuiltinExamineContributors.register();
        var collector = new ExamineCollector(false, Component.literal("Machine"));
        var context = new ExamineContext(null, null, null, null, null, null, null, false);
        BuiltinExamineContributors.metadata(context, collector);
        assertEquals("Machine", collector.name().getString());
        assertTrue(collector.sortedEntries().isEmpty());
    }

    @Test
    void tegExamineSnapshotIsReadOnlyAndSurvivesConsumptionOfTransientOffer() {
        var state = new com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy.State(42000, 38000);
        var before = com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy.snapshot(state);
        assertNotNull(before);
        assertEquals(38000, before.currentWatts());
        assertEquals(38000, before.ratedMaximumWatts());
        assertEquals(38000, state.maximumSupplyWatts());
        assertEquals(42000, state.rampPositionWatts());
    }

    @Test
    void missingTegStateHasNoInventedOffer() {
        assertNull(com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy.snapshot(null));
    }
}
