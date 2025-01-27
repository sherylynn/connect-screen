package com.gitee.connect_screen;

/*
AirPlay Mirror Protocol Packet Structure
======================================

Mirror Data Packet Header (128 bytes)
-----------------------------------

Bytes 0-3:   Payload size (32-bit integer)
Bytes 4-7:   Packet type and options
    - 4-5: Packet type identifier
        0x00 0x00: Encrypted packet containing non-IDR type 1 VCL NAL unit
        0x00 0x10: Encrypted packet containing IDR type 5 VCL NAL unit  
        0x01 0x00: Unencrypted packet containing type 7 SPS + type 8 PPS NAL units
        0x02 0x00: Unencrypted packet (old protocol) no payload, sent once per second
        0x05 0x00: Unencrypted packet with "streaming report", sent once per second
    - 6-7: Payload options
        0x00 0x00: Used for encrypted and "streaming report" packets
        0x1e 0x00: Used in old protocol (AirMyPC) no-payload packets
        0x16 0x01: Common in unencrypted h264 SPS+PPS packets
        0x56 0x01: Unencrypted h264 SPS+PPS packets (video stream stops, client sleeps)
        0x1e 0x01: Unencrypted h265/HEVC SPS+PPS packets
        0x5e 0x01: Unencrypted h265 SPS+PPS packets (video stream stops, client sleeps)

Bytes 8-15:  NTP timestamp (64-bit)
    - Not present in "streaming report" packets (type 0x05)

Bytes 16-127: Additional metadata (for SPS/PPS packets):
    16-19: Source width (float, value is x.0000 where x = unsigned short)
    20-23: Source height (float, value is x.0000 where x = unsigned short)
    24-39: Reserved (all 0x0)
    40-43: Source width repeated
    44-47: Source height repeated
    48-51: Other width value (unidentified)
    52-55: Other height value (unidentified)
    56-59: Display width
    60-63: Display height
    64-127: Reserved (all 0x0)

Payload
-------
For encrypted video packets (0x00):
- Contains encrypted H.264/H.265 NAL units
- Each NAL unit is prefixed with its size (4 bytes, big-endian)
- After decryption, size prefixes are replaced with 0x00000001 start codes

For SPS+PPS packets (0x01):
H.264:
- Contains unencrypted sequence and picture parameter sets
- Format details in payload bytes 0-11:
    0-5: Header
    6-7: SPS size (short, big-endian)
    8+: SPS data
    After SPS: PPS size (short, big-endian) followed by PPS data

H.265:
- Contains VPS, SPS and PPS units marked with:
    0xa0 0x00 0x01 0x00: VPS start
    0xa1 0x00 0x01 0x00: SPS start  
    0xa2 0x00 0x01 0x00: PPS start
- Each unit prefixed with 2-byte size

For streaming report packets (0x05):
- Contains binary property list with client performance data
- May include 25KB trailer with currently unidentified content
 */

import android.util.Log;
import java.net.Socket;
import java.io.IOException;

public class RtpSenderThread extends Thread {
    private static final String TAG = "RtpSenderThread";
    private final String host;
    private final int port;
    private Socket socket;
    private boolean running = true;

    public RtpSenderThread(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            Log.i(TAG, "已连接到数据端口: " + port);

            // 首先发送 SPS 和 PPS
            // 这里需要从编码器获取实际的 SPS 和 PPS 数据
            byte[] sps = new byte[] { /* 实际的 SPS 数据 */ };
            byte[] pps = new byte[] { /* 实际的 PPS 数据 */ };
            sendSPSPPSPacket(sps, pps);

            while (running) {
                // TODO: 从编码器获取 H.264 帧数据并发送
                Thread.sleep(500);
            }

        } catch (Exception e) {
            Log.e(TAG, "RTP发送线程错误: " + e.getMessage());
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "关闭socket错误: " + e.getMessage());
            }
        }
    }

    public void stopSending() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "停止发送时错误: " + e.getMessage());
        }
    }

    private void sendSPSPPSPacket(byte[] sps, byte[] pps) {
        // 计算总负载大小：header(6) + sps_size(2) + sps_data + pps_size(2) + pps_data
        int payloadSize = 6 + 2 + sps.length + 2 + pps.length;
        
        // 创建128字节的数据包头
        byte[] header = new byte[128];
        
        // 设置负载大小 (前4字节)
        header[0] = (byte)((payloadSize >> 24) & 0xFF);
        header[1] = (byte)((payloadSize >> 16) & 0xFF);
        header[2] = (byte)((payloadSize >> 8) & 0xFF);
        header[3] = (byte)(payloadSize & 0xFF);
        
        // 设置包类型 (0x01 0x00) 和选项 (0x16 0x01)
        header[4] = 0x01;
        header[5] = 0x00;
        header[6] = 0x16;
        header[7] = 0x01;
        
        // 设置视频尺寸信息 (示例使用1646x1080)
        setFloatValue(header, 16, 1646.0f);  // source width
        setFloatValue(header, 20, 1080.0f);  // source height
        setFloatValue(header, 40, 1646.0f);  // source width repeated
        setFloatValue(header, 44, 1080.0f);  // source height repeated
        setFloatValue(header, 56, 1646.0f);  // display width
        setFloatValue(header, 60, 1080.0f);  // display height
        
        // 创建负载数据
        byte[] payload = new byte[payloadSize];
        int offset = 0;
        
        // 添加SPS+PPS header (01 64 00 28 ff e1)
        payload[offset++] = 0x01;
        payload[offset++] = 0x64;
        payload[offset++] = 0x00;
        payload[offset++] = 0x28;
        payload[offset++] = (byte)0xff;
        payload[offset++] = (byte)0xe1;
        
        // 添加SPS大小和数据
        payload[offset++] = (byte)((sps.length >> 8) & 0xFF);
        payload[offset++] = (byte)(sps.length & 0xFF);
        System.arraycopy(sps, 0, payload, offset, sps.length);
        offset += sps.length;
        
        // 添加PPS大小和数据
        payload[offset++] = (byte)((pps.length >> 8) & 0xFF);
        payload[offset++] = (byte)(pps.length & 0xFF);
        System.arraycopy(pps, 0, payload, offset, pps.length);
        
        // TODO: 通过网络发送header和payload
    }
    
    private void setFloatValue(byte[] buffer, int offset, float value) {
        int intBits = Float.floatToIntBits(value);
        buffer[offset] = (byte)((intBits >> 24) & 0xFF);
        buffer[offset + 1] = (byte)((intBits >> 16) & 0xFF);
        buffer[offset + 2] = (byte)((intBits >> 8) & 0xFF);
        buffer[offset + 3] = (byte)(intBits & 0xFF);
    }
}
