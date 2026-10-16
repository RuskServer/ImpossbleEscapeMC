package com.lunar_prototype.impossbleEscapeMC.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * コマンドの名前・説明・別名・権限。Paper の BasicCommand を実装したクラスに付けて、{@link CommandRegistry} に渡す
 * (起動時に Paper のコマンド (Brigadier) として登録される。plugin.yml には書かない)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    /** コマンドの名前 (/ の後) */
    String name();

    /** 説明 (/help などに出る) */
    String description() default "";

    /** 別名 */
    String[] aliases() default {};

    /**
     * 使うのに要る権限 (空なら誰でも)。持っていない人には、コマンドそのものが見えない (補完にも出ない)。
     * 登録していない権限は OP だけが持つ扱いになる
     */
    String permission() default "";
}
