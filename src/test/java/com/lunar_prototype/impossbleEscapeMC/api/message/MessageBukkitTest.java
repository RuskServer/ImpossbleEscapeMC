package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Bukkit実装のイベント登録・本文変更・受信者言語・標準broadcast互換を確認する。 */
class MessageBukkitTest {
    @Test void realBukkitDispatch() throws Exception {
        var server = MockBukkit.mock();
        MessageService service = null;
        try {
            var plugin = MockBukkit.createMockPlugin();
            var player = server.addPlayer();
            player.setOp(true);
            player.setLocale(Locale.US);
            var platform = new BukkitMessagePlatform(plugin);
            service = new MessageService(MessageCatalog.load(null, "ja_jp"), platform, false);
            List<IemMessageDispatchEvent> results = new ArrayList<>();
            AtomicBoolean cancel = new AtomicBoolean();
            Listener listener = new Listener() {};
            server.getPluginManager().registerEvent(IemMessageSendEvent.class, listener, EventPriority.NORMAL,
                    (l, event) -> {
                        var message = (IemMessageSendEvent) event;
                        assertFalse(message.isAsynchronous());
                        message.setMessage(MessageText.of("iem.admin.messages.reloaded"));
                    }, plugin);
            server.getPluginManager().registerEvent(IemMessageDispatchEvent.class, listener, EventPriority.NORMAL,
                    (l, event) -> results.add((IemMessageDispatchEvent) event), plugin);
            server.getPluginManager().registerEvent(BroadcastMessageEvent.class, listener, EventPriority.NORMAL,
                    (l, event) -> {
                        var broadcast = (BroadcastMessageEvent) event;
                        broadcast.setCancelled(cancel.get());
                        broadcast.getRecipients().clear();
                        broadcast.getRecipients().add(player);
                    }, plugin);
            var origin = new MessageOrigin("test", "send", "Test.java", 1);
            String key = "iem.admin.messages.usage";
            service.send(player, key, MessageContent.text(Component.text("original")), origin).toCompletableFuture().join();
            assertEquals("Message translations reloaded.", MessageCatalogTest.plain(player.nextComponentMessage()));
            assertEquals("original", MessageCatalogTest.plain(results.getLast().getOriginalContent().text()));
            service.send(server.getConsoleSender(), key, MessageContent.text(Component.text("original")), origin).toCompletableFuture().join();
            assertEquals(MessageChannel.CONSOLE, results.getLast().getDefinition().channel());
            assertTrue(MessageCatalogTest.plain(results.getLast().getRenderedContent().text()).contains("再読み込み"));
            cancel.set(true);
            var blocked = service.broadcast(key, MessageContent.text(Component.text("broadcast")), origin).toCompletableFuture().join();
            assertEquals(1, blocked.size());
            assertEquals(MessageResult.Status.CANCELLED, blocked.getFirst().status());
            assertNull(player.nextComponentMessage());
        } finally {
            if (service != null) service.close();
            MockBukkit.unmock();
        }
    }
}
