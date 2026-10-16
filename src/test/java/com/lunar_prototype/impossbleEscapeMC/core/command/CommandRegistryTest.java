package com.lunar_prototype.impossbleEscapeMC.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * コマンドの登録: {@link Command} を付けたコマンドが前の plugin.yml と同じ名前・権限で揃っている、
 * 補完の引数を Bukkit の形にする、注釈の権限が付く
 */
public final class CommandRegistryTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 前の plugin.yml の commands (名前 → 権限。空は誰でも) */
    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("getitem", "impossbleescapemc.getitem"), Map.entry("scavspawn", "op"), Map.entry("attachment", ""),
            Map.entry("mg", "op"), Map.entry("itemreload", "op"), Map.entry("acoustics", "op"), Map.entry("shaderdebug", "op"),
            Map.entry("raid", "op"), Map.entry("party", ""), Map.entry("loot", "op"), Map.entry("trader", "op"),
            Map.entry("market", ""), Map.entry("quest", "op"), Map.entry("settings", ""), Map.entry("hideout", ""),
            Map.entry("datapacktest", "op"));

    @Command(name = "dummy", permission = "test.perm")
    private static final class Dummy implements BasicCommand {
        @Override
        public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        }
    }

    public static void main(String[] args) throws IOException {
        // ---- 注釈の付いたコマンド (ビルドしたクラスを探す。初期化はしない) ----
        Path root = Path.of("target/classes");
        Map<String, Command> found = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", "");
                Class<?> type;
                try {
                    type = Class.forName(name, false, CommandRegistryTest.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    continue;   // サーバーの中身 (NMS) を継承したクラスはここでは読めない (コマンドが読めなければ下の名前の確かめで分かる)
                }
                Command info = type.getAnnotation(Command.class);
                if (info == null) continue;
                check(BasicCommand.class.isAssignableFrom(type), "not a BasicCommand: " + name);
                check(found.put(info.name(), info) == null, "duplicate command: " + info.name());
                check(!info.description().isEmpty(), "no description: " + info.name());
            }
        }
        check(found.keySet().equals(EXPECTED.keySet()), "commands " + found.keySet());
        for (var e : EXPECTED.entrySet()) {
            check(found.get(e.getKey()).permission().equals(e.getValue()), "permission of " + e.getKey());
        }

        // ---- 補完の引数 ----
        check(Arrays.equals(CommandRegistry.completionArgs(new String[0]), new String[]{""}), "empty -> one empty");
        String[] typed = {"set", "fac"};
        check(CommandRegistry.completionArgs(typed) == typed, "typed args kept");

        // ---- 登録簿と、注釈の権限 ----
        CommandRegistry registry = new CommandRegistry();
        Dummy dummy = new Dummy();
        registry.register(dummy);
        check(registry.commands().equals(List.of(dummy)), "registered");
        BasicCommand wrapped = CommandRegistry.wrap(dummy, Dummy.class.getAnnotation(Command.class));
        check("test.perm".equals(wrapped.permission()), "annotation permission");
        try {
            registry.register(new BasicCommand() {
                @Override
                public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
                }
            });
            check(false, "unannotated command accepted");
        } catch (IllegalArgumentException expected) {
            // 注釈が無ければ断る
        }
        System.out.println("CommandRegistryTest OK (" + found.size() + " commands)");
    }
}
