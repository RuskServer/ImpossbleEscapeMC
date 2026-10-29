package com.lunar_prototype.impossbleEscapeMC.effect;

/** アドレナリン後の心拍。120→60BPMへ間隔を広げ、4秒かけて聴こえなくする。 */
public final class AdrenalineHeartbeatRecovery {
    public static final int DURATION_TICKS = 80;
    public static final int START_INTERVAL = 10;
    private int age;
    private int nextBeat;
    public record Beat(float volume, float pitch) {}

    public AdrenalineHeartbeatRecovery(int delay) { nextBeat = Math.clamp(delay,1,START_INTERVAL); }
    public boolean finished() { return age >= DURATION_TICKS; }
    public Beat tick() {
        if (finished()) return null;
        age++;
        if (finished() || age < nextBeat) return null;
        double progress = age / (double) DURATION_TICKS;
        double smooth = progress * progress * (3-2*progress);
        nextBeat = age + (int) Math.round(START_INTERVAL + 10*smooth);
        return new Beat((float)(0.9*Math.pow(1-progress,1.25)),(float)(1-0.15*smooth));
    }
}
