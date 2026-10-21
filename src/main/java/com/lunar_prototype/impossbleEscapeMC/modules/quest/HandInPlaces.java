package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * 納品に使う物のある場所 (トレーダーの納品の画面 {@link QuestHandInDialog} と、レイド結果の自動の納品 {@link QuestAutoHandIn} が使う。
 * トレーダーの修理の画面も、着ている防具・オフハンドを足しスタッシュを除いて使う: {@link #scan(Player, PlayerData, boolean, boolean)})。
 * <ul>
 *   <li>並びの順: 持ち物 (ホットバーと持ち物の欄)、持ち物のバックパックの中、オフハンドのバックパックの中、スタッシュのページ順
 *       (各ページの欄、続いてそのページのバックパックの中)</li>
 *   <li>着ている防具・オフハンドの物そのものは数えない。中に物が入っているバックパックそのものも数えない (納品すると中身ごと消える)。
 *       バックパックの中のバックパックは見ない</li>
 * </ul>
 * 読み書きは {@link Store}: スタッシュのページとバッグの中身は読んだ物を使い回し、{@link Store#save} でバッグ・ページへ書き戻す
 */
public final class HandInPlaces {

    /** 物のある場所の種類 */
    public enum Kind { INVENTORY, OFFHAND_BAG, STASH }

    /**
     * 1 つの物 (スタック) の場所。kind の欄 slot (スタッシュはページ page の欄)。inner が 0 以上なら、その欄のバックパックの中の欄。
     * オフハンドのバックパックの中は OFFHAND_BAG (slot は使わない)。着ている防具・オフハンドの物は INVENTORY の欄 36..40
     */
    public record Source(Kind kind, int page, int slot, int inner) {
        boolean inBag() {
            return kind == Kind.OFFHAND_BAG || inner >= 0;
        }
    }

    /** 物 1 つと、その場所・場所の名前 (画面に出す) */
    public record Entry(Source source, String place, ItemStack item) {
    }

    private HandInPlaces() {
    }

    /** 着ている防具・オフハンドの欄 (PlayerInventory の番号) と、場所の名前 */
    private static final int[] EQUIPPED_SLOTS = {39, 38, 37, 36, 40};
    private static final String[] EQUIPPED_PLACES = {"装備中 (頭)", "装備中 (胴)", "装備中 (脚)", "装備中 (足)", "オフハンド"};

    /** 並びの順に、空でない物を集める (物は読んだ時の写し。納品: 着ている物は除き、スタッシュは含める) */
    static List<Entry> scan(Player player, PlayerData data) {
        return scan(player, data, false, true);
    }

    /**
     * 並びの順に、空でない物を集める。equipped なら最初に着ている防具・オフハンドの物 (バックパックは除く)、
     * stash ならスタッシュのページも
     */
    public static List<Entry> scan(Player player, PlayerData data, boolean equipped, boolean stash) {
        BackpackModule backpacks = backpackModule();
        List<Entry> out = new ArrayList<>();
        List<Entry> bagged = new ArrayList<>();
        if (equipped) {
            for (int i = 0; i < EQUIPPED_SLOTS.length; i++) {
                ItemStack item = player.getInventory().getItem(EQUIPPED_SLOTS[i]);
                if (!empty(item) && !isBag(backpacks, item)) {
                    out.add(new Entry(new Source(Kind.INVENTORY, 0, EQUIPPED_SLOTS[i], -1), EQUIPPED_PLACES[i], item.clone()));
                }
            }
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (empty(item)) continue;
            add(out, backpacks, new Source(Kind.INVENTORY, 0, slot, -1), "持ち物", item);
            if (isBag(backpacks, item)) {
                Inventory bag = backpacks.loadBackpackInventory(item);
                for (int inner = 0; inner < bag.getSize(); inner++) {
                    add(bagged, backpacks, new Source(Kind.INVENTORY, 0, slot, inner), "持ち物の " + plain(item), bag.getItem(inner));
                }
            }
        }
        out.addAll(bagged);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isBag(backpacks, offhand)) {
            Inventory bag = backpacks.loadBackpackInventory(offhand);
            for (int inner = 0; inner < bag.getSize(); inner++) {
                add(out, backpacks, new Source(Kind.OFFHAND_BAG, 0, -1, inner), plain(offhand) + " (オフハンド)", bag.getItem(inner));
            }
        }
        if (!stash) return out;
        for (Map.Entry<Integer, Inventory> page : stashPages(data).entrySet()) {
            ItemStack[] items = page.getValue().getContents();
            bagged.clear();
            for (int slot = 0; slot < items.length; slot++) {
                ItemStack item = items[slot];
                if (empty(item)) continue;
                add(out, backpacks, new Source(Kind.STASH, page.getKey(), slot, -1), "スタッシュ " + page.getKey(), item);
                if (isBag(backpacks, item)) {
                    Inventory bag = backpacks.loadBackpackInventory(item);
                    for (int inner = 0; inner < bag.getSize(); inner++) {
                        add(bagged, backpacks, new Source(Kind.STASH, page.getKey(), slot, inner),
                                "スタッシュ " + page.getKey() + " の " + plain(item), bag.getItem(inner));
                    }
                }
            }
            out.addAll(bagged);
        }
        return out;
    }

    private static void add(List<Entry> out, BackpackModule backpacks, Source source, String place, ItemStack item) {
        if (empty(item)) return;
        // 中に物が入っているバックパックそのものは使わない (中身ごと消えてしまう)。中の物はそれぞれ候補になる
        if (isBag(backpacks, item) && !backpacks.loadBackpackInventory(item).isEmpty()) return;
        out.add(new Entry(source, place, item.clone()));
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean isBag(BackpackModule backpacks, ItemStack item) {
        return backpacks != null && backpacks.isBackpackItem(item);
    }

    static Map<Integer, Inventory> stashPages(PlayerData data) {
        Map<Integer, Inventory> pages = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> page : data.getStashPages().entrySet()) {
            if (page.getValue() == null) continue;
            try {
                pages.put(page.getKey(), SerializationUtil.deserializeInventory(page.getValue(), Component.empty()));
            } catch (Exception e) {
                ImpossbleEscapeMC.getInstance().getLogger().log(Level.WARNING, "スタッシュのページを読めませんでした: " + page.getKey(), e);
            }
        }
        return pages;
    }

    /** 物を読み直して書き戻すための入れ物 (スタッシュのページとバッグの中身は、読んだ物を使い回して最後に書く) */
    public static final class Store {
        private final Player player;
        private final PlayerData data;
        private final BackpackModule backpacks;
        private final Map<Integer, Inventory> stash;
        private final Set<Integer> changedPages = new HashSet<>();
        /** バッグ (持っている場所の Source、inner = -1) → 中身 */
        private final Map<Source, Inventory> bags = new LinkedHashMap<>();

        public Store(Player player, PlayerData data) {
            this.player = player;
            this.data = data;
            this.backpacks = backpackModule();
            this.stash = stashPages(data);
        }

        private ItemStack bagItem(Source bag) {
            if (bag.kind() == Kind.OFFHAND_BAG) return player.getInventory().getItemInOffHand();
            if (bag.kind() == Kind.INVENTORY) return player.getInventory().getItem(bag.slot());
            Inventory page = stash.get(bag.page());
            return page != null ? page.getItem(bag.slot()) : null;
        }

        private Inventory bag(Source source) {
            Source bag = new Source(source.kind(), source.page(), source.slot(), -1);
            return bags.computeIfAbsent(bag, b -> {
                ItemStack item = bagItem(b);
                return isBag(backpacks, item) ? backpacks.loadBackpackInventory(item) : null;
            });
        }

        public ItemStack get(Source source) {
            if (source.inBag()) {
                Inventory bag = bag(source);
                return bag != null ? bag.getItem(source.inner()) : null;
            }
            if (source.kind() == Kind.INVENTORY) return player.getInventory().getItem(source.slot());
            Inventory page = stash.get(source.page());
            return page != null ? page.getItem(source.slot()) : null;
        }

        public void set(Source source, ItemStack item) {
            if (source.inBag()) {
                Inventory bag = bag(source);
                if (bag != null) bag.setItem(source.inner(), item);
            } else if (source.kind() == Kind.INVENTORY) {
                player.getInventory().setItem(source.slot(), item);
            } else {
                Inventory page = stash.get(source.page());
                if (page != null) page.setItem(source.slot(), item);
            }
            if (source.kind() == Kind.STASH) changedPages.add(source.page());
        }

        /** 変えたバッグの中身をバッグに書き、バッグとスタッシュのページを戻す */
        public void save() {
            for (Map.Entry<Source, Inventory> entry : bags.entrySet()) {
                Source bag = entry.getKey();
                ItemStack item = bagItem(bag);
                if (entry.getValue() == null || item == null) continue;
                backpacks.saveBackpackInventory(item, entry.getValue());
                if (bag.kind() == Kind.OFFHAND_BAG) {
                    player.getInventory().setItemInOffHand(item);
                } else if (bag.kind() == Kind.INVENTORY) {
                    player.getInventory().setItem(bag.slot(), item);
                } else {
                    stash.get(bag.page()).setItem(bag.slot(), item);
                }
            }
            for (int page : changedPages) {
                try {
                    data.setStashPage(page, SerializationUtil.serializeInventory(stash.get(page)));
                } catch (Exception e) {
                    ImpossbleEscapeMC.getInstance().getLogger().log(Level.WARNING, "スタッシュのページを書けませんでした: " + page, e);
                }
            }
        }
    }

    public static String plain(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
    }

    private static BackpackModule backpackModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(BackpackModule.class);
    }
}
