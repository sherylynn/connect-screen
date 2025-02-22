package com.gitee.connect_screen.job;

import android.media.MediaCodec;
import android.util.Log;
import com.gitee.connect_screen.NativeServer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class EncoderThread extends Thread {
    private static final String TAG = "EncoderThread";
    private final MediaCodec encoder;
    private final NativeServer nativeServer;
    private final MediaCodec.BufferInfo bufferInfo;
    private int frameIndex;
    private byte[] sps = null;
    private byte[] pps = null;
    private android.view.Surface inputSurface;

    public EncoderThread(android.media.MediaFormat format, NativeServer nativeServer) throws IOException {
        this.nativeServer = nativeServer;
        this.bufferInfo = new MediaCodec.BufferInfo();
        this.frameIndex = 1;
        
        // 创建编码器
        this.encoder = MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_VIDEO_AVC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        
        // 获取输入Surface
        this.inputSurface = encoder.createInputSurface();
        encoder.start();
    }

    public android.view.Surface getInputSurface() {
        return inputSurface;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, -1);
            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                
                boolean isIdrFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                
                if (outputBuffer != null) {
                    byte[] data = new byte[bufferInfo.size];
                    outputBuffer.get(data);
                    
                    // 解析并处理NALU
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // 配置帧包含SPS和PPS，需要分别保存
                        extractSpsPps(data);
                    } else {
                        byte[] processedData = processFrame(data, isIdrFrame);
                        if (processedData != null) {
                            String frameType = getFrameType(bufferInfo.flags);
                            Log.i(TAG, String.format(
                                "收到帧 %d: 大小=%d字节, 类型=%s, flags=0x%x", 
                                frameIndex, processedData.length, frameType, bufferInfo.flags));
                            logNaluTypes(processedData);
                            nativeServer.postFrame(processedData, isIdrFrame, frameIndex++);
                        }
                    }
                }
                
                encoder.releaseOutputBuffer(outputBufferId, false);
            }
        }
    }

    private void extractSpsPps(byte[] data) {
        // 打印完整的配置帧数据
        StringBuilder hexDump = new StringBuilder("配置帧数据: ");
        for (byte b : data) {
            hexDump.append(String.format("%02X ", b));
        }
        Log.d(TAG, hexDump.toString());

        int offset = 0;
        while (offset < data.length - 3) {
            // 查找起始码
            if (data[offset] == 0x00 && data[offset + 1] == 0x00 &&
                ((data[offset + 2] == 0x00 && data[offset + 3] == 0x01) ||
                 (data[offset + 2] == 0x01))) {
                
                // 确定NALU开始位置
                int naluStart = (data[offset + 2] == 0x01) ? offset + 3 : offset + 4;
                if (naluStart >= data.length) break;
                
                // 获取NALU类型
                int naluType = data[naluStart] & 0x1F;
                Log.d(TAG, String.format("发现NALU: 类型=%d, 位置=%d", naluType, naluStart));
                
                // 查找下一个起始码
                int nextNaluOffset = findNextStartCode(data, naluStart);
                if (nextNaluOffset == -1) {
                    nextNaluOffset = data.length;
                }
                
                // 计算NALU长度（包括起始码）
                int naluLength = nextNaluOffset - offset;
                byte[] naluData = new byte[naluLength];
                System.arraycopy(data, offset, naluData, 0, naluLength);
                
                if (naluType == 7) { // SPS
                    sps = naluData;
                    parseSps(naluData);
                    Log.i(TAG, "获取到SPS，长度: " + naluData.length + " 字节");
                } else if (naluType == 8) { // PPS
                    pps = naluData;
                    Log.i(TAG, "获取到PPS，长度: " + naluData.length + " 字节");
                }
                
                offset = nextNaluOffset;
            } else {
                offset++;
            }
        }
    }

    private int findNextStartCode(byte[] data, int offset) {
        for (int i = offset + 1; i < data.length - 3; i++) {
            if (data[i] == 0x00 && data[i + 1] == 0x00 &&
                ((data[i + 2] == 0x00 && data[i + 3] == 0x01) ||
                 (data[i + 2] == 0x01))) {
                return i;
            }
        }
        return -1;
    }

    private void parseSps(byte[] spsData) {
        try {
            // 检查SPS数据长度是否足够
            if (spsData.length < 6) {
                Log.w(TAG, "SPS数据长度不足: " + spsData.length + " 字节");
                return;
            }

            // 跳过起始码(4字节)和NALU头(1字节)
            int offset = 5;
            
            // 解析profile_idc
            int profile_idc = spsData[offset] & 0xFF;
            
            // 检查是否有足够的数据来解析level_idc
            if (spsData.length < offset + 2) {
                Log.w(TAG, "SPS数据不完整，无法解析level_idc");
                return;
            }

            // 跳过constraint_set标志和reserved_zero位(1字节)
            offset++;
            
            // 解析level_idc
            int level_idc = spsData[offset] & 0xFF;
            
            StringBuilder log = new StringBuilder();
            log.append("SPS解析结果:\n");
            log.append("Profile: ").append(getProfileString(profile_idc)).append("\n");
            log.append("Level: ").append(level_idc / 10.0).append("\n");
            log.append("总长度: ").append(spsData.length).append(" 字节");
            
            Log.i(TAG, log.toString());
        } catch (Exception e) {
            Log.e(TAG, "解析SPS失败", e);
        }
    }

    private String getProfileString(int profile_idc) {
        switch (profile_idc) {
            case 66: return "Baseline";
            case 77: return "Main";
            case 88: return "Extended";
            case 100: return "High";
            case 110: return "High 10";
            case 122: return "High 4:2:2";
            case 244: return "High 4:4:4";
            default: return "Unknown Profile(" + profile_idc + ")";
        }
    }

    private byte[] processFrame(byte[] data, boolean isIdrFrame) {
        if (!isValidFrame(data)) {
            return null;
        }

        try {
            // 如果是IDR帧，需要在前面添加SPS和PPS
            if (isIdrFrame && sps != null && pps != null) {
                // 计算总大小：原始数据 + SPS + PPS
                byte[] fullFrame = new byte[data.length + sps.length + pps.length];
                int offset = 0;
                
                // 复制SPS
                System.arraycopy(sps, 0, fullFrame, offset, sps.length);
                offset += sps.length;
                
                // 复制PPS
                System.arraycopy(pps, 0, fullFrame, offset, pps.length);
                offset += pps.length;
                
                // 复制原始帧数据
                System.arraycopy(data, 0, fullFrame, offset, data.length);
                
                return fullFrame;
            }
            
            // 非IDR帧直接返回原始数据
            return data;
        } catch (Exception e) {
            Log.e(TAG, "处理帧数据时出错", e);
            return null;
        }
    }

    private boolean isValidFrame(byte[] data) {
        // 检查是否只包含SPS和PPS
        boolean hasSps = false;
        boolean hasPps = false;
        boolean hasOtherNalu = false;
        
        int offset = 0;
        while (offset < data.length - 4) {
            int naluStart = findNaluStart(data, offset);
            if (naluStart == -1) break;
            
            int naluType = data[naluStart] & 0x1F;
            if (naluType == 7) hasSps = true;
            else if (naluType == 8) hasPps = true;
            else hasOtherNalu = true;
            
            offset = naluStart + 1;
        }
        
        return hasOtherNalu || !(hasSps && hasPps);
    }

    private int findNaluStart(byte[] data, int offset) {
        for (int i = offset; i < data.length - 4; i++) {
            if ((data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x00 && data[i + 3] == 0x01) ||
                (data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x01)) {
                return (data[i + 2] == 0x01) ? i + 3 : i + 4;
            }
        }
        return -1;
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