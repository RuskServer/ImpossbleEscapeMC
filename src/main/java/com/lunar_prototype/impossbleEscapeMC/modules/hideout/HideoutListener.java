package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.craft.CraftDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.craft.CraftRecipes;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashPageSelectorGUI;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public class HideoutListener implements Listener {
    private final HideoutModule module;

    public HideoutListener(HideoutModule module) {
        this.module = module;
    }

    /**
     * 隠れ家のワールドに入った時 (隠れ家の中で参加し直した時も) はアドベンチャーにする (ブロックを壊させない)。
     * ほかの所で戻されることがあるので、隠れ家にいる間も見張る ({@link #enforceAll}。レイドと同じ)
     */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        enforceAdventure(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 隠れ家の場所の番号がほかの人と重なっていないか (前の版で配った番号)
        module.assignSlot(event.getPlayer().getUniqueId(), module.getDataModule().getPlayerData(event.getPlayer().getUniqueId()), false);
        enforceAdventure(event.getPlayer());
    }

    private void enforceAdventure(Player player) {
        if (!module.getWorldManager().isPlayerWorld(player.getWorld())) return;
        // 隠れ家の画面のカメラ (スペクテイター) の間はそのまま
        if (HideoutDialog.isCameraActive(player)) return;
        if (player.getGameMode() != GameMode.ADVENTURE) player.setGameMode(GameMode.ADVENTURE);
    }

    /** 集水設備: ロビーと隠れ家にいる間は空腹が減りにくい */
    @EventHandler(ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double multiplier = module.foodDrainMultiplier(player);
        if (multiplier < 1.0) event.setExhaustion((float) (event.getExhaustion() * multiplier));
    }

    /** 隠れ家のワールドにいる全員をアドベンチャーにする (HideoutModule が一定の間隔で呼ぶ) */
    void enforceAll() {
        org.bukkit.World world = module.getWorldManager().getPlayerWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) enforceAdventure(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // 自分の隠れ家の、設備が置いたブロック: その設備の画面を開く (チェストなどの元の動作はさせない)。
        // 右クリックは手ごとに来るので画面はメインハンドでだけ開くが、イベントは両手とも止める。
        // 止めないと、オフハンドのバックパックを BackpackListener が拾ってバックパックの画面を開いてしまう
        HideoutFacility facility = module.facilityAt(event.getPlayer(), block);
        boolean opensScreen = facility == HideoutFacility.STASH || facility == HideoutFacility.SKILL_TREE
                || (facility != null && CraftRecipes.crafts(facility));
        if (opensScreen) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) return;
            if (facility == HideoutFacility.STASH) {
                StashModule stash = module.getPlugin().getServiceContainer().get(StashModule.class);
                if (stash != null) new StashPageSelectorGUI(event.getPlayer(), stash).open();
            } else if (facility == HideoutFacility.SKILL_TREE) {
                com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillTreeDialog.open(event.getPlayer());
            } else {
                CraftDialog.open(event.getPlayer(), facility);
            }
            return;
        }

        if (!(block.getState() instanceof Sign sign)) return;

        // 看板の1行目をチェックして種類を判別 (エディターで書いてNBT保存する想定)
        String line1 = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase();

        // 画面を開く看板も、オフハンドのバックパックが開かないように両手とも止める
        if (line1.contains("workbench") || line1.contains("hideout")) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) return;
            if (line1.contains("workbench")) {
                CraftDialog.open(event.getPlayer(), HideoutFacility.WORKBENCH);
            } else {
                HideoutDialog.open(event.getPlayer());
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (line1.contains("generator")) {
            event.getPlayer().sendMessage("発電機の燃料メニュー（未実装）を開きます。");
            // new GeneratorGUI(module, event.getPlayer()).open();
        }
    }
}
