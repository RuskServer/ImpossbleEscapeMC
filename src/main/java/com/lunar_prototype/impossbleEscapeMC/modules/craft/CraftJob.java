package com.lunar_prototype.impossbleEscapeMC.modules.craft;

/**
 * 製作のキューの 1 つ (プレイヤーデータに保存する。Gson で読むので record にしない)。
 * 時間は実時間 (ミリ秒) で持つので、ログアウトしている間も進む。終わる時刻 endMillis は、前の製作が終わってから始まる前提で決める
 * ({@link CraftQueues})
 */
public final class CraftJob {
    private String recipeId;
    private long durationMillis;
    private long endMillis;

    public CraftJob(String recipeId, long durationMillis, long endMillis) {
        this.recipeId = recipeId;
        this.durationMillis = durationMillis;
        this.endMillis = endMillis;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }
}
