package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import java.util.UUID;

/** AIの強さと討伐種別を分離する。死亡後もエンティティから参照できる。 */
public record BossIdentity(String bossId, UUID encounterId, String role, boolean leader) {
    private static final NamespacedKey ID=new NamespacedKey("iem","boss_id"),
            ENCOUNTER=new NamespacedKey("iem","boss_encounter"), ROLE=new NamespacedKey("iem","boss_role"),
            LEADER=new NamespacedKey("iem","boss_leader");
    public BossIdentity {
        if (bossId==null || bossId.isBlank() || encounterId==null || role==null || role.isBlank())
            throw new IllegalArgumentException("Invalid boss identity");
    }
    public void apply(Entity entity) {
        var data=entity.getPersistentDataContainer();
        data.set(ID,PersistentDataType.STRING,bossId); data.set(ENCOUNTER,PersistentDataType.STRING,encounterId.toString());
        data.set(ROLE,PersistentDataType.STRING,role); data.set(LEADER,PersistentDataType.BYTE,(byte)(leader?1:0));
    }
    public static BossIdentity read(Entity entity) {
        var data=entity.getPersistentDataContainer();
        String id=data.get(ID,PersistentDataType.STRING), encounter=data.get(ENCOUNTER,PersistentDataType.STRING), role=data.get(ROLE,PersistentDataType.STRING);
        Byte leader=data.get(LEADER,PersistentDataType.BYTE);
        if (id==null || encounter==null || role==null || leader==null || (leader!=0 && leader!=1)) return null;
        try { return new BossIdentity(id,UUID.fromString(encounter),role,leader==1); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
