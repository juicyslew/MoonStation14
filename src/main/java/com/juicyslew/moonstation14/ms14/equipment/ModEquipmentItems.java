package com.juicyslew.moonstation14.ms14.equipment;
import com.juicyslew.moonstation14.MoonStation14;import com.juicyslew.moonstation14.ms14.prototype.*;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.level.Level;import java.util.Objects;
public final class ModEquipmentItems {
 private ModEquipmentItems(){}
 public static final PrototypeType<EquipmentItemData> TYPE=new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"equipment_item"),"moonstation14/equipment_item",EquipmentItemData.CODEC,new EquipmentItemMergeStrategy());
 public static PrototypeCatalog<EquipmentItemData> serverCatalog(){return PrototypeRuntime.serverEquipmentItems();}
 public static PrototypeCatalog<EquipmentItemData> clientCatalog(){return PrototypeRuntime.clientEquipmentItems();}
 public static PrototypeCatalog<EquipmentItemData> catalog(Level level){Objects.requireNonNull(level);return level.isClientSide()?clientCatalog():serverCatalog();}
 public static EquipmentItemData require(PrototypeCatalog<EquipmentItemData> catalog,ResourceLocation id){EquipmentItemData data=Objects.requireNonNull(catalog).get(Objects.requireNonNull(id));if(data==null)throw new IllegalArgumentException("Missing equipment item '"+id+"'");return data;}
 public static void bootstrapClientBundled(){var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();for(String name:java.util.List.of("base_item","base_clothing","grey_cap","poppy","grey_jumpsuit","simple_coat","black_gloves","black_shoes","glasses","red_tie","black_bandana","earpieces","blank_id_card","utility_case","bag","belt","suit_rad","hood_rad"))raw.put(ModInventoryTemplates.id(name),read(name));PrototypeRuntime.clientManager().reload(TYPE,raw);}
 private static com.google.gson.JsonObject read(String name){String path="/data/moonstation14/moonstation14/equipment_item/"+name+".json";try(var stream=ModEquipmentItems.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing bundled prototype "+path);return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException e){throw new IllegalStateException("Could not read "+path,e);}}
}
