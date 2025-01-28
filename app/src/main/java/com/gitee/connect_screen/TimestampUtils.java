package com.gitee.connect_screen;
import android.util.Log;

public class TimestampUtils {
    private static final String TAG = "TimestampUtils";

    public static final long MILLIS_TO_NANOS = 1000000L;

    /**
     * 将当前系统时间转换为NTP时间戳格式并写入字节数组(小端序)
     * @param buffer 目标字节数组
     * @param offset 写入位置的偏移量(通常是8)
     * @param currentTimeMillis 当前系统时间(毫秒)
     */
    public static void putNtpTimestamp(byte[] buffer, int offset, long currentTimeMillis) {
        // 1. 计算秒数部分
        long seconds = currentTimeMillis / 1000L;

        // 2. 计算小数部分(转换为32位定点数)
        long millis = currentTimeMillis % 1000L;
        long fraction = (millis * 0x100000000L) / 1000L;

        // 将秒数和小数部分组合成完整的NTP时间戳
        long ntpTimestamp = (seconds << 32) | fraction;

        // 写入秒数(小端序)
        buffer[offset + 4] = (byte) ((ntpTimestamp >> 32) & 0xFF);
        buffer[offset + 5] = (byte) ((ntpTimestamp >> 40) & 0xFF);
        buffer[offset + 6] = (byte) ((ntpTimestamp >> 48) & 0xFF);
        buffer[offset + 7] = (byte) ((ntpTimestamp >> 56) & 0xFF);

        // 写入小数部分(小端序)
        buffer[offset] = (byte) (ntpTimestamp & 0xFF);
        buffer[offset + 1] = (byte) ((ntpTimestamp >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((ntpTimestamp >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((ntpTimestamp >> 24) & 0xFF);

        // 添加日志
        Log.d(TAG, String.format("写入NTP时间戳: 当前系统时间=%d ms (%.3f s), " +
                        "NTP完整时间戳=0x%016X, " +
                        "NTP秒数=%d, 小数部分=%d, " +
                        "转换后时间=%.3f s",
                currentTimeMillis,
                currentTimeMillis / 1000.0,
                ntpTimestamp,
                seconds,
                fraction,
                (seconds) + (fraction / (double)(1L << 32))));
    }

    /**
     * 从字节数组中读取NTP时间戳并转换为纳秒时间(小端序)
     * @param buffer 源字节数组
     * @param offset 读取位置的偏移量
     * @return 纳秒时间戳
     */
    public static long getNtpTimestampAsNanos(byte[] buffer, int offset) {
        // 读取秒数(小端序)
        long seconds = ((buffer[offset + 3] & 0xFFL) << 24) |
                ((buffer[offset + 2] & 0xFFL) << 16) |
                ((buffer[offset + 1] & 0xFFL) << 8) |
                (buffer[offset] & 0xFFL);

        // 读取小数部分(小端序)
        long fraction = ((buffer[offset + 7] & 0xFFL) << 24) |
                ((buffer[offset + 6] & 0xFFL) << 16) |
                ((buffer[offset + 5] & 0xFFL) << 8) |
                (buffer[offset + 4] & 0xFFL);

        // 转换为纳秒
        long originalSeconds = seconds;
        long nanos = (seconds * MILLIS_TO_NANOS) + ((fraction * MILLIS_TO_NANOS) >> 32);

        // 添加日志
        Log.d(TAG, String.format("读取NTP时间戳: NTP秒数=%d, 小数部分=%d, " +
                        "转换后纳秒=%d ns (%.6f s)",
                originalSeconds,
                fraction,
                nanos,
                nanos / (double)MILLIS_TO_NANOS));

        return nanos;
    }
}
