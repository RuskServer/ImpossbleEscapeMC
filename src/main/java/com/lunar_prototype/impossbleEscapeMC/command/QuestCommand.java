package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestCommand implements CommandExecutor, TabCompleter {
    private final QuestModule questModule;

    public QuestCommand(QuestModule questModule) {
        this.questModule = questModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                questModule.loadQuests();
                sender.sendMessage("§aQuests reloaded.");
            }
            return true;
        }
        
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("impossbleescape.admin")) return true;
            questModule.loadQuests();
            sender.sendMessage("§aQuests reloaded.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("handin")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage("§c手にアイテムを持っていません。");
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(PDCKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                player.sendMessage("§cこれは納品可能なカスタムアイテムではありません。");
                return true;
            }

            String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def == null) return true;

            PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());

            Map<String, Object> params = new HashMap<>();
            params.put("itemId", itemId);
            params.put("itemType", def.type);
            params.put("amount", item.getAmount());

            // トリガーを発火
            questModule.getEventBus().fire(player, data, QuestTrigger.HAND_IN, params);
            
            // アイテムを消費
            player.getInventory().setItemInMainHand(null);
            player.sendMessage("§aアイテムを納品しました: " + def.displayName + " x" + item.getAmount());
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("handin");
        }
        return completions;
    }
}
