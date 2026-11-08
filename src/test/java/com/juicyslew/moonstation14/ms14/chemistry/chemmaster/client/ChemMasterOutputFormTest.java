package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterOutputFormTest {
    @Test void authoritativeSuggestionsAndCurrentBounds() {
        var form = new ChemMasterOutputForm();
        assertEquals("1", form.pillDose());
        assertEquals("0", form.pillCount());
        assertEquals("0", form.bottleDose());
        form.authoritativeState(0, 4, 20, 30);
        assertEquals("0", form.pillDose());
        assertFalse(form.state(0, 4, 20, 30).validPillDose());
        form.authoritativeState(17, 4, 20, 30);
        assertEquals("17", form.pillDose());
        assertEquals("1", form.pillCount());
        assertEquals("17", form.bottleDose());
        form.editPillDose("999", 20);
        assertEquals("999", form.pillDose());
        assertFalse(form.state(17, 4, 20, 30).canCreatePills());
        form.authoritativeState(25, 4, 20, 30);
        assertEquals("999", form.pillDose(),"an explicit dose is never silently lowered to a new suggestion");
        assertEquals("0", form.pillCount(),"an out-of-range explicit dose has no eligible count");
    }

    @Test void manualLabelIsBoundedAndSurvivesGeneratedRefresh() {
        var form = new ChemMasterOutputForm();
        form.generatedLabel("Sodium chloride");
        form.editLabel("x".repeat(51));
        assertEquals(50, form.label().length());
        assertTrue(form.updateLabel());
        form.generatedLabel("new reagent label");
        assertEquals("x".repeat(50), form.label());
        form.authoritativeGeneratedLabel("keep on false",false);
        assertEquals("x".repeat(50),form.label());
        form.authoritativeGeneratedLabel("Sodium",true);
        assertEquals("Sodium",form.label());
        assertFalse(form.updateLabel());
    }

    @Test void emptyZeroAndNonIntegerDraftsNeverEnableCommands() {
        var form = new ChemMasterOutputForm();
        form.authoritativeState(20, 10, 20, 30);
        form.editPillDose("1.5", 20);
        assertEquals("", form.pillDose());
        assertFalse(form.state(20, 10, 20, 30).canCreatePills());
        form.editBottleDose("99", 7);
        assertEquals("99", form.bottleDose());
    }

    @Test void explicitTypedOrAdjustedPillDosePersistsAndRecountsWithinNewSourceBounds(){
        var typed=new ChemMasterOutputForm();typed.authoritativeState(120,10,20,0);
        assertEquals("20",typed.pillDose());assertEquals("6",typed.pillCount());assertFalse(typed.hasExplicitPillDose());
        typed.editPillDose("5",20);
        typed.authoritativeState(90,4,20,0);
        assertEquals("5",typed.pillDose());assertEquals("4",typed.pillCount());assertTrue(typed.hasExplicitPillDose());
        typed.authoritativeState(4,4,20,0);
        assertEquals("5",typed.pillDose());assertEquals("0",typed.pillCount(),"source below chosen dose disables creation instead of lowering dosage");
        typed.authoritativeState(90,4,4,0);
        assertEquals("5",typed.pillDose());assertEquals("0",typed.pillCount(),"a stricter dosage limit never silently changes the chosen dose");
        assertFalse(typed.state(90,4,4,0).canCreatePills());

        var adjusted=new ChemMasterOutputForm();adjusted.authoritativeState(120,10,20,0);
        adjusted.adjustPillDose(-15,20);
        assertEquals("5",adjusted.pillDose());adjusted.authoritativeState(90,4,20,0);
        assertEquals("5",adjusted.pillDose());assertEquals("4",adjusted.pillCount());
    }
}
