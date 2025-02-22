package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.MediaProjectionService;
import com.gitee.connect_screen.NativeServer;
import com.gitee.connect_screen.State;

import java.nio.ByteBuffer;

public class ProjectViaMoonlight implements Job {
    private final NativeServer nativeServer;
    private boolean mediaProjectionRequested;

    public ProjectViaMoonlight(NativeServer nativeServer) {
        this.nativeServer = nativeServer;
    }

    @Override
    public void start() throws YieldException {
        if (requestMediaProjectionPermission(State.currentActivity.get())) {
            MediaProjection mediaProjection = State.getMediaProjection();
            // 创建1080p虚拟显示器
            int width = 1920;
            int height = 1080;
            int dpi = 160;
            
            // 配置MediaCodec编码参数
            android.media.MediaFormat format = android.media.MediaFormat.createVideoFormat(android.media.MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 8000000); // 8Mbps
            format.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 60);
            format.setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            
            try {
                // 创建并启动编码处理线程
                EncoderThread encoderThread = new EncoderThread(format, nativeServer);
                encoderThread.start();
                
                // 创建虚拟显示器
                android.hardware.display.VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, dpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    encoderThread.getInputSurface(), null, null);
                    
                State.mirrorVirtualDisplay = virtualDisplay;
                State.log("已创建1080p虚拟显示器并开始H.264编码");
                
            } catch (Exception e) {
                State.log("创建编码器或虚拟显示器失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private boolean requestMediaProjectionPermission(Context context) throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            if (MediaProjectionService.isStarting && MediaProjectionService.instance == null) {
                throw new YieldException("等待服务启动");
            }
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        MediaProjectionService.isStarting = true;
        mediaProjectionRequested = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            State.currentActivity.get().startActivityForResult(captureIntent, MainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
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
                    String naluTypeStr = "";
                    
                    // 解析NALU类型
                    switch (naluType) {
                        case 1: naluTypeStr = "SLICE"; break;
                        case 5: naluTypeStr = "IDR"; break;
                        case 6: naluTypeStr = "SEI"; break;
                        case 7: naluTypeStr = "SPS"; break;
                        case 8: naluTypeStr = "PPS"; break;
                        default: naluTypeStr = "TYPE_" + naluType;
                    }
                    
                    naluInfo.append(naluTypeStr).append(" ");
                }
            }
        }
        
        android.util.Log.i("ProjectViaMoonlight", "NALU类型: " + naluInfo.toString());
    }

}
