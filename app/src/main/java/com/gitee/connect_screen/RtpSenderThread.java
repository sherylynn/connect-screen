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
                sendSPSPPSPacket(sps, pps);
                Log.i(TAG, "SPS 和 PPS 发送完成");
            }

            Log.i(TAG, "开始主循环发送视频数据");
            // 开始发送编码后的视频数据
            while (running) {
                int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    if (outputBuffer != null) {
                        // 发送视频数据包
                        sendVideoPacket(outputBuffer, bufferInfo);
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

    private void sendVideoPacket(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) throws IOException {
        Log.v(TAG, String.format("发送视频包: 大小=%d, 时间戳=%d, 标志=%d", 
            bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags));
        // 创建128字节的数据包头
        byte[] header = new byte[128];
        
        // 设置负载大小
        header[0] = (byte)((bufferInfo.size >> 24) & 0xFF);
        header[1] = (byte)((bufferInfo.size >> 16) & 0xFF);
        header[2] = (byte)((bufferInfo.size >> 8) & 0xFF);
        header[3] = (byte)(bufferInfo.size & 0xFF);
        
        // 设置包类型 (根据是否为关键帧)
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            header[4] = 0x00;
            header[5] = 0x10; // IDR frame
        } else {
            header[4] = 0x00;
            header[5] = 0x00; // Non-IDR frame
        }
        
        // 设置当前时间戳
        long ntpTimestamp = System.currentTimeMillis() + 2208988800000L; // NTP时间戳
        for (int i = 0; i < 8; i++) {
            header[8 + i] = (byte)((ntpTimestamp >> ((7 - i) * 8)) & 0xFF);
        }
        
        // 发送数据包头
        socket.getOutputStream().write(header);
        
        // 发送视频数据
        byte[] data = new byte[bufferInfo.size];
        buffer.get(data);
        socket.getOutputStream().write(data);
        socket.getOutputStream().flush();
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

    private void sendSPSPPSPacket(byte[] sps, byte[] pps) throws IOException {
        // 计算总负载大小：6字节头部 + 2字节SPS大小 + SPS数据 + 2字节PPS大小 + PPS数据
        int payloadSize = 6 + 2 + sps.length + 2 + pps.length;
        
        // 创建128字节的数据包头
        byte[] header = new byte[128];
        
        // 设置负载大小
        header[0] = (byte)((payloadSize >> 24) & 0xFF);
        header[1] = (byte)((payloadSize >> 16) & 0xFF);
        header[2] = (byte)((payloadSize >> 8) & 0xFF);
        header[3] = (byte)(payloadSize & 0xFF);
        
        // 设置包类型为SPS+PPS (0x01 0x00)
        header[4] = 0x01;
        header[5] = 0x00;
        
        // 设置payload选项 (0x16 0x01)
        header[6] = 0x16;
        header[7] = 0x01;
        
        // 设置NTP时间戳
        long ntpTimestamp = System.currentTimeMillis() + 2208988800000L;
        for (int i = 0; i < 8; i++) {
            header[8 + i] = (byte)((ntpTimestamp >> ((7 - i) * 8)) & 0xFF);
        }
        
        // 设置分辨率信息
        setFloatValue(header, 16, 1920); // 源宽度
        setFloatValue(header, 20, 1080); // 源高度
        setFloatValue(header, 40, 1920); // 重复源宽度
        setFloatValue(header, 44, 1080); // 重复源高度
        setFloatValue(header, 56, 1920); // 显示宽度
        setFloatValue(header, 60, 1080); // 显示高度
        
        // 发送数据包头
        socket.getOutputStream().write(header);
        
        // 发送负载数据
        // 1. 发送6字节的头部
        byte[] payloadHeader = new byte[]{0x00, 0x00, 0x00, 0x01, 0x67, 0x42};
        socket.getOutputStream().write(payloadHeader);
        
        // 2. 发送SPS (先发送2字节大小，再发送数据)
        socket.getOutputStream().write((byte)((sps.length >> 8) & 0xFF));
        socket.getOutputStream().write((byte)(sps.length & 0xFF));
        socket.getOutputStream().write(sps);
        
        // 3. 发送PPS (先发送2字节大小，再发送数据)
        socket.getOutputStream().write((byte)((pps.length >> 8) & 0xFF));
        socket.getOutputStream().write((byte)(pps.length & 0xFF));
        socket.getOutputStream().write(pps);
        
        socket.getOutputStream().flush();
    }

    private void setFloatValue(byte[] buffer, int offset, int value) {
        // 将整数值转换为浮点数格式 (x.0000)
        buffer[offset] = (byte)((value >> 24) & 0xFF);
        buffer[offset + 1] = (byte)((value >> 16) & 0xFF);
        buffer[offset + 2] = 0x00;
        buffer[offset + 3] = 0x00;
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