package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

/** Local, non-authoritative draft state for ChemMaster output controls. */
public final class ChemMasterOutputForm {
    public static final int LABEL_LIMIT = 50;
    public record NumericInputSignature(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.DrawSource drawSource,
                                        long sourceVolumeCents,
                                        com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState.OutputKind outputKind,
                                        int freePillCells,int freeBottleCapacity,int pillDosageLimit) { }
    private String label = "";
    private String pillCount = "0", pillDose = "1", bottleDose = "0";
    private boolean updateLabel;
    private boolean explicitPillDose;
    private NumericInputSignature lastNumericInputs;

    public record State(String label, String pillCount, String pillDose, String bottleDose,
                        boolean updateLabel, int sourceUnits, int freePillCells,
                        int pillDoseLimit, int bottleDoseLimit) {
        public boolean validPillDose() { return positiveInRange(pillDose, pillDoseLimit); }
        public boolean canCreatePills() {
            return validPillDose() && positiveInRange(pillCount, Math.min(64,freePillCells))
                    && (long) integer(pillCount) * integer(pillDose) <= sourceUnits;
        }
        public boolean canFillBottle() { return positiveInRange(bottleDose, Math.min(21474836,bottleDoseLimit)) && integer(bottleDose) <= sourceUnits; }
    }

    public State state(int sourceUnits, int freePillCells, int pillDoseLimit, int bottleDoseLimit) {
        int source = Math.max(0, sourceUnits), cells = Math.max(0, freePillCells);
        int doseLimit = Math.max(0, pillDoseLimit), bottleLimit = Math.max(0, bottleDoseLimit);
        return new State(label, pillCount, pillDose, bottleDose, updateLabel, source, cells, doseLimit, bottleLimit);
    }

    /** Fresh authoritative state resets only numeric suggestions; label remains user-owned. */
    public void authoritativeState(int sourceUnits, int freePillCells, int pillDoseLimit, int freeBottleCapacity) {
        int source = Math.max(0, sourceUnits), limit = Math.max(0, pillDoseLimit);
        if(!explicitPillDose)pillDose=Integer.toString(Math.min(source,limit));
        pillCount=Integer.toString(suggestedCount(source,freePillCells,limit,pillDose));
        bottleDose = Integer.toString(Math.min(Math.max(0, freeBottleCapacity), source));
    }

    /** Refreshes suggestions only when their actual authoritative numeric inputs change. */
    public boolean authoritativeState(NumericInputSignature inputs,int sourceUnits,int freePillCells,
                                     int pillDoseLimit,int freeBottleCapacity){
        if(inputs.equals(lastNumericInputs))return false;
        lastNumericInputs=inputs;
        authoritativeState(sourceUnits,freePillCells,pillDoseLimit,freeBottleCapacity);
        return true;
    }

    public boolean editLabel(String value) {
        if (value == null) return false;
        label = truncate(value, LABEL_LIMIT);
        updateLabel = true;
        return true;
    }
    public void generatedLabel(String value) {
        if (!updateLabel) label = truncate(value == null ? "" : value, LABEL_LIMIT);
    }
    public void authoritativeGeneratedLabel(String value,boolean regenerate){
        if(regenerate){label=truncate(value==null?"":value,LABEL_LIMIT);updateLabel=false;}
    }
    public void editPillCount(String value) { pillCount = boundedDraft(value, 0, Integer.MAX_VALUE); }
    public void editPillDose(String value, int currentLimit) { pillDose = boundedDraft(value, 0, Math.max(0, currentLimit));explicitPillDose=true; }
    /** Syncs a native in-progress value without turning an automatic suggestion into a manual dose choice. */
    public void restorePillDoseDraft(String value) { pillDose=boundedDraft(value,0,Integer.MAX_VALUE); }
    public void editBottleDose(String value, int currentLimit) { bottleDose = boundedDraft(value, 0, Math.max(0, currentLimit)); }
    public String label() { return label; }
    public String pillCount() { return pillCount; }
    public String pillDose() { return pillDose; }
    public String bottleDose() { return bottleDose; }
    public boolean updateLabel() { return updateLabel; }
    public boolean hasExplicitPillDose() { return explicitPillDose; }
    public void adjustCount(int delta,int maximum){pillCount=Integer.toString(adjust(pillCount,delta,0,Math.max(0,maximum)));}
    public void adjustPillDose(int delta,int maximum){int max=Math.max(0,maximum);pillDose=Integer.toString(adjust(pillDose,delta,max>0?1:0,max));explicitPillDose=true;}
    public void adjustBottleDose(int delta,int maximum){bottleDose=Integer.toString(adjust(bottleDose,delta,0,Math.max(0,maximum)));}

    private static boolean positiveInRange(String value, int max) {
        return isInteger(value) && integer(value) > 0 && integer(value) <= max;
    }
    private static String boundedDraft(String value, int min, int max) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(Character::isDigit)) return "";
        try { Integer.parseInt(value); return value; }
        catch (NumberFormatException e) { return ""; }
    }
    private static int suggestedCount(int source,int freeCells,int doseLimit,String doseText){
        if(!positiveInRange(doseText,doseLimit))return 0;
        int dose=integer(doseText);
        if(dose>source)return 0;
        return Math.min(Math.max(0,freeCells),source/dose);
    }
    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(Character::isDigit)) return false;
        try { Integer.parseInt(value); return true; } catch (NumberFormatException e) { return false; }
    }
    private static int integer(String value) { return Integer.parseInt(value); }
    private static int adjust(String value,int delta,int min,int max){
        int current=0;try{current=Integer.parseInt(value);}catch(RuntimeException ignored){}
        return (int)Math.max(min,Math.min(max,(long)current+delta));
    }
    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
