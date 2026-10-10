package com.juicyslew.moonstation14.ms14.equipment;
import com.juicyslew.moonstation14.MoonStation14;import com.juicyslew.moonstation14.ms14.prototype.*;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.level.Level;import java.util.Objects;
public final class ModInventoryTemplates {
 private ModInventoryTemplates(){}
 public static final ResourceLocation HUMAN_ID=id("human");
 public static final PrototypeType<InventoryTemplateData> TYPE=new PrototypeType<>(id("inventory_template"),"moonstation14/inventory_template",InventoryTemplateData.CODEC,new InventoryTemplateMergeStrategy());
 public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,path);}
 public static PrototypeCatalog<InventoryTemplateData> serverCatalog(){return PrototypeRuntime.serverInventoryTemplates();}
 public static PrototypeCatalog<InventoryTemplateData> clientCatalog(){return PrototypeRuntime.clientInventoryTemplates();}
 public static PrototypeCatalog<InventoryTemplateData> catalog(Level level){Objects.requireNonNull(level);return level.isClientSide()?clientCatalog():serverCatalog();}
 public static InventoryTemplateData require(PrototypeCatalog<InventoryTemplateData> catalog,ResourceLocation id){InventoryTemplateData data=Objects.requireNonNull(catalog).get(Objects.requireNonNull(id));if(data==null)throw new IllegalArgumentException("Missing inventory template '"+id+"'");return data;}
 public static void bootstrapClientBundled(){var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();raw.put(HUMAN_ID,read("human"));PrototypeRuntime.clientManager().reload(TYPE,raw);}
 private static com.google.gson.JsonObject read(String name){String path="/data/moonstation14/moonstation14/inventory_template/"+name+".json";try(var stream=ModInventoryTemplates.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing bundled prototype "+path);return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException e){throw new IllegalStateException("Could not read "+path,e);}}
}
