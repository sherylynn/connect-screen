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

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import java.net.Socket;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RtpSenderThread extends Thread {
    private static final String TAG = "RtpSenderThread";
    private final String host;
    private final int port;
    private Socket socket;
    private java.io.OutputStream outputStream;
    private boolean running = true;
    private final MediaProjection mediaProjection;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;

    public RtpSenderThread(String host, int port, MediaProjection mediaProjection) {
        this.host = host;
        this.port = port;
        this.mediaProjection = mediaProjection;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            outputStream = socket.getOutputStream();
            Log.i(TAG, "已连接到数据端口: " + port);

            // 初始化编码器
            Log.d(TAG, "开始初始化编码器...");
            setupEncoder();
            Log.i(TAG, "编码器初始化完成");

            // 等待编码器输出 SPS 和 PPS
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            byte[] sps = null;
            byte[] pps = null;
            boolean gotSpsPps = false;

            Log.d(TAG, "等待获取 SPS 和 PPS...");
            while (!gotSpsPps && running) {
                int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    if (outputBuffer != null && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.get(data);
                        Log.d(TAG, "收到编码器配置数据，大小: " + bufferInfo.size + " 字节");
                        // 解析 SPS 和 PPS
                        int[] startIndex = new int[2];
                        int[] nalLength = new int[2];
                        int numNals = parseSpsPps(data, startIndex, nalLength);
                        if (numNals == 2) {
                            sps = new byte[nalLength[0]];
                            pps = new byte[nalLength[1]];
                            System.arraycopy(data, startIndex[0], sps, 0, nalLength[0]);
                            System.arraycopy(data, startIndex[1], pps, 0, nalLength[1]);
                            gotSpsPps = true;
                            Log.i(TAG, "成功获取 SPS(" + nalLength[0] + "字节) 和 PPS(" + nalLength[1] + "字节)");
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferId, false);
                }
            }

            // 发送 SPS 和 PPS
            if (sps != null && pps != null) {
                Log.d(TAG, "开始发送 SPS 和 PPS...");
                sendFirstPacket(sps, pps);
                Log.i(TAG, "SPS 和 PPS 发送完成");
            }

            Log.i(TAG, "开始主循环发送视频数据");
            // 开始发送编码后的视频数据
            while (running) {
                int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    if (outputBuffer != null) {
                        // todo
                    }
                    encoder.releaseOutputBuffer(outputBufferId, false);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "RTP发送线程错误: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void setupEncoder() throws IOException {
        int width = 1920;
        int height = 1080;
        
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        
        Surface inputSurface = encoder.createInputSurface();
        encoder.start();
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecording",
            width, height, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            inputSurface, null, null);
    }

    private void cleanup() {
        Log.d(TAG, "开始清理资源...");
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭socket错误: " + e.getMessage());
        }
        Log.i(TAG, "资源清理完成");
    }

    private void sendFirstPacket(byte[] sps, byte[] pps) throws IOException {
        int payloadSize = 6 + 2 + sps.length + 2 + pps.length + 4;
        byte[] packet = new byte[128 + payloadSize];
        
        // 修改为小端字节序 (little-endian)
        packet[0] = (byte)(payloadSize & 0xFF);
        packet[1] = (byte)((payloadSize >> 8) & 0xFF);
        packet[2] = (byte)((payloadSize >> 16) & 0xFF);
        packet[3] = (byte)((payloadSize >> 24) & 0xFF);
        
        // 添加日志输出
        Log.d(TAG, String.format("发送数据包 - 负载大小: %d (0x%08X), 字节: [%02X %02X %02X %02X]",
            payloadSize, payloadSize,
            packet[0] & 0xFF, packet[1] & 0xFF, packet[2] & 0xFF, packet[3] & 0xFF));

        int offset = 4;
        
        // 设置包类型和选项
        packet[offset++] = 0x01;
        packet[offset++] = 0x00;
        packet[offset++] = 0x16;
        packet[offset++] = 0x01;
        
        // 设置时间戳
        long ntpTimestamp = System.currentTimeMillis() + 2208988800000L;
        for (int i = 0; i < 8; i++) {
            packet[offset++] = (byte)((ntpTimestamp >> ((7 - i) * 8)) & 0xFF);
        }
        
        // 设置分辨率信息 (使用IEEE 754格式)
        int width = 1920;
        int height = 1080;
        
        writeFloat(packet, 16, width);  // 源宽度
        writeFloat(packet, 20, height); // 源高度
        
        // 清零保留字节
        for (int i = 24; i < 40; i++) {
            packet[i] = 0;
        }
        
        writeFloat(packet, 40, width);  // 重复源宽度
        writeFloat(packet, 44, height); // 重复源高度
        writeFloat(packet, 48, width);  // 其他宽度值
        writeFloat(packet, 52, height); // 其他高度值
        writeFloat(packet, 56, width);  // 显示宽度
        writeFloat(packet, 60, height); // 显示高度
        
        // 清零剩余保留字节
        for (int i = 64; i < 128; i++) {
            packet[i] = 0;
        }
        
        // 添加固定头部
        byte[] payloadHeader = new byte[]{0x01, 0x64, 0x00, 0x28, (byte)0xff, (byte)0xe1};
        System.arraycopy(payloadHeader, 0, packet, 128, 6);
        offset = 134;
        
        // 添加SPS长度（网络字节序）
        packet[offset++] = (byte)((sps.length >> 8) & 0xFF);
        packet[offset++] = (byte)(sps.length & 0xFF);
        System.arraycopy(sps, 0, packet, offset, sps.length);
        offset += sps.length;
        
        // 添加PPS长度（网络字节序）
        packet[offset++] = (byte)((pps.length >> 8) & 0xFF);
        packet[offset++] = (byte)(pps.length & 0xFF);
        System.arraycopy(pps, 0, packet, offset, pps.length);
        offset += pps.length;
        
        // 添加结尾数据
        packet[offset++] = 0x02;
        packet[offset++] = 0x00;
        packet[offset++] = 0x00;
        packet[offset] = 0x00;
        
        // 发送数据包前添加日志
        Log.d(TAG, "准备发送数据包，总大小: " + packet.length + " 字节");
        outputStream.write(packet);
        outputStream.flush();
        Log.d(TAG, "数据包发送完成");
    }

    // 将整数转换为IEEE 754浮点数格式
    private void writeFloat(byte[] buffer, int offset, int value) {
        float floatValue = (float)value;
        int bits = Float.floatToIntBits(floatValue);
        // 使用小端序（系统本地字节序）
        buffer[offset] = (byte)bits;
        buffer[offset + 1] = (byte)(bits >> 8);
        buffer[offset + 2] = (byte)(bits >> 16);
        buffer[offset + 3] = (byte)(bits >> 24);
    }

    private int parseSpsPps(byte[] data, int[] outStartIndex, int[] outNalLength) {
        int nals = 0;
        int offset = 0;
        while (offset + 4 < data.length) {
            // 查找开始码 0x00000001
            if (data[offset] == 0 && data[offset + 1] == 0 && 
                data[offset + 2] == 0 && data[offset + 3] == 1) {
                
                if (nals < 2) {
                    outStartIndex[nals] = offset + 4;
                    // 查找下一个开始码或文件结尾
                    int nextOffset = offset + 4;
                    while (nextOffset + 4 < data.length) {
                        if (data[nextOffset] == 0 && data[nextOffset + 1] == 0 &&
                            data[nextOffset + 2] == 0 && data[nextOffset + 3] == 1) {
                            break;
                        }
                        nextOffset++;
                    }
                    if (nextOffset + 4 >= data.length) {
                        outNalLength[nals] = data.length - outStartIndex[nals];
                    } else {
                        outNalLength[nals] = nextOffset - outStartIndex[nals];
                    }
                    nals++;
                }
                offset += 4;
            } else {
                offset++;
            }
        }
        return nals;
    }
}