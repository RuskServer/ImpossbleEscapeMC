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
        org.bukkit.entity.LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // 弾丸などの間接ダメージでのキラー特定
        if (killer == null && victim.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player p) {
                killer = p;
            } else if (edbe.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
                killer = p;
            }
        }

        if (killer == null) return;

        PlayerData data = dataModule.getPlayerData(killer.getUniqueId());
        if (data == null) return;

        Map<String, Object> params = new HashMap<>();
        // エンティティタイプの判定
        String type = victim.getType().name();
        
        // SCAVシステムのコントローラを確認して詳細に判定
        com.lunar_prototype.impossbleEscapeMC.ai.ScavController controller = 
                com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner.getController(victim.getUniqueId());
        
        if (controller != null) {
            if (controller.getBrain().getBrainLevel() == com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain.BrainLevel.HIGH) {
                type = "BOSS";
            } else {
                type = "SCAV";
            }
        } else if (victim instanceof Player) {
            type = "PMC";
        }
        
        params.put("entityType", type);
        questModule.getEventBus().fire(killer, data, QuestTrigger.KILL_ENTITY, params);
    }
    
    // 脱出トリガーは RaidInstance 側で直接発火されるため、ここでは不要。
    // 納品トリガーは GUI 等でアイテムが消費された際に発火させる。
}
