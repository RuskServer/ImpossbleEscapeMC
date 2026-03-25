package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * BukkitイベントをQuestEventBusに橋渡しするリスナー
 */
public class QuestListener implements Listener {
    private final QuestModule questModule;
    private final PlayerDataModule dataModule;

    public QuestListener(QuestModule questModule, PlayerDataModule dataModule) {
        this.questModule = questModule;
        this.dataModule = dataModule;
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerData data = dataModule.getPlayerData(killer.getUniqueId());
        if (data == null) return;

        Map<String, Object> params = new HashMap<>();
        // エンティティタイプの判定 (メタデータ等からSCAVを判別)
        String type = event.getEntity().getType().name();
        if (event.getEntity().hasMetadata("SCAV")) {
            type = "SCAV";
        }
        params.put("entityType", type);

        questModule.getEventBus().fire(killer, data, QuestTrigger.KILL_ENTITY, params);
    }
    
    // 脱出トリガーは RaidInstance 側で直接発火されるため、ここでは不要。
    // 納品トリガーは GUI 等でアイテムが消費された際に発火させる。
}
