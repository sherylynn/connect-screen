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
        byte[] response = new byte[48];
        System.arraycopy(request, 0, response, 0, 48);
        
        // 设置 Leap Indicator, Version Number, 和 Mode
        response[0] = (byte) ((4 << 3) | 4); // Version 4, Server Mode
        
        // 获取当前时间戳（从1900年开始的秒数）
        long now = System.currentTimeMillis();
        long ntpTime = now / 1000L + 2208988800L; // 转换为 NTP 时间戳
        
        // 设置 Transmit Timestamp
        long seconds = ntpTime;
        long fraction = ((now % 1000L) << 32) / 1000L;
        
        // 将时间戳写入响应包
        for (int i = 40; i <= 43; i++) {
            response[i] = (byte) (seconds >>> ((3 - (i - 40)) * 8));
        }
        for (int i = 44; i <= 47; i++) {
            response[i] = (byte) (fraction >>> ((3 - (i - 44)) * 8));
        }
        
        Log.i(TAG, "NTP响应已生成，时间戳: " + ntpTime);
        return response;
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