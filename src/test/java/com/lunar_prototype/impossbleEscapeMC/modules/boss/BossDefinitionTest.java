package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BossDefinitionTest {
    static BossDefinition defaults() {
        return BossCatalog.create().get(BossDefinition.DEFAULT_ID);
    }
    @Test void javaDefinitionPreservesExistingSquad() throws Exception {
        var d=defaults();
        assertEquals(.125,d.chance());assertEquals(Set.of("ノヴォザリエ薬品工場"),d.maps());
        assertEquals(List.of("boss","guard1","guard2"),d.members().stream().map(BossDefinition.Member::role).toList());
        assertEquals(List.of("scar_h","m4a1","as_val"),d.members().stream().map(BossDefinition.Member::gun).toList());
        assertEquals(List.of(true,false,false),d.members().stream().map(BossDefinition.Member::leader).toList());
        assertEquals(List.of(8.0,12.0,12.0),d.members().stream().map(BossDefinition.Member::radius).toList());
        for(var m:d.members()) {
            assertEquals(40,m.health());assertEquals("best",m.ammo());
            assertEquals("Altyn",m.helmet());assertEquals("sp_v",m.chest());assertEquals("pilgrim_tourist_backpack",m.backpack());
        }
    }
    @Test void arbitraryRolesAndLeaderOrderValidate() throws Exception {
        var template=defaults().members().getFirst();
        var members=new ArrayList<BossDefinition.Member>();var positions=new LinkedHashMap<String,double[]>();
        for(int i=0;i<5;i++) {
            String role="member"+i;
            members.add(new BossDefinition.Member(role,i==4,template.gun(),null,null,null,template.brain(),80,6,"custom_ammo"));
            positions.put(role,new double[]{1.5+2*i,1,1.5,0,0});
        }
        var d=new BossDefinition("custom","Custom",true,Set.of("map"),1,members);
        var p=new BossPlacement(d.id(),"zone",positions);var z=new BossTerritory("zone",0,0,0,20,10,20);
        assertNull(p.problem(d,z));
        positions.get("member0")[0]=999;assertEquals(1.5,p.position("member0")[0]);
        p.positions().get("member0")[0]=999;assertEquals(1.5,p.position("member0")[0]);
        var missing=new LinkedHashMap<>(p.positions());missing.remove("member0");
        assertNotNull(new BossPlacement(d.id(),"zone",missing).problem(d,z));
        var extra=new LinkedHashMap<>(p.positions());extra.put("unknown",new double[]{2,1,2,0,0});
        assertNotNull(new BossPlacement(d.id(),"zone",extra).problem(d,z));
        assertNotNull(p.problem(null,z));assertNotNull(p.problem(d,null));
    }
    @Test void invalidDefinitionsAreRejected() throws Exception {
        var d=defaults();var leader=d.members().getFirst();
        assertThrows(IllegalArgumentException.class,()->new BossDefinition("boss","Name",true,Set.of(),1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new BossDefinition("boss","Name",true,Set.of(),1,List.of(leader,leader)));
        assertThrows(IllegalArgumentException.class,()->new BossDefinition("boss","Name",true,Set.of(),1,List.of(d.members().get(1))));
        assertThrows(IllegalArgumentException.class,()->new BossDefinition.Member("guard",false,"gun",null,null,null,leader.brain(),Double.NaN,12,"best"));
        assertThrows(IllegalArgumentException.class,()->new BossPlacement("boss","zone",Map.of("guard",new double[]{1,2,3,4,Double.NaN})));
    }
    @Test void bossModuleDoesNotDependOnRaidTypes() throws Exception {
        try(var paths=Files.list(Path.of("src/main/java/com/lunar_prototype/impossbleEscapeMC/modules/boss"))) {
            for(Path p:paths.filter(p->p.toString().endsWith(".java")).toList())
                assertFalse(Files.readString(p).contains("modules.raid"),p.toString());
        }
    }
}
