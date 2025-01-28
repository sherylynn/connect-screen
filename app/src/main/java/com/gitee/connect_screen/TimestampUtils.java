package com.gitee.connect_screen;
public class TimestampUtils {

    public static final long SECOND_IN_NSECS = 1000000000L;
    public static final long SECONDS_FROM_1900_TO_1970 = 2208988800L;

    /**
     * 将当前系统时间转换为NTP时间戳格式并写入字节数组(小端序)
     * @param buffer 目标字节数组
     * @param offset 写入位置的偏移量(通常是8)
     * @param currentTimeNanos 当前系统时间(纳秒)
     */
    public static void putNtpTimestamp(byte[] buffer, int offset, long currentTimeNanos) {
        // 1. 计算秒数部分
        long seconds = currentTimeNanos / SECOND_IN_NSECS;
        seconds += SECONDS_FROM_1900_TO_1970; // 加上1900到1970的秒数差值

        // 2. 计算小数部分(转换为32位定点数)
        long nanos = currentTimeNanos % SECOND_IN_NSECS;
        long fraction = (nanos << 32) / SECOND_IN_NSECS;

        // 写入秒数(小端序)
        buffer[offset] = (byte) (seconds & 0xFF);
        buffer[offset + 1] = (byte) ((seconds >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((seconds >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((seconds >> 24) & 0xFF);

        // 写入小数部分(小端序)
        buffer[offset + 4] = (byte) (fraction & 0xFF);
        buffer[offset + 5] = (byte) ((fraction >> 8) & 0xFF);
        buffer[offset + 6] = (byte) ((fraction >> 16) & 0xFF);
        buffer[offset + 7] = (byte) ((fraction >> 24) & 0xFF);
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
        seconds -= SECONDS_FROM_1900_TO_1970; // 减去1900到1970的秒数差值
        return (seconds * SECOND_IN_NSECS) + ((fraction * SECOND_IN_NSECS) >> 32);
    }
}
