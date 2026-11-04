package com.lunar_prototype.impossbleEscapeMC.gui;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 表示したホームからの操作だけを一度受け付け、実行前に条件を読み直す。メインスレッド専用。 */
final class PdaHomeController {
    enum Page {HOME, HIDEOUT}
    private record Session(String token, Page page) {}
    interface Port {
        PdaHomeModel.View snapshot(Player player);
        void show(Player player, String token, PdaHomeModel.View view, Page page);
        void unavailable(Player player);
        void denied(Player player, String reason);
        void close(Player player);
        void dispatch(Player player, PdaHomeModel.Action action, PdaHomeModel.View view);
    }
    private final Port port;
    private final Map<UUID,Session> sessions = new HashMap<>();
    PdaHomeController(Port port) {this.port = port;}

    void open(Player player) {open(player, Page.HOME);}

    private void open(Player player, Page page) {
        if (!player.isOnline() || player.isDead()) return;
        PdaHomeModel.View view = port.snapshot(player);
        if (view == null) {sessions.remove(player.getUniqueId());port.close(player);port.unavailable(player);return;}
        String token = UUID.randomUUID().toString();
        sessions.put(player.getUniqueId(), new Session(token, page));
        port.show(player, token, view, page);
    }

    void handle(Player player, String token, PdaHomeModel.Action action) {
        if (action == null || !player.isOnline() || player.isDead()) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.token().equals(token)) return;
        boolean allowed = session.page() == Page.HIDEOUT
                ? action == PdaHomeModel.Action.HIDEOUT || action == PdaHomeModel.Action.FACILITIES || action == PdaHomeModel.Action.BACK
                : action != PdaHomeModel.Action.HIDEOUT && action != PdaHomeModel.Action.FACILITIES && action != PdaHomeModel.Action.BACK;
        if (!allowed || !sessions.remove(player.getUniqueId(), session)) return;
        if (action == PdaHomeModel.Action.BACK) {open(player);return;}
        if (action == PdaHomeModel.Action.CLOSE) {port.close(player);return;}
        if (action == PdaHomeModel.Action.REFRESH) {open(player);return;}
        PdaHomeModel.View current = port.snapshot(player);
        if (current == null) {port.close(player);port.unavailable(player);return;}
        String reason = PdaHomeModel.blocked(current, action);
        if (reason != null) {port.denied(player, reason);open(player, session.page());return;}
        if (action == PdaHomeModel.Action.HIDEOUT_MENU) {open(player, Page.HIDEOUT);return;}
        port.close(player);
        port.dispatch(player, action, current);
    }

    void forget(UUID player) {sessions.remove(player);}
    void clear() {sessions.clear();}
}
