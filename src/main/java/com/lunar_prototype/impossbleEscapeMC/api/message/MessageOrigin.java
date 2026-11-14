package com.lunar_prototype.impossbleEscapeMC.api.message;

/** キューへ渡す前の直接の呼び出し位置。Classを保持しない。 */
public record MessageOrigin(String className, String methodName, String fileName, int lineNumber) {
    public static MessageOrigin capture() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().startsWith("com.lunar_prototype.impossbleEscapeMC.api.message."))
                .findFirst().map(f -> new MessageOrigin(f.getClassName(),f.getMethodName(),f.getFileName(),f.getLineNumber()))
                .orElse(new MessageOrigin("unknown","unknown",null,-1)));
    }
}
