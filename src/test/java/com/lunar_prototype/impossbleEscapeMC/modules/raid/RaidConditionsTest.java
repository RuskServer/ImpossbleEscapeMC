package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** 時間帯・天候の抽選の範囲と割合、現地時刻の表示を確かめる。サーバーは要らない */
public final class RaidConditionsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Random random = new Random(42);
        int n = 20000;
        Map<RaidConditions.Period, Integer> periods = new EnumMap<>(RaidConditions.Period.class);
        Map<RaidConditions.Weather, Integer> weathers = new EnumMap<>(RaidConditions.Weather.class);
        for (int i = 0; i < n; i++) {
            RaidConditions c = RaidConditions.roll(random);
            periods.merge(c.period(), 1, Integer::sum);
            weathers.merge(c.weather(), 1, Integer::sum);
            if (c.period() == RaidConditions.Period.DAY) {
                check(c.worldTime() >= RaidConditions.DAY_START && c.worldTime() < RaidConditions.DAY_END, "day time " + c.worldTime());
            } else {
                check(c.worldTime() >= RaidConditions.NIGHT_START && c.worldTime() < RaidConditions.NIGHT_END, "night time " + c.worldTime());
            }
        }
        double day = periods.get(RaidConditions.Period.DAY) / (double) n;
        check(Math.abs(day - RaidConditions.DAY_CHANCE) < 0.02, "day share " + day);
        double clear = weathers.get(RaidConditions.Weather.CLEAR) / (double) n;
        double storm = weathers.get(RaidConditions.Weather.STORM) / (double) n;
        check(Math.abs(clear - RaidConditions.WEATHER_CHANCES[0]) < 0.02, "clear share " + clear);
        check(Math.abs(storm - RaidConditions.WEATHER_CHANCES[2]) < 0.02, "storm share " + storm);

        check(new RaidConditions(RaidConditions.Period.DAY, 0, RaidConditions.Weather.CLEAR).clock().equals("06:00"), "0 = 06:00");
        check(new RaidConditions(RaidConditions.Period.DAY, 6000, RaidConditions.Weather.CLEAR).clock().equals("12:00"), "6000 = 12:00");
        check(new RaidConditions(RaidConditions.Period.NIGHT, 18000, RaidConditions.Weather.RAIN).clock().equals("00:00"), "18000 = 00:00");
        check(new RaidConditions(RaidConditions.Period.NIGHT, 15500, RaidConditions.Weather.STORM).describe().equals("夜 21:30 / 雷雨"), "describe");
        System.out.println("RaidConditionsTest: ranges, shares and local clock passed");
    }
}
