package com.gitee.connect_screen;

import com.gitee.connect_screen.job.ProjectViaMoonlight;

public class NativeServer {
    static {
        System.loadLibrary("server");
    }
    
    private static NativeServer instance;
    public byte[] gcmKey;
    public byte[] iv;
    public String peerIp;

    private NativeServer() {}
    
    public static NativeServer getInstance() {
        if (instance == null) {
            instance = new NativeServer();
        }
        return instance;
    }
    
    public native void startServer(byte[] gcmKey, byte[] iv, String peerIp, String configStr);
    public native void stopServer();
    public native void postFrame(byte[] frameData, boolean isIdr, long frameIndex);
    public native int ping();
    
    public void onMoonlightConnected() {
        android.util.Log.i("NativeServer", "Moonlight 客户端已连接");
        
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (State.getMediaProjection() == null) {
                State.startNewJob(new ProjectViaMoonlight(this));
            }
        });
    }
} 