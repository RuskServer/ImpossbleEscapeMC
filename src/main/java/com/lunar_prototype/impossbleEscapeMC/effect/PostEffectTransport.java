package com.lunar_prototype.impossbleEscapeMC.effect;

import org.bukkit.entity.Player;
import java.util.ArrayList;

/** PostEffectの変更・強制再送・掃除を送る唯一の場所。マズル等の別信号には触れない。 */
final class PostEffectTransport {
    void apply(Player player, PostEffectStack.Layers layers) { apply(player, layers, false); }
    void resend(Player player, PostEffectStack.Layers layers) { apply(player, layers, true); }
    private void apply(Player player, PostEffectStack.Layers layers, boolean resend) {
        var target = player.postEffects();
        var current = new ArrayList<>(target.values());
        var desired = PostEffectStack.compose(current, layers);
        if (!desired.equals(current)) target.set(desired);
        // ワールド読み込み後は、サーバー上の構成が同じでもクライアントへ送り直す。
        if (resend) target.update();
    }
    void clear(Player player) {
        var target = player.postEffects();
        target.set(PostEffectStack.externalOnly(new ArrayList<>(target.values())));
    }
}
