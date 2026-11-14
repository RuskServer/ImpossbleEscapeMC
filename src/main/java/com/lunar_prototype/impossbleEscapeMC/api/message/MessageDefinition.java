package com.lunar_prototype.impossbleEscapeMC.api.message;

import java.util.Objects;
/** 言語に依存しない通知の分類。本文はlang JSONで管理する。 */
public record MessageDefinition(String key, MessageCategory category, MessageLevel level, MessageChannel channel) {
    public MessageDefinition { Objects.requireNonNull(key);Objects.requireNonNull(category);Objects.requireNonNull(level);Objects.requireNonNull(channel); }
}
