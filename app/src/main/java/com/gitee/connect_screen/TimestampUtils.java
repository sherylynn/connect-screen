package com.gitee.connect_screen;

public class TimestampUtils {
    private static final String TAG = "TimestampUtils";

    /**
     * 将当前系统时间转换为NTP时间戳格式并写入字节数组(小端序)
     * @param buffer 目标字节数组
     * @param offset 写入位置的偏移量(通常是8)
     * @param ntpTimestamp 当前系统时间(毫秒)
     */
    public static void putLittleEndian(byte[] buffer, int offset, long ntpTimestamp) {
        // 写入小数部分(小端序)
        buffer[offset] = (byte) (ntpTimestamp & 0xFF);
        buffer[offset + 1] = (byte) ((ntpTimestamp >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((ntpTimestamp >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((ntpTimestamp >> 24) & 0xFF);
        // 写入秒数(小端序)
        buffer[offset + 4] = (byte) ((ntpTimestamp >> 32) & 0xFF);
        buffer[offset + 5] = (byte) ((ntpTimestamp >> 40) & 0xFF);
        buffer[offset + 6] = (byte) ((ntpTimestamp >> 48) & 0xFF);
        buffer[offset + 7] = (byte) ((ntpTimestamp >> 56) & 0xFF);
    }


    /**
     * 将当前系统时间转换为NTP时间戳格式并写入字节数组(大端序)
     * @param buffer 目标字节数组
     * @param offset 写入位置的偏移量(通常是8)
     * @param ntpTimestamp 当前系统时间(毫秒)
     */
    public static void putBigEndian(byte[] buffer, int offset, long ntpTimestamp) {
        // 写入秒数(大端序)
        buffer[offset] = (byte) ((ntpTimestamp >> 56) & 0xFF);
        buffer[offset + 1] = (byte) ((ntpTimestamp >> 48) & 0xFF);
        buffer[offset + 2] = (byte) ((ntpTimestamp >> 40) & 0xFF);
        buffer[offset + 3] = (byte) ((ntpTimestamp >> 32) & 0xFF);
        // 写入小数部分(大端序)
        buffer[offset + 4] = (byte) ((ntpTimestamp >> 24) & 0xFF);
        buffer[offset + 5] = (byte) ((ntpTimestamp >> 16) & 0xFF);
        buffer[offset + 6] = (byte) ((ntpTimestamp >> 8) & 0xFF);
        buffer[offset + 7] = (byte) (ntpTimestamp & 0xFF);
    }

    public static long getCurrentNtpTime(boolean addSeconds) {
        // 获取当前系统时间（纳秒级）
        long nanoTime = System.nanoTime();

        // 计算秒数部分 (将纳秒转换为秒)
        long seconds = nanoTime / 1_000_000_000L;

        if (addSeconds) {
            seconds += 2208988800L;
        }

        // 计算小数部分 (剩余的纳秒转换为NTP小数格式)
        // NTP小数部分使用2^32表示1秒，所以需要将剩余纳秒相应转换
        long fraction = ((nanoTime % 1_000_000_000L) << 32) / 1_000_000_000L;

        // 组合秒数和小数部分为NTP时间戳
        // 秒数部分在高32位，小数部分在低32位
        return (seconds << 32) | fraction;
    }
}
