package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LobbyVisibilityListener extends PacketListenerAbstract {

    private final ImpossbleEscapeMC plugin;

    public LobbyVisibilityListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            Player receiver = (Player) event.getPlayer();

            // ロビーワールド（オーバーワールドの初期スポーンワールド）かチェック
            if (!receiver.getWorld().equals(org.bukkit.Bukkit.getWorlds().get(0))) {
                return;
            }

            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
            
            // パケットの対象エンティティを特定
            Player targetPlayer = null;
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getEntityId() == packet.getEntityId()) {
                    targetPlayer = p;
                    break;
                }
            }

            // 対象が自分でないプレイヤーの場合のみ処理
            if (targetPlayer != null && !targetPlayer.equals(receiver)) {
                List<Equipment> equipmentList = new ArrayList<>(packet.getEquipment());
                boolean modified = false;

                for (Equipment equipment : equipmentList) {
                    EquipmentSlot slot = equipment.getSlot();

                    // 防具スロット（ヘルメット、チェストプレート、レギンス、ブーツ）をチェック
                    if (isArmorSlot(slot)) {
                        // アイテムを AIR に差し替える
                        equipment.setItem(SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(Material.AIR)));
                        modified = true;
                    }
                }

                if (modified) {
                    packet.setEquipment(equipmentList);
                }
            }
        }
    }

    private boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HELMET || 
               slot == EquipmentSlot.CHEST_PLATE ||
               slot == EquipmentSlot.LEGGINGS || 
               slot == EquipmentSlot.BOOTS;
    }
}
