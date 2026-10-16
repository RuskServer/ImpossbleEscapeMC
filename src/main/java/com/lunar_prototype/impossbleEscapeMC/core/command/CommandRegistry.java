package com.lunar_prototype.impossbleEscapeMC.core.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * プラグインのコマンドの登録簿。{@link Command} を付けた BasicCommand を {@link #register} で集め、
 * Paper のコマンドの登録の時 (LifecycleEvents.COMMANDS。起動時と /minecraft:reload の時) にまとめて登録する。
 * 本体は {@link #install} を onEnable で 1 回呼ぶ。モジュールは onEnable で ServiceContainer からこれを取って自分のコマンドを入れる。
 * <p>
 * 登録するコマンドは包んで、注釈の権限 ({@link Command#permission}) を付け、補完の引数を Bukkit の TabCompleter と同じ形にする
 * (Paper の BasicCommand は、何も打っていない時の補完に空の配列を渡す。Bukkit は空文字 1 つ。各コマンドの補完は Bukkit の形で書いてある)
 */
public final class CommandRegistry {

    private final List<BasicCommand> commands = new ArrayList<>();

    /** コマンドを入れる ({@link Command} が付いていること) */
    public void register(BasicCommand... commands) {
        for (BasicCommand command : commands) {
            if (!command.getClass().isAnnotationPresent(Command.class)) {
                throw new IllegalArgumentException("@Command が付いていません: " + command.getClass().getName());
            }
            this.commands.add(command);
        }
    }

    /** 入れたコマンド */
    public List<BasicCommand> commands() {
        return List.copyOf(commands);
    }

    /** Paper のコマンドの登録の時に、入れたコマンドをまとめて登録する */
    @SuppressWarnings("UnstableApiUsage")
    public void install(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (BasicCommand command : commands) {
                Command info = command.getClass().getAnnotation(Command.class);
                event.registrar().register(info.name(), info.description(), Arrays.asList(info.aliases()), wrap(command, info));
            }
            plugin.getLogger().info("Registered " + commands.size() + " commands.");
        });
    }

    /** 注釈の権限と、Bukkit の形の補完の引数 */
    static BasicCommand wrap(BasicCommand command, Command info) {
        String permission = info.permission().isEmpty() ? command.permission() : info.permission();
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) throws CommandSyntaxException {
                command.execute(stack, args);
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
                return command.suggest(stack, completionArgs(args));
            }

            @Override
            public boolean canUse(@NotNull CommandSender sender) {
                return command.canUse(sender) && (permission == null || sender.hasPermission(permission));
            }

            @Override
            public @Nullable String permission() {
                return permission;
            }
        };
    }

    /** 補完の引数を Bukkit の形にする (何も打っていない時は空文字 1 つ) */
    static String[] completionArgs(String[] args) {
        return args.length == 0 ? new String[]{""} : args;
    }
}
