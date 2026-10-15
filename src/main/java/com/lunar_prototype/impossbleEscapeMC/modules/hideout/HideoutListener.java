package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashPageSelectorGUI;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

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

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 右クリックは手ごとに来ることがあるので、メインハンドの時だけ (画面を2回開かない)
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // 自分の隠れ家の、設備が置いたブロック: その設備の画面を開く (チェストなどの元の動作はさせない)
        HideoutFacility facility = module.facilityAt(event.getPlayer(), block);
        if (facility == HideoutFacility.STASH) {
            event.setCancelled(true);
            StashModule stash = module.getPlugin().getServiceContainer().get(StashModule.class);
            if (stash != null) new StashPageSelectorGUI(event.getPlayer(), stash).open();
            return;
        }
        if (facility == HideoutFacility.SKILL_TREE) {
            event.setCancelled(true);
            com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillTreeDialog.open(event.getPlayer());
            return;
        }

        if (!(block.getState() instanceof Sign sign)) return;

        // 看板の1行目をチェックして種類を判別 (エディターで書いてNBT保存する想定)
        String line1 = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase();
        
        if (line1.contains("generator")) {
            event.getPlayer().sendMessage("発電機の燃料メニュー（未実装）を開きます。");
            // new GeneratorGUI(module, event.getPlayer()).open();
        } else if (line1.contains("workbench")) {
            event.getPlayer().sendMessage("ワークベンチのクラフトメニュー（未実装）を開きます。");
            // new WorkbenchGUI(module, event.getPlayer()).open();
        } else if (line1.contains("hideout")) {
            HideoutDialog.open(event.getPlayer());
        }
    }
}
