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
                // 创建编码器
                android.media.MediaCodec encoder = android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_VIDEO_AVC);
                encoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
                
                // 获取输入Surface
                android.view.Surface inputSurface = encoder.createInputSurface();
                encoder.start();

                // 创建缓冲区接收编码数据
                android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
                
                // 开启编码处理线程
                new Thread(() -> {
                    int frameIndex = 0;
                    boolean firstFrame = true;  // 添加标记判断是否为第一帧
                    
                    while (!Thread.interrupted()) {
                        // 获取输出buffer
                        int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, -1);
                        if (outputBufferId >= 0) {
                            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                            
                            // 检查是否为IDR帧
                            boolean isIdrFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                            
                            // 如果是第一帧但不是IDR帧，则跳过这一帧
                            if (firstFrame && !isIdrFrame) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                                continue;
                            }
                            firstFrame = false;
                            
                            if (outputBuffer != null) {
                                // 处理编码后的数据
                                byte[] data = new byte[bufferInfo.size];
                                outputBuffer.get(data);
                                
                                android.util.Log.i("ProjectViaMoonlight", "收到 " + frameIndex + " 帧: " + data.length + "字节, " + (isIdrFrame ? "IDR帧" : "非IDR帧"));
                                nativeServer.postFrame(data, isIdrFrame, frameIndex++);
                            }
                            
                            // 释放buffer
                            encoder.releaseOutputBuffer(outputBufferId, false);
                        }
                    }
                }).start();
                
                // 创建虚拟显示器
                android.hardware.display.VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, dpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    inputSurface, null, null);
                    
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

}
