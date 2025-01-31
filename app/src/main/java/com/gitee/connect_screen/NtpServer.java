package com.gitee.connect_screen;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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
        long ntpTime = TimestampUtils.getCurrentNtpTime(true);
        
        // 设置Originate Timestamp (T0) - 从请求包中复制
        System.arraycopy(request, 24, response, 8, 8);
        
        TimestampUtils.putBigEndian(response, 16, ntpTime);

        TimestampUtils.putBigEndian(response, 24, ntpTime);
        
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