package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

public class ScavController {
    private final Mob scav;
    private final ScavBrain brain;
    private final GunListener gunListener;

    public ScavController(Mob scav, GunListener listener) {
        this.scav = scav;
        this.brain = new ScavBrain(scav);
        this.gunListener = listener;
    }

    public void onTick() {
        LivingEntity target = scav.getTarget();

        // 装備中の銃のステータス取得
        ItemStack item = scav.getEquipment().getItemInMainHand();
        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) {
            Bukkit.getLogger().warning("[SCAV-AI] ItemDefinition or GunStats is null for: " + itemId);
            return;
        }

        // AIに思考させる
        int[] actions = brain.decide(target, def.gunStats);

        if (actions.length < 2) return;

        // --- デバッグログ: AIが何を選んだか ---
        // actions[0]:移動, actions[1]:射撃, actions[2]:リロード
        Bukkit.getLogger().info(String.format("[SCAV-AI] %s Thought -> Move:%d, Shoot:%d",
                scav.getName(), actions[0], actions[1]));

        // --- Action 0: 移動 ---
        switch (actions[0]) {
            case 0: // AIが明示的な移動行動を返さない場合、デフォルトで標的に接近
            case 1: // 接近
                if (target != null) {
                    scav.getPathfinder().moveTo(target);
                }
                break;
            case 2: // 後退
                if (target != null) {
                    scav.getPathfinder().moveTo(scav.getLocation().add(
                            scav.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(5)));
                }
                break;
        }

        // --- Action 1: 射撃 ---
        if (actions[1] == 1) {
            if (target != null && scav.hasLineOfSight(target)) {
                // ログ出力: 実際に射撃命令が飛んでいるか確認
                Bukkit.getLogger().info("[SCAV] " + scav.getName() + " is SHOOTING at " + target.getName());
                gunListener.executeMobShoot(scav, def.gunStats, 1, 0.2);
            } else if (target != null) {
                // 射撃を選んだが、射線が通っていない場合
                // Bukkit.getLogger().info("[SCAV] " + scav.getName() + " wants to shoot but NO LINE OF SIGHT");
            }
        }
    }

    public ScavBrain getBrain() { return brain; }

    /**
     * Spawner側で個体の状態をチェックするために必要
     */
    public Mob getScav() {
        return this.scav;
    }
}