package com.gitee.connect_screen.job;

import android.media.MediaCodec;
import android.util.Log;
import com.gitee.connect_screen.NativeServer;
import java.nio.ByteBuffer;

public class EncoderThread extends Thread {
    private static final String TAG = "EncoderThread";
    private final MediaCodec encoder;
    private final NativeServer nativeServer;
    private final MediaCodec.BufferInfo bufferInfo;
    private int frameIndex;

    public EncoderThread(MediaCodec encoder, NativeServer nativeServer) {
        this.encoder = encoder;
        this.nativeServer = nativeServer;
        this.bufferInfo = new MediaCodec.BufferInfo();
        this.frameIndex = 1;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            // 获取输出buffer
            int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, -1);
            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                
                // 检查是否为IDR帧
                boolean isIdrFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                
                if (outputBuffer != null) {
                    // 处理编码后的数据
                    byte[] data = new byte[bufferInfo.size];
                    outputBuffer.get(data);
                    
                    String frameType = getFrameType(bufferInfo.flags);
                    
                    Log.i(TAG, String.format(
                        "收到帧 %d: 大小=%d字节, 类型=%s, flags=0x%x", 
                        frameIndex, data.length, frameType, bufferInfo.flags));
                    // 添加NALU解析日志
                    logNaluTypes(data);
                    nativeServer.postFrame(data, isIdrFrame, frameIndex++);
                }
                
                // 释放buffer
                encoder.releaseOutputBuffer(outputBufferId, false);
            }
        }
    }

    private String getFrameType(int flags) {
        if ((flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            return "配置帧";
        } else if ((flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            return "关键帧";
        } else {
            return "普通帧";
        }
    }

    private void logNaluTypes(byte[] data) {
        // 用于存储找到的NALU类型
        StringBuilder naluInfo = new StringBuilder();
        
        // 查找NALU起始码 (0x00 0x00 0x00 0x01 或 0x00 0x00 0x01)
        for (int i = 0; i < data.length - 4; i++) {
            if ((data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x00 && data[i + 3] == 0x01) ||
                (data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x01)) {
                
                // 确定NALU头的位置
                int naluStart = (data[i + 2] == 0x01) ? i + 3 : i + 4;
                if (naluStart < data.length) {
                    // 获取NALU类型 (低5位)
                    int naluType = data[naluStart] & 0x1F;
                    String naluTypeStr = getNaluTypeString(naluType);
                    naluInfo.append(naluTypeStr).append(" ");
                }
            }
        }
        
        Log.i(TAG, "NALU类型: " + naluInfo.toString());
    }

    private String getNaluTypeString(int naluType) {
        switch (naluType) {
            case 1: return "SLICE";
            case 5: return "IDR";
            case 6: return "SEI";
            case 7: return "SPS";
            case 8: return "PPS";
            default: return "TYPE_" + naluType;
        }
    }
} 