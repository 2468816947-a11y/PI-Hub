package com.patrol.simulator.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 仿真数据随机化工具。
 * <p>
 * 所有随机源都用 {@link ThreadLocalRandom}(线程安全、无锁、并发友好)。
 */
public final class RandomData {

    private RandomData() {}

    /** 高斯分布温度(°C),均值 mean,标准差 std,下限 min,上限 max */
    public static double gaussianClamped(double mean, double std, double min, double max) {
        double v;
        // Box-Muller(单线程足够)
        double u1 = Math.max(1e-9, ThreadLocalRandom.current().nextDouble());
        double u2 = ThreadLocalRandom.current().nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        v = mean + z * std;
        return Math.max(min, Math.min(max, v));
    }

    /** 偏移一个经纬度(在中心点 ±jitter 度范围内) */
    public static double jitter(double center, double jitter) {
        return center + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
    }

    /** 在 [min, max] 区间随机整数 */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** 在 [min, max] 区间随机 long */
    public static long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /** 在 [min, max) 区间随机 double */
    public static double randomDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /** 概率触发 */
    public static boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /** 列表随机选一 */
    public static <T> T pick(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("pick from empty list");
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** 数组随机选一 */
    public static <T> T pick(T[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }
}
