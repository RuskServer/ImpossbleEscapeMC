package com.lunar_prototype.impossbleEscapeMC.api.message;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 辞書の検証と差し替え。失敗した場合は直前の辞書を維持する。 */
@Command(name="iemmessages",description="メッセージ辞書を検証・再読み込みします。",permission="iem.messages.admin")
public final class MessageCommand implements BasicCommand {
    private final ImpossbleEscapeMC plugin;
    private final AtomicBoolean reloading = new AtomicBoolean();
    public MessageCommand(ImpossbleEscapeMC plugin){this.plugin=plugin;}
    @Override public void execute(CommandSourceStack stack,String[] args){
        if(args.length!=1||!args[0].equalsIgnoreCase("reload")) {
            Messages.chat(stack.getSender(),"iem.admin.messages.usage",MessageText.of("iem.admin.messages.usage"));return;
        }
        if (!reloading.compareAndSet(false, true)) {
            Messages.chat(stack.getSender(), "iem.admin.messages.busy", MessageText.of("iem.admin.messages.busy"));
            return;
        }
        // ファイルI/Oと検証は非同期。差し替え・返信だけを主スレッドで行う。
        var recipient = stack.getSender();
        var current = Messages.service();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                MessageCatalog replacement = null;
                Exception failure = null;
                try {
                    var config = new org.bukkit.configuration.file.YamlConfiguration();
                    config.load(new java.io.File(plugin.getDataFolder(), "config.yml"));
                    replacement = MessageCatalog.load(plugin.getDataFolder().toPath().resolve("lang"),
                            config.getString("messages.default-language", "ja_jp"));
                } catch (Exception e) {
                    failure = e;
                }
                final MessageCatalog loaded = replacement;
                final Exception error = failure;
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            current.reload(loadedOrThrow(loaded, error));
                            Messages.chat(recipient, "iem.admin.messages.reloaded", MessageText.of("iem.admin.messages.reloaded"));
                        } catch (Exception e) {
                            plugin.getLogger().warning("Message reload rejected: " + e.getMessage());
                            Messages.chat(recipient, "iem.admin.messages.reload_failed",
                                    MessageText.of("iem.admin.messages.reload_failed", e.getMessage()));
                        } finally {
                            reloading.set(false);
                        }
                    });
                } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
                    // 停止中は辞書を変更せず、通知も予約しない。
                    reloading.set(false);
                }
            });
        } catch (RuntimeException e) {
            reloading.set(false);
            throw e;
        }
    }
    private static MessageCatalog loadedOrThrow(MessageCatalog loaded, Exception failure) throws Exception {
        if (failure != null) throw failure;
        return Objects.requireNonNull(loaded);
    }
    @Override public Collection<String> suggest(CommandSourceStack stack,String[] args){return List.of("reload");}
}
