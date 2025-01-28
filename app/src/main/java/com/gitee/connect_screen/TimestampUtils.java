package com.gitee.connect_screen;
import android.util.Log;

public class TimestampUtils {
    private static final String TAG = "TimestampUtils";

    public static final long SECOND_IN_NSECS = 1000000000L;

    /**
     * 将当前系统时间转换为NTP时间戳格式并写入字节数组(小端序)
     * @param buffer 目标字节数组
     * @param offset 写入位置的偏移量(通常是8)
     * @param currentTimeNanos 当前系统时间(纳秒)
     */
    public static void putNtpTimestamp(byte[] buffer, int offset, long currentTimeNanos) {
        // 1. 计算秒数部分
        long seconds = currentTimeNanos / SECOND_IN_NSECS;

        // 2. 计算小数部分(转换为32位定点数)
        long nanos = currentTimeNanos % SECOND_IN_NSECS;
        long fraction = (nanos << 32) / SECOND_IN_NSECS;

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
        Log.d(TAG, String.format("写入NTP时间戳: 当前系统时间=%d ns (%.6f s), " +
                        "NTP完整时间戳=0x%016X, " +
                        "NTP秒数=%d, 小数部分=%d, " +
                        "转换后时间=%.6f s",
                currentTimeNanos,
                currentTimeNanos / (double)SECOND_IN_NSECS,
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
        long nanos = (seconds * SECOND_IN_NSECS) + ((fraction * SECOND_IN_NSECS) >> 32);

        // 添加日志
        Log.d(TAG, String.format("读取NTP时间戳: NTP秒数=%d, 小数部分=%d, " +
                        "转换后纳秒=%d ns (%.6f s)",
                originalSeconds,
                fraction,
                nanos,
                nanos / (double)SECOND_IN_NSECS));

        return nanos;
    }
}
