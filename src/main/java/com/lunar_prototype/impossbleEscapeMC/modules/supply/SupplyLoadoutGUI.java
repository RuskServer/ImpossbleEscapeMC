package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI;
import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.*;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.*;

/** 無料装備の選択と全内容を表示するダイアログ。受取条件・装備の生成は SupplyLoadout に任せる。 */
public final class SupplyLoadoutGUI implements Listener {
    private static final int BODY_W = 340;
    private static final String PREFIX = "supply/";
    private static final Map<UUID, SupplyLoadoutGUI> ACTIVE = new HashMap<>();

    /** 選択前の受取パケットや二重受取を無効にする、画面ごとの操作番号。 */
    static final class Selection {
        private final String id = UUID.randomUUID().toString();
        private int revision;
        private boolean finished;
        private SupplyLoadout.Weapon weapon = SupplyLoadout.Weapon.SHOTGUN;

        String key(String action) {return PREFIX + id + "/" + revision + "/" + action;}
        boolean accepts(String action) {return !finished && action.startsWith(PREFIX + id + "/" + revision + "/");}
        SupplyLoadout.Weapon weapon() {return weapon;}
        void select(SupplyLoadout.Weapon selected) {if (!finished) {weapon = selected; revision++;}}
        void refresh() {revision++;}
        boolean finish() {
            if (finished) return false;
            finished = true;
            return true;
        }
    }

    private final Player player;
    private final PlayerDataModule dataModule;
    private final Runnable onReturn;
    private final Selection selection = new Selection();
    private String notice;

    public SupplyLoadoutGUI(Player player) {this(player, null);}

    /** リザルトなど、呼び出し元の画面に戻る場合。Esc も戻るボタンと同じ。 */
    public SupplyLoadoutGUI(Player player, Runnable onReturn) {
        this.player = player;
        this.onReturn = onReturn;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
    }

    public void open() {
        if (!available()) return;
        SupplyLoadoutGUI previous = ACTIVE.put(player.getUniqueId(), this);
        if (previous != null) previous.dispose();
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        show();
    }

    private PlayerData data() {
        return dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
    }

    private boolean available() {
        RaidModule raids = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
        return player.isOnline() && !player.isDead() && (raids == null || !raids.isInRaid(player));
    }

    private void show() {
        var status = FreeLoadoutPresentation.capture(player);
        SupplyLoadout.Weapon weapon = selection.weapon();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(FreeLoadoutPresentation.buttonLabel(status), BODY_W));
        body.add(DialogBody.plainMessage(Component.text("武器を選択", NOTE), BODY_W));
        DialogLine tabs = line();
        for (SupplyLoadout.Weapon candidate : SupplyLoadout.Weapon.values()) {
            boolean selected = candidate == weapon;
            String label = candidate.label;
            String action = "weapon/" + candidate.name().toLowerCase(Locale.ROOT);
            tabs.box(TRADER_FONT, boxGlyph(label, selected ? BOX_TAB_ON : BOX_TAB), boxArtWidth(label),
                    label, selected ? DARK : AMBER, selected, ClickEvent.custom(key(action), null),
                    Component.text(candidate.description, NOTE)).shift(5);
        }
        body.add(DialogBody.plainMessage(tabs.moveTo(BODY_W - 8).build(), BODY_W));
        body.add(DialogBody.plainMessage(Component.text(weapon.description + " / 弾は貫通クラス " + SupplyLoadout.AMMO_CLASS, NOTE), BODY_W));

        List<ItemStack> contents = SupplyLoadout.contents(player.getWorld(), weapon, data());
        for (ItemStack item : contents) {
            Component name = item.getItemMeta().displayName();
            if (name == null) name = Component.translatable(item.getType().translationKey());
            // 表示名を独立した札に載せる。実物の詳細はアイコンのツールチップで読める。
            Component description = Component.join(JoinConfiguration.newlines(),
                    itemLines(PlainTextComponentSerializer.plainText().serialize(name), item.getAmount())
                            .stream().map(DialogLine::build).toList());
            body.add(DialogBody.item(item).description(DialogBody.plainMessage(description, ROW_BODY_W))
                    .showTooltip(true).showDecorations(true).width(ITEM_BOX).height(ITEM_BOX).build());
        }
        if (contents.isEmpty()) body.add(DialogBody.plainMessage(Component.text("装備を読み込めませんでした", NG), BODY_W));
        body.add(DialogBody.plainMessage(Component.text(notice != null ? notice : status.reason() == null
                ? "持ち物と装備が空です。このセットを受け取れます" : status.reason(),
                notice != null ? NG : status.state() == FreeLoadoutPresentation.State.READY ? OK : AMBER), BODY_W));
        body.add(DialogBody.plainMessage(Component.text("受取後 " + status.interval() / 60000
                + " 分待ち / 支給品は売値 0₽・納品不可", DIM), BODY_W));

        ActionButton receive = ActionButton.create(Component.text(status.reason() == null ? "この装備を受け取る" : "受取条件を再確認", status.reason() == null ? OK : DIM),
                status.reason() == null ? null : Component.text(status.reason(), NOTE), 150,
                action("receive"));
        ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("back"));
        DialogBase base = DialogBase.builder(Component.text(FreeLoadoutPresentation.TITLE))
                .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(List.of(receive, back)).columns(2).exitAction(back).build())));
    }

    /** トレーダーやリザルトと同じ品物の札。名前が長い時も数量を隠さない。 */
    static List<DialogLine> itemLines(String name, int amount) {
        DialogLine first = line();
        for (int i = 0; i < PLATE_TILES; i++)
            first.stamp(TRADER_FONT, (char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        first.stamp(TRADER_FONT, RING, RING_W, RING_X);
        first.moveTo(TEXT_X).text(FontWidths.truncate(name, ROW_LINE_W - TEXT_X - 6), NAME).moveTo(ROW_LINE_W);
        DialogLine second = line().moveTo(TEXT_X).text("数量 " + amount, AMBER).moveTo(ROW_LINE_W);
        return List.of(first, second);
    }

    private Key key(String action) {return Key.key("iem", selection.key(action));}
    private DialogAction action(String action) {return DialogAction.customClick(key(action), null);}

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().namespace().equals("iem") || !event.getIdentifier().value().startsWith(PREFIX)) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)
                || !connection.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        String action = event.getIdentifier().value();
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> handle(action));
    }

    private void handle(String key) {
        if (ACTIVE.get(player.getUniqueId()) != this || !selection.accepts(key)) return;
        if (!available()) {dispose(); return;}
        String action = key.substring(selection.key("").length());
        if (action.equals("back")) {
            returnToCaller();
        } else if (action.startsWith("weapon/")) {
            SupplyLoadout.Weapon chosen;
            try {chosen = SupplyLoadout.Weapon.valueOf(action.substring("weapon/".length()).toUpperCase(Locale.ROOT));}
            catch (IllegalArgumentException ignored) {return;}
            selection.select(chosen);
            notice = null;
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            show();
        } else if (action.equals("receive")) {
            // 画面表示時の状態に頼らず、最新の待ち時間・持ち物・レイド状態で受け取る。
            String reason = SupplyLoadout.give(player, data(), selection.weapon());
            if (reason != null) {
                selection.refresh();
                notice = reason;
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                show();
                return;
            }
            if (dataModule != null) dataModule.saveAsync(player.getUniqueId());
            player.playSound(player, Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f);
            player.sendMessage(Component.text("無料装備 (" + selection.weapon().label + ") を受け取りました", OK));
            returnToCaller();
        }
    }

    private void returnToCaller() {
        if (!selection.finish()) return;
        dispose();
        player.closeDialog();
        if (onReturn != null) onReturn.run();
        else {
            RaidModule raids = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
            if (raids != null) new RaidSelectionGUI(raids).open(player);
        }
    }

    /** プラグイン停止時に、画面とプレイヤー参照を残さない。 */
    public static void shutdown() {
        for (SupplyLoadoutGUI gui : List.copyOf(ACTIVE.values())) {
            gui.dispose();
            if (gui.player.isOnline()) gui.player.closeDialog();
        }
    }

    private void dispose() {
        selection.finish();
        ACTIVE.remove(player.getUniqueId(), this);
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) dispose();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity().getUniqueId().equals(player.getUniqueId())) dispose();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) dispose();
    }
}
