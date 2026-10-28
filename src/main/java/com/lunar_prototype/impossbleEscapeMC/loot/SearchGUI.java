package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndEvent;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndReason;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionSourceType;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionStartEvent;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SearchGUI implements Listener {
    /** 1 マスの探索にかかる時間 (tick) */
    private static final int SEARCH_TICKS = 10;

    private final ImpossbleEscapeMC plugin;
    private final NamespacedKey SEARCHED_SLOTS_KEY;

    public SearchGUI(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.SEARCHED_SLOTS_KEY = new NamespacedKey(plugin, "searched_slots");
    }

    public static class SearchHolder implements InventoryHolder {
        private final Object source; // Container or Entity
        private final Inventory liveInventory;
        private final Set<Integer> sessionSearchedSlots = new HashSet<>();
        private final Set<Integer> searchingSlots = new HashSet<>();
        private final Map<Integer, Integer> searchFrames = new HashMap<>();
        private final Map<Integer, int[]> waves = new HashMap<>();
        private com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.Bag bag;
        public SearchHolder(Object source, Inventory liveInventory) {
            this.source = source;
            this.liveInventory = liveInventory;
        }
        public Object getSource() { return source; }
        public Inventory getLiveInventory() { return liveInventory; }
        public Set<Integer> getSessionSearchedSlots() { return sessionSearchedSlots; }
        public Set<Integer> getSearchingSlots() { return searchingSlots; }
        public void markSessionSearched(int slot) { sessionSearchedSlots.add(slot); }
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;

        PersistentDataContainer pdc = container.getPersistentDataContainer();
        if (!pdc.has(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING)) return;

        event.setCancelled(true);
        // 右クリックは手ごとに (メイン・オフハンド) 来る。2回開くと漁るセッションが開き直され、
        // 間に挟まる後片付けがバックパックの表示を消してしまうので、メインハンドの時だけ開く
        if (event.getHand() != EquipmentSlot.HAND) return;
        open(event.getPlayer(), container);
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;

        PersistentDataContainer pdc = mannequin.getPersistentDataContainer();
        if (!pdc.has(PDCKeys.CORPSE_INVENTORY, PDCKeys.BYTE_ARRAY) && !pdc.has(PDCKeys.CORPSE_INVENTORY, PDCKeys.STRING)) return;

        event.setCancelled(true);
        // マネキンへの右クリックはメインハンドの後にオフハンドでも来る (上と同じ理由で、メインハンドの時だけ開く)
        if (event.getHand() != EquipmentSlot.HAND) return;
        open(event.getPlayer(), mannequin);
    }

    /**
     * レイド中は、ルートテーブルが設定されたコンテナ (漁るコンテナ) 以外を開けないようにする。
     * 漁るコンテナは {@link #onInteract} が横取りして SearchGUI を開くので、ここには来ない。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Container container)) return;
        if (container.getPersistentDataContainer().has(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING)) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule raidModule = plugin.getRaidModule();
        if (raidModule == null || !raidModule.isInRaid(player)) return;

        event.setCancelled(true);
        player.sendActionBar(Component.text("レイド中はこのコンテナは漁れません", NamedTextColor.RED));
    }

    /**
     * 開いている死体の中身 (死体ごとに 1 つを、見ている人みんなで使う)。人ごとに読んだ写しを持つと、2 人が同じ物を取れてしまい、
     * 後から書き戻した人の写しで取られた物が戻ってしまう。見ている人がいなくなったら捨てる (中身は取るたびに死体へ書いてある)
     */
    private final Map<UUID, Inventory> openCorpses = new HashMap<>();
    private final Map<UUID, Integer> corpseViewers = new HashMap<>();

    public void open(Player player, Object source) {
        Inventory realInv = getInventoryFromSource(source);
        if (realInv == null) return;
        if (source instanceof Mannequin m) {
            openCorpses.putIfAbsent(m.getUniqueId(), realInv);
            corpseViewers.merge(m.getUniqueId(), 1, Integer::sum);
        }

        SearchHolder holder = new SearchHolder(source, realInv);
        PersistentDataContainer pdc = getPDCFromSource(source);
        if (pdc != null) {
            holder.getSessionSearchedSlots().addAll(getSearchedSlots(pdc));
        }

        Inventory guiInv = Bukkit.createInventory(holder, realInv.getSize(), title(realInv.getSize() / 9, null));
        updateInventory(guiInv, source);
        player.openInventory(guiInv);
        Bukkit.getPluginManager().callEvent(new LootSessionStartEvent(player, detectSourceType(source), source, realInv));
    }

    /** 漁る画面のタイトル (下の欄は bag が null ならいつもの「インベントリ」、あればバックパックの表示) */
    public static Component title(int rows, com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.Bag bag) {
        return com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.title("Searching...", rows, bag);
    }

    private Container getLiveContainer(Object source) {
        if (!(source instanceof Container container)) return null;
        Block block = container.getBlock();
        if (block.getState() instanceof Container liveContainer) {
            return liveContainer;
        }
        return container;
    }

    private Inventory getInventoryFromSource(Object source) {
        if (source instanceof Container) {
            Container live = getLiveContainer(source);
            return live != null ? live.getInventory() : null;
        }
        if (source instanceof Mannequin m) {
            Inventory open = openCorpses.get(m.getUniqueId());
            if (open != null) return open;
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            Component title = m.customName();
            if (title == null) title = Component.text("死体漁り");

            try {
                // 1. BYTE_ARRAY (新形式)
                if (pdc.has(PDCKeys.CORPSE_INVENTORY, PDCKeys.BYTE_ARRAY)) {
                    byte[] bytes = pdc.get(PDCKeys.CORPSE_INVENTORY, PDCKeys.BYTE_ARRAY);
                    if (bytes != null) {
                        return com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil.deserializeInventoryFromBytes(bytes, title);
                    }
                }
                // 2. STRING (旧形式)
                else if (pdc.has(PDCKeys.CORPSE_INVENTORY, PDCKeys.STRING)) {
                    String data = pdc.get(PDCKeys.CORPSE_INVENTORY, PDCKeys.STRING);
                    if (data != null) {
                        return CorpseManager.deserializeInventory(data, title);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize corpse inventory!");
            }
        }
        return null;
    }

    private PersistentDataContainer getPDCFromSource(Object source) {
        if (source instanceof Container) {
            Container live = getLiveContainer(source);
            return live != null ? live.getPersistentDataContainer() : null;
        }
        if (source instanceof Entity e) return e.getPersistentDataContainer();
        return null;
    }

    private void updateInventory(Inventory guiInv, Object source) {
        SearchHolder holder = (guiInv.getHolder() instanceof SearchHolder h) ? h : null;
        Inventory realInv = holder != null ? holder.getLiveInventory() : getInventoryFromSource(source);
        PersistentDataContainer pdc = getPDCFromSource(source);
        if (realInv == null) return;

        Set<Integer> searchedSlots = new HashSet<>();
        if (holder != null) {
            searchedSlots.addAll(holder.getSessionSearchedSlots());
        }
        if (pdc != null) {
            searchedSlots.addAll(getSearchedSlots(pdc));
        }

        for (int i = 0; i < realInv.getSize(); i++) {
            ItemStack realItem = realInv.getItem(i);
            if (realItem == null || realItem.getType() == Material.AIR) {
                guiInv.setItem(i, null);
                continue;
            }

            if (holder != null && holder.searchingSlots.contains(i)) {
                guiInv.setItem(i, searchIcon(holder.searchFrames.getOrDefault(i, 0)));
            } else if (searchedSlots.contains(i)) {
                guiInv.setItem(i, realItem.clone());
            } else {
                guiInv.setItem(i, createUnknownIcon());
            }
        }
    }

    private ItemStack createUnknownIcon() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("iem", "search/unknown"));
        meta.displayName(Component.text("??? (未確認)", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack searchIcon(int frame) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("iem", "search/spin_" + (frame % 8)));
        meta.displayName(Component.text("サーチ中...", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    /** バックパック表示の切替と衝撃波で、同じタイトルを組み立てる。 */
    public static void refreshTitle(Player player, com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.Bag bag) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof SearchHolder holder)) return;
        holder.bag = bag;
        var line = new com.lunar_prototype.impossbleEscapeMC.gui.DialogLine(
                net.kyori.adventure.key.Key.key("iem:gui"), '\uE040', '\uE050');
        line.text("Searching...", net.kyori.adventure.text.format.TextColor.color(0x404040));
        com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel.append(line, top.getSize() / 9, bag);
        for (var entry : holder.waves.entrySet()) {
            int slot = entry.getKey();
            int rarity = entry.getValue()[0], frame = entry.getValue()[1];
            int rgb = switch (rarity) {
                case 2 -> 0x55FF55;
                case 3 -> 0x5555FF;
                case 4 -> 0xB954FF;
                case 5 -> 0xFFAA00;
                default -> 0xFFFFFF;
            };
            line.moveTo((slot % 9) * 18 - 24).glyph(net.kyori.adventure.key.Key.key("iem:search"),
                    (char) (0xE000 + (slot / 9) * 6 + frame), 64,
                    net.kyori.adventure.text.format.TextColor.color(rgb), null, null);
        }
        com.lunar_prototype.impossbleEscapeMC.gui.OpenTitle.set(player,
                line.build().shadowColor(net.kyori.adventure.text.format.ShadowColor.none()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SearchHolder holder)) return;

        int topSize = top.getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot < 0 || rawSlot >= topSize) {
            InventoryAction action = event.getAction();
            ClickType click = event.getClick();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR
                    || click == ClickType.NUMBER_KEY
                    || click == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = rawSlot;
        Object source = holder.getSource();
        PersistentDataContainer pdc = getPDCFromSource(source);
        if (pdc == null) return;

        Set<Integer> searchedSlots = new HashSet<>(holder.getSessionSearchedSlots());
        searchedSlots.addAll(getSearchedSlots(pdc));

        if (searchedSlots.contains(slot)) {
            Inventory srcInv = holder.getLiveInventory();
            if (srcInv == null) return;
            ItemStack sourceItem = srcInv.getItem(slot);
            if (sourceItem == null || sourceItem.getType() == Material.AIR) return;

            ItemStack toMove = sourceItem.clone();
            ItemFactory.applyFIR(toMove); // FIRを適用
            // バックパックなどのコンテナ内アイテムにも適用
            com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule backpackModule = plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule.class);
            if (backpackModule != null && backpackModule.isBackpackItem(toMove)) {
                Inventory inv = backpackModule.loadBackpackInventory(toMove);
                for (ItemStack content : inv.getContents()) {
                    if (content != null && !content.getType().isAir()) {
                        ItemFactory.applyFIR(content);
                    }
                }
                backpackModule.saveBackpackInventory(toMove, inv);
            }

            // 装備しているバックパックに先に入れ、入りきらない分を持ち物に入れる。
            // バックパックの表示中は持ち物の欄がバックパックの表示なので、本物のホットバーにだけ入れ、残りは死体・コンテナに残す
            com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener overlay =
                    plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.loot.LootBackpackOverlayListener.class);
            ItemStack rest = overlay != null ? overlay.addToBackpackFirst(player, toMove) : toMove;
            Map<Integer, ItemStack> leftover;
            if (rest == null) {
                leftover = Map.of();
            } else if (overlay != null && overlay.isBackpackView(player)) {
                ItemStack notTaken = overlay.addToHotbar(player, rest);
                leftover = notTaken == null ? Map.of() : Map.of(0, notTaken);
                if (notTaken != null) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "バックパックがいっぱいです。コンパスで通常インベントリの表示に切り替えると、持ち物に入れられます",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            } else {
                leftover = player.getInventory().addItem(rest);
            }
            int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int movedAmount = sourceItem.getAmount() - leftoverAmount;
            if (movedAmount <= 0) return;

            int remain = sourceItem.getAmount() - movedAmount;
            if (remain <= 0) {
                srcInv.setItem(slot, null);
            } else {
                ItemStack updated = sourceItem.clone();
                updated.setAmount(remain);
                srcInv.setItem(slot, updated);
            }

            if (source instanceof Container) {
                Container live = getLiveContainer(source);
                if (live != null) {
                    live.update(true, false);
                }
            } else if (source instanceof Mannequin m) {
                try {
                    byte[] bytes = com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil.serializeInventoryToBytes(srcInv);
                    m.getPersistentDataContainer().set(PDCKeys.CORPSE_INVENTORY, PDCKeys.BYTE_ARRAY, bytes);
                    CorpseManager.updateMannequinAppearance(m, srcInv);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save updated corpse inventory!");
                }
            }

            updateInventory(top, source);
        } else {
            startSearching(player, top, holder, source, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SearchHolder)) return;

        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SearchHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (holder.getSource() instanceof Mannequin m) {
            Integer left = corpseViewers.computeIfPresent(m.getUniqueId(), (id, n) -> n > 1 ? n - 1 : null);
            if (left == null) openCorpses.remove(m.getUniqueId());
        }

        Bukkit.getPluginManager().callEvent(new LootSessionEndEvent(
                player,
                detectSourceType(holder.getSource()),
                holder.getSource(),
                holder.getLiveInventory(),
                LootSessionEndReason.CLOSED
        ));
    }

    private void startSearching(Player player, Inventory guiInv, SearchHolder holder, Object source, int slot) {
        Inventory realInv = holder.getLiveInventory();
        if (realInv == null) return;
        if (!holder.getSearchingSlots().add(slot)) return;

        ItemStack current = realInv.getItem(slot);
        if (current == null || current.getType() == Material.AIR) {
            // 実体が空なら未確認表示を残さず即時確定する
            markAsSearched(holder, source, slot);
            updateInventory(guiInv, source);
            holder.getSearchingSlots().remove(slot);
            return;
        }

        // 1 マス 10 tick (スキルで縮む)。1 tick ごとに進める
        final int searchTicks = Math.max(1, SEARCH_TICKS + (int) SkillModule.value(player, SkillStat.SEARCH_TICKS));
        new BukkitRunnable() {
            int elapsed = 0;
            int flashFrame = -1;

            @Override
            public void run() {
                if (!player.getOpenInventory().getTopInventory().equals(guiInv)) {
                    holder.getSearchingSlots().remove(slot);
                    holder.searchFrames.remove(slot);
                    holder.waves.remove(slot);
                    this.cancel();
                    return;
                }

                if (flashFrame >= 0) {
                    if (++flashFrame >= 6) {
                        holder.waves.remove(slot);
                        refreshTitle(player, holder.bag);
                        cancel();
                    } else {
                        holder.waves.get(slot)[1] = flashFrame;
                        refreshTitle(player, holder.bag);
                    }
                } else if (elapsed < searchTicks) {
                    holder.searchFrames.put(slot, elapsed % 8);
                    guiInv.setItem(slot, searchIcon(elapsed));
                    elapsed++;
                } else {
                    ItemStack revealed = realInv.getItem(slot);
                    holder.getSearchingSlots().remove(slot);
                    holder.searchFrames.remove(slot);
                    markAsSearched(holder, source, slot);
                    updateInventory(guiInv, source);
                    if (revealed != null && !revealed.getType().isAir()) {
                        int rarity = revealRarity(revealed);
                        holder.waves.put(slot, new int[]{rarity, 0});
                        flashFrame = 0;
                        refreshTitle(player, holder.bag);
                        player.playSound(player, "iem:loot.reveal." + rarity, SoundCategory.MASTER,
                                switch (rarity) {
                                    case 3 -> 0.7f;
                                    case 4 -> 0.9f;
                                    case 5 -> 1.0f;
                                    default -> 0.5f;
                                }, 1.0f);
                    }
                    holder.getSearchingSlots().remove(slot);
                    if (flashFrame < 0) this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private static int revealRarity(ItemStack item) {
        if (!item.hasItemMeta()) return 1;
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (id == null) return 1;
        var def = ItemRegistry.get(id);
        if (def != null) return Math.clamp(def.rarity, 1, 5);
        var ammo = ItemRegistry.getAmmo(id);
        if (ammo != null) return Math.clamp(ammo.rarity, 1, 5);
        var attachment = ItemRegistry.getAttachment(id);
        if (attachment != null) return Math.clamp(attachment.rarity, 1, 5);
        return 1;
    }

    private Set<Integer> getSearchedSlots(PersistentDataContainer pdc) {
        String data = pdc.get(SEARCHED_SLOTS_KEY, PersistentDataType.STRING);
        Set<Integer> slots = new HashSet<>();
        if (data != null && !data.isEmpty()) {
            for (String s : data.split(",")) {
                try { slots.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        }
        return slots;
    }

    private void markAsSearched(SearchHolder holder, Object source, int slot) {
        holder.markSessionSearched(slot);

        PersistentDataContainer pdc = getPDCFromSource(source);
        if (pdc == null) return;

        Set<Integer> slots = getSearchedSlots(pdc);
        slots.add(slot);
        StringBuilder sb = new StringBuilder();
        for (int s : slots) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        pdc.set(SEARCHED_SLOTS_KEY, PersistentDataType.STRING, sb.toString());

        if (source instanceof Container) {
            Container live = getLiveContainer(source);
            if (live != null) {
                live.update(true, false);
            }
        }
    }

    private LootSessionSourceType detectSourceType(Object source) {
        if (source instanceof Container) {
            return LootSessionSourceType.CRATE;
        }
        if (source instanceof Mannequin) {
            return LootSessionSourceType.CORPSE;
        }
        return LootSessionSourceType.UNKNOWN;
    }
}
