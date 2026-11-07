package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TegFueledFixtureStatusFormatterTest {
    private TegFueledFixtureStatusFormatter.Snapshot sample(boolean known){return new TegFueledFixtureStatusFormatter.Snapshot("initialized / finite","(22, 2, 18)","(11, 2, 16)",true,1.55,14,1800,270,294,43,400,101,43,290,101,"core 1000 W / UNKNOWN",387300,12,120,500,"UNKNOWN",known?"100 W @sample 90 / topology 4":"UNKNOWN",1234,true,known?"100 J since observer start":"UNKNOWN");}
    @Test void formatsMeasuredFiniteStatusWithUnitsAndSeparatedElectricalBoundaries(){var text=TegFueledFixtureStatusFormatter.format(sample(true));assertTrue(text.contains("plasma 1.6 mol, O2 14.0 mol"),text);assertTrue(text.contains("270.0 mol (270 mol initial)"),text);assertTrue(text.contains("43.0 mol, 400.0 K, bulk-equivalent 101.0 kPa"),text);assertTrue(text.contains("generated 12.0 J / 120.0 W"),text);assertTrue(text.contains("offered HV UNKNOWN; output APC allocation 100 W"),text);assertTrue(text.contains("delivered 100 J"),text);assertTrue(text.contains("not net-positive or self-sustaining"),text);assertTrue(text.contains("(11, 2, 16) ON"),text);}
    @Test void unknownMetersRemainUnknownAndNeverBecomeZeroOrDeliveredOffer(){var text=TegFueledFixtureStatusFormatter.format(sample(false));assertTrue(text.contains("allocation UNKNOWN"));assertTrue(text.contains("offered HV UNKNOWN"));assertTrue(text.contains("delivered UNKNOWN"));assertFalse(text.contains("delivered 0.0 J"));}
}
