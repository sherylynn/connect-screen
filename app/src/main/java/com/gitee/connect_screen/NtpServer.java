package com.gitee.connect_screen;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class NtpServer extends Thread {
    private static final String TAG = "NtpServer";
    private final int port;
    private boolean running = false;
    private DatagramSocket socket;

    public NtpServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        running = true;
        try {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[48];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            Log.i(TAG, "NTP 服务器启动在端口: " + port);
            
            while (running) {
                socket.receive(packet);
                Log.i(TAG, "收到来自 " + packet.getAddress() + ":" + packet.getPort() + " 的NTP请求");
                
                // 处理 NTP 请求
                byte[] response = processNtpRequest(packet.getData());
                
                // 发送响应
                DatagramPacket responsePacket = new DatagramPacket(
                    response,
                    response.length,
                    packet.getAddress(),
                    packet.getPort()
                );
                socket.send(responsePacket);
                Log.i(TAG, "已发送NTP响应到 " + packet.getAddress() + ":" + packet.getPort());
            }
        } catch (Exception e) {
            Log.e(TAG, "NTP 服务器错误: " + e.getMessage());
        }
    }

    private byte[] processNtpRequest(byte[] request) {
        Log.i(TAG, "开始处理NTP请求");
        
        // 创建48字节的响应数据包
        byte[] response = new byte[48];
        
        // 复制原始报文的前8字节（包含客户端的发送时间戳）
        System.arraycopy(request, 0, response, 0, 8);
        
        // 设置Leap Indicator(LI=0), Version(VN=4), Mode(Mode=4,server)
        response[0] = (byte) (0x24); // 00100100 in binary
        
        // 设置Stratum (本地时钟)
        response[1] = 1;
        
        // 设置Poll Interval (默认值)
        response[2] = 10;
        
        // 设置Precision (默认值)
        response[3] = (byte) 0xFA; // ~15ms
        
        // 获取当前NTP时间戳
        long ntpTime = getCurrentNtpTime();
        
        // 设置Reference Timestamp (T1)
        writeTimeStamp(response, 16, ntpTime);
        
        // 设置Originate Timestamp (T2) - 从请求包中复制
        System.arraycopy(request, 24, response, 24, 8);
        
        // 设置Receive Timestamp (T3)
        writeTimeStamp(response, 32, ntpTime);
        
        // 设置Transmit Timestamp (T4)
        writeTimeStamp(response, 40, ntpTime);
        
        Log.i(TAG, "NTP响应已生成，时间戳: " + ntpTime);
        return response;
    }
    
    // 辅助方法：将时间戳写入字节数组
    private void writeTimeStamp(byte[] array, int offset, long timestamp) {
        // 处理秒数部分
        long seconds = timestamp >> 32;
        array[offset] = (byte) ((seconds >> 24) & 0xFF);
        array[offset + 1] = (byte) ((seconds >> 16) & 0xFF);
        array[offset + 2] = (byte) ((seconds >> 8) & 0xFF);
        array[offset + 3] = (byte) (seconds & 0xFF);
        
        // 处理小数部分
        long fraction = timestamp & 0xFFFFFFFFL;
        array[offset + 4] = (byte) ((fraction >> 24) & 0xFF);
        array[offset + 5] = (byte) ((fraction >> 16) & 0xFF);
        array[offset + 6] = (byte) ((fraction >> 8) & 0xFF);
        array[offset + 7] = (byte) (fraction & 0xFF);
    }

    private long getCurrentNtpTime() {
        // 获取当前系统时间（纳秒级）
        long nanoTime = System.nanoTime();
        long milliTime = System.currentTimeMillis();
        
        // 计算完整的 NTP 时间戳
        long seconds = milliTime / 1000L + 2208988800L;
        long fraction = ((nanoTime % 1000000000L) << 32) / 1000000000L;
        
        return (seconds << 32) | fraction;
    }

    public void stopServer() {
        Log.i(TAG, "正在停止NTP服务器...");
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            Log.i(TAG, "NTP服务器已停止");
        }
    }
} 