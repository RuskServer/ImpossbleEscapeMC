package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;
import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Command(name = "gunskin", description = "手に持った銃のスキンを変更", permission = "iem.gunskin")
public final class GunSkinCommand implements BasicCommand {
    // パック側の gun_skin_shared.STYLES のタイル順。0 は元塗装。
    private static final List<String> SKINS = List.of("default", "crimson_camo", "whiteout", "ion_bloom", "sand", "woodland");
    private static final int COLOR_BASE = 0x49EA00;
    private final Set<String> guns;

    public GunSkinCommand(List<String> guns) {
        this.guns = Set.copyOf(guns);
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            Messages.chat(stack.getSender(), "iem.inventory.gunskin.held", MessageText.of("iem.inventory.gunskin.held"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String gun = DatapackGunCatalog.gunIdOf(held);
        if (gun == null || !guns.contains(gun)) {
            Messages.chat(player, "iem.inventory.gunskin.held", MessageText.of("iem.inventory.gunskin.held"));
            return;
        }
        int slot = args.length == 1 ? SKINS.indexOf(args[0].toLowerCase(Locale.ROOT)) : -1;
        if (slot < 0) {
            Messages.chat(player, "iem.inventory.gunskin.usage", MessageText.of("iem.inventory.gunskin.usage", String.join(" | ", SKINS)));
            return;
        }
        DyedItemColor current = held.getData(DataComponentTypes.DYED_COLOR);
        int currentSlot = current == null ? 0 : current.color().asRGB() - COLOR_BASE;
        if (current != null && (currentSlot < 1 || currentSlot >= SKINS.size())) {
            Messages.chat(player, "iem.inventory.gunskin.conflict", MessageText.of("iem.inventory.gunskin.conflict"));
            return;
        }
        if (slot != currentSlot) {
            ItemStack changed = held.clone();
            if (slot == 0) {
                changed.resetData(DataComponentTypes.DYED_COLOR);
            } else {
                changed.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(COLOR_BASE + slot)));
            }
            player.getInventory().setItemInMainHand(changed);
        }
        Messages.chat(player, "iem.inventory.gunskin.applied", MessageText.of("iem.inventory.gunskin.applied", gun, SKINS.get(slot)));
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SKINS.stream().filter(name -> name.startsWith(prefix)).toList();
    }
}
