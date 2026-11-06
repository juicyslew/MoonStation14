package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.AngularRecoveryComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AngularRecoveryPolicyTest {
    @Test void resolvedPrototypeInheritanceDefaultsOverrideAndOptOut(){
        var parent=JsonParser.parseString("{\"abstract\":true,\"components\":[{\"type\":\"AngularRecovery\"}]}").getAsJsonObject();
        var child=JsonParser.parseString("{\"parent\":\"recovery_parent\",\"components\":[{\"type\":\"AngularRecovery\",\"grace_seconds\":0.8}]}").getAsJsonObject();
        var defaultsChild=JsonParser.parseString("{\"parent\":\"recovery_parent\"}").getAsJsonObject();
        var absent=JsonParser.parseString("{}").getAsJsonObject();
        var manager=new PrototypeManager(); manager.register(ModCharacters.CHARACTER_TYPE);
        var parentId=ResourceLocation.parse("moonstation14:recovery_parent"); var childId=ResourceLocation.parse("moonstation14:recovery_child"); var defaultsId=ResourceLocation.parse("moonstation14:recovery_defaults"); var absentId=ResourceLocation.parse("moonstation14:recovery_absent");
        manager.reload(ModCharacters.CHARACTER_TYPE,Map.of(parentId,parent,childId,child,defaultsId,defaultsChild,absentId,absent));
        CharacterData resolved=manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId);
        assertEquals(.8,resolved.angularRecoveryPolicy().orElseThrow().graceSeconds());
        assertFalse(manager.snapshot(ModCharacters.CHARACTER_TYPE).contains(parentId));
        var base=manager.snapshot(ModCharacters.CHARACTER_TYPE).get(defaultsId).angularRecoveryPolicy().orElseThrow();
        assertEquals(new AngularRecoveryComponent(.65,1,45,1,10,.25,.5,true),base);
        assertTrue(manager.snapshot(ModCharacters.CHARACTER_TYPE).get(absentId).angularRecoveryPolicy().isEmpty());
    }
    @Test void directConstructorAndSchemaRejectInvalidPolicy(){
        assertThrows(IllegalArgumentException.class,()->new AngularRecoveryComponent(-1,1,45,1,10,.25,.5,true));
        for(String json:new String[]{"{\"type\":\"AngularRecovery\",\"grace_seconds\":-1}","{\"type\":\"AngularRecovery\",\"recovery_seconds\":0}","{\"type\":\"AngularRecovery\",\"bad_landing_tilt_degrees\":181}","{\"type\":\"AngularRecovery\",\"comfort_tilt_degrees\":46}","{\"type\":\"AngularRecovery\",\"comfort_return_enabled\":1}","{\"type\":\"AngularRecovery\",\"unexpected\":1}"}){
            var raw=JsonParser.parseString("{\"components\":["+json+"]}").getAsJsonObject();
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,raw).error().isPresent(),json);
            assertThrows(IllegalArgumentException.class,()->CharacterSchemaAudit.audit(raw));
        }
    }
}
