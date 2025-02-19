package com.gitee.connect_screen;

import com.gitee.connect_screen.job.ProjectViaMoonlight;

public class NativeServer {
    static {
        System.loadLibrary("server");
    }
    
    private static NativeServer instance;
    
    private NativeServer() {}
    
    public static NativeServer getInstance() {
        if (instance == null) {
            instance = new NativeServer();
        }
        return instance;
    }
    
    public native void startServer(byte[] gcmKey, byte[] iv, String peerIp);
    public native void stopServer();
    public native void postFrame(byte[] frameData, boolean isIdr, long frameIndex);
    
    public void onMoonlightConnected() {
        android.util.Log.i("NativeServer", "Moonlight 客户端已连接");
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            State.startNewJob(new ProjectViaMoonlight(this));
        });
    }
} 