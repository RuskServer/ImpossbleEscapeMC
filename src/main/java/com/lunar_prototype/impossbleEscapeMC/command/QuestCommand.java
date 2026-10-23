package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(name = "quest", description = "クエストシステム管理コマンド", permission = "op")
public class QuestCommand implements BasicCommand {
    private final QuestModule questModule;

    public QuestCommand(QuestModule questModule) {
        this.questModule = questModule;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                questModule.loadQuests();
                sender.sendMessage("§aQuests reloaded.");
            }
            return;
        }
        
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("impossbleescape.admin")) return;
            questModule.loadQuests();
            sender.sendMessage("§aQuests reloaded.");
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("handin")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage("§c手にアイテムを持っていません。");
                return;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(PDCKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                player.sendMessage("§cこれは納品可能なカスタムアイテムではありません。");
                return;
            }

            String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def == null) return;

            PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());

            Map<String, Object> params = new HashMap<>();
            params.put("itemId", itemId);
            params.put("itemType", def.type);
            params.put("amount", item.getAmount());
            boolean isFIR = meta.getPersistentDataContainer().getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte)0) == 1;
            params.put("isFIR", isFIR);

            // トリガーを発火
            questModule.getEventBus().fire(player, data, QuestTrigger.HAND_IN, params);
            
            // アイテムを消費
            player.getInventory().setItemInMainHand(null);
            player.sendMessage("§aアイテムを納品しました: " + def.displayName + " x" + item.getAmount());
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("open")) {
            QuestDialog.openPda(player);
            return;
        }
        return;
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("handin");
            completions.add("open");
        }
        return completions;
    }
}
