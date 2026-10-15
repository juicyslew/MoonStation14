package com.juicyslew.moonstation14.ms14.equipment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;
/** Resolved ordered inventory layout and supported upstream slot policy. */
public record InventoryTemplateData(List<Slot> slots) {
    public enum SlotFlag { FEET, INNERCLOTHING, OUTERCLOTHING, GLOVES, NECK, MASK, EYES, EARS, HEAD, POCKET, SUITSTORAGE, IDCARD, BELT, BACK, PREVENTEQUIP }
    public static final Codec<SlotFlag> SLOT_FLAG_CODEC=Codec.STRING.comapFlatMap(value->{try{return com.mojang.serialization.DataResult.success(SlotFlag.valueOf(value));}catch(IllegalArgumentException exception){return com.mojang.serialization.DataResult.error(()->"unknown slot flag "+value);}},SlotFlag::name);
    public static final Codec<Slot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Slot::id), Codec.STRING.fieldOf("display_name").forGetter(Slot::displayName),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(Slot::icon), Codec.STRING.optionalFieldOf("group", "Default").forGetter(Slot::group),
            Codec.INT.fieldOf("x").forGetter(Slot::x), Codec.INT.fieldOf("y").forGetter(Slot::y),
            SLOT_FLAG_CODEC.listOf().optionalFieldOf("flags", List.of(SlotFlag.PREVENTEQUIP)).forGetter(Slot::flags),
            Codec.STRING.listOf().optionalFieldOf("depends_on", List.of()).forGetter(Slot::dependsOn),
            EquipmentTag.CODEC.listOf().optionalFieldOf("blacklist", List.of()).forGetter(Slot::blacklist),
            Codec.BOOL.optionalFieldOf("allow_suit_storage", false).forGetter(Slot::allowSuitStorage),
            Codec.DOUBLE.optionalFieldOf("strip_time", 4.0).forGetter(Slot::stripTime),
            Codec.BOOL.optionalFieldOf("strip_hidden", false).forGetter(Slot::stripHidden)
    ).apply(i, Slot::new));
    public static final Codec<InventoryTemplateData> CODEC = RecordCodecBuilder.create(i -> i.group(SLOT_CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(InventoryTemplateData::slots)).apply(i, InventoryTemplateData::new));
    public InventoryTemplateData {
        slots=List.copyOf(slots);
        if (slots.size() > EquipmentSlots.SLOT_ORDER.size()) throw new IllegalArgumentException("too many inventory slots");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(Slot slot:slots) if(!EquipmentSlots.SLOT_ORDER.contains(slot.id())||!ids.add(slot.id())) throw new IllegalArgumentException("unknown or duplicate inventory slot "+slot.id());
        for (Slot slot:slots) for(String dep:slot.dependsOn()) if(!ids.contains(dep)) throw new IllegalArgumentException("unknown slot dependency "+dep);
        java.util.Map<String,Slot> slotsById=new java.util.LinkedHashMap<>();
        for(Slot slot:slots) slotsById.put(slot.id(),slot);
        java.util.Set<String> done=new java.util.HashSet<>();
        for(Slot slot:slots) ensureAcyclic(slot.id(),slotsById,new java.util.HashSet<>(),done);
    }
    public java.util.Optional<Slot> slot(String id) { return slots.stream().filter(s->s.id().equals(id)).findFirst(); }
    private static void ensureAcyclic(String id,java.util.Map<String,Slot> slotsById,java.util.Set<String> active,java.util.Set<String> done){
        if(done.contains(id))return;
        if(!active.add(id))throw new IllegalArgumentException("cyclic equipment slot dependency at '"+id+"'");
        Slot current=slotsById.get(id);
        if(current==null)throw new IllegalArgumentException("unknown slot dependency '"+id+"'");
        for(String dependency:current.dependsOn())ensureAcyclic(dependency,slotsById,active,done);
        active.remove(id);done.add(id);
    }
    public record Slot(String id,String displayName,ResourceLocation icon,String group,int x,int y,List<SlotFlag> flags,List<String> dependsOn,List<EquipmentTag> blacklist,boolean allowSuitStorage,double stripTime,boolean stripHidden) {
        public Slot(String id,String displayName,ResourceLocation icon,String group,int x,int y,List<SlotFlag> flags,List<String> dependsOn,List<EquipmentTag> blacklist,boolean allowSuitStorage) { this(id,displayName,icon,group,x,y,flags,dependsOn,blacklist,allowSuitStorage,4.0,false); }
        public Slot { if(id==null||displayName==null||displayName.isBlank()||icon==null||!Set.of("Default","MainHotbar","SecondHotbar").contains(group)||!Double.isFinite(stripTime)||stripTime<0) throw new IllegalArgumentException("invalid slot metadata"); flags=List.copyOf(flags);dependsOn=List.copyOf(dependsOn);blacklist=List.copyOf(blacklist);if(new java.util.HashSet<>(flags).size()!=flags.size()||new java.util.HashSet<>(dependsOn).size()!=dependsOn.size()||new java.util.HashSet<>(blacklist).size()!=blacklist.size())throw new IllegalArgumentException("duplicate slot policy value"); }
    }
}

