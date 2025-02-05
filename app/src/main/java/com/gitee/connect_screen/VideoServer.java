package com.gitee.connect_screen;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class VideoServer extends Thread {
    private static final String TAG = "VideoServer";
    private static final int BUFFER_SIZE = 65535; // UDP包的最大大小
    
    private volatile boolean isRunning;
    private DatagramSocket socket;
    private final byte[] buffer;
    
    public VideoServer() {
        this.isRunning = true;
        this.buffer = new byte[BUFFER_SIZE];
    }
    
    @Override
    public void run() {
        try {
            socket = new DatagramSocket(NvHTTP.VIDEO_PORT);
            Log.i(TAG, "视频服务器已启动，正在监听端口: " + NvHTTP.VIDEO_PORT);
            
            while (isRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    handleVideoPacket(packet);
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "接收数据包时出错: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            if (isRunning) {
                Log.e(TAG, "创建 UDP socket 失败: " + e.getMessage());
            }
        } finally {
            shutdown();
        }
    }
    
    private void handleVideoPacket(DatagramPacket packet) {
        // 获取接收到的数据
        byte[] data = packet.getData();
        int length = packet.getLength();
        String clientAddress = packet.getAddress().getHostAddress();
        int clientPort = packet.getPort();
        
        // 在这里处理视频数据包
        Log.d(TAG, String.format("收到来自 %s:%d 的视频数据包，长度: %d 字节",
                clientAddress, clientPort, length));
        
        // TODO: 在这里添加视频数据的具体处理逻辑
        // 比如解码视频流、保存视频数据等
    }
    
    public void shutdown() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            Log.i(TAG, "视频服务器已关闭");
        }
    }
} 