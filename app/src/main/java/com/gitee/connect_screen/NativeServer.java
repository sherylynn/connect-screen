package com.gitee.connect_screen;

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
} 