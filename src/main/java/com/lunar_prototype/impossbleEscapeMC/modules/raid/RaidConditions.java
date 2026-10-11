package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.random.RandomGenerator;

/**
 * レイドの時間帯 (昼・夜) と天候。マップの周期ごとに RaidModule が決め、周期が始まる時にレイドのワールドへ当てはめる。
 * レイドの間は時刻も天候も止める (ワールドの昼夜・天候の進行を止める)。
 * <ul>
 *   <li>時間帯: 昼 {@link #DAY_CHANCE}、夜 (太陽が沈んでから昇るまで)。同じ時間帯でも時刻は毎回少し変える (太陽・月の位置)</li>
 *   <li>天候: 晴れ・雨・雷雨</li>
 * </ul>
 * 夜や雨は空の光が暗くなるので、SCAV の視覚 (ScavVision の明るさ) にも効く
 */
public record RaidConditions(Period period, long worldTime, Weather weather) {

    public enum Period {
        DAY("昼"), NIGHT("夜");

        public final String label;

        Period(String label) {
            this.label = label;
        }
    }

    public enum Weather {
        CLEAR("晴れ"), RAIN("雨"), STORM("雷雨");

        public final String label;

        Weather(String label) {
            this.label = label;
        }
    }

    static final double DAY_CHANCE = 0.65;
    /** 天候の確率 (晴れ, 雨, 雷雨) */
    static final double[] WEATHER_CHANCES = {0.60, 0.30, 0.10};
    /** 昼の時刻の範囲 (朝 7 時〜夕方 5 時) */
    static final long DAY_START = 1000, DAY_END = 11000;
    /** 夜の時刻の範囲 (夜 8 時〜朝 4 時。空が暗い間) */
    static final long NIGHT_START = 14000, NIGHT_END = 22000;
    /** 天候を止めておく長さ (tick)。周期 (10 分) より十分長く */
    private static final int WEATHER_HOLD_TICKS = 20 * 60 * 60;

    public static RaidConditions roll(RandomGenerator random) {
        Period period = random.nextDouble() < DAY_CHANCE ? Period.DAY : Period.NIGHT;
        long time = period == Period.DAY ? random.nextLong(DAY_START, DAY_END) : random.nextLong(NIGHT_START, NIGHT_END);
        double r = random.nextDouble();
        Weather weather = r < WEATHER_CHANCES[0] ? Weather.CLEAR
                : r < WEATHER_CHANCES[0] + WEATHER_CHANCES[1] ? Weather.RAIN : Weather.STORM;
        return new RaidConditions(period, time, weather);
    }

    /** レイドのワールドへ当てはめる (時刻と天候を決めて止める) */
    @SuppressWarnings("deprecation")
    public void applyTo(World world) {
        if (world == null) return;
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(worldTime);
        switch (weather) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(WEATHER_HOLD_TICKS);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(WEATHER_HOLD_TICKS);
            }
            case STORM -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(WEATHER_HOLD_TICKS);
                world.setThunderDuration(WEATHER_HOLD_TICKS);
            }
        }
    }

    /** 現地の時刻 (例 "21:30")。マイクラの時刻 0 は朝 6 時 */
    public String clock() {
        long minutes = ((worldTime + 6000) % 24000) * 60 / 1000;
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** 表示用 (例 "夜 21:30 / 雨") */
    public String describe() {
        return period.label + " " + clock() + " / " + weather.label;
    }
}
