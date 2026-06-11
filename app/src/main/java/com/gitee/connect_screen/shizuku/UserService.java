package com.gitee.connect_screen.shizuku;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;

import androidx.annotation.Keep;

import com.gitee.connect_screen.State;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private volatile boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private volatile boolean keepScreenOff = false;
    private volatile boolean userExited = false;
    private Thread screenOffLoopThread;
    private static final long SCREEN_OFF_CHECK_INTERVAL = 100; // 检查间隔（毫秒），缩短为100ms以更快响应系统唤醒

    public UserService() {
        Log.i("UserService", "constructor");
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        Log.i("UserService", "constructor with Context: context=" + context.toString());
    }
    
    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/安卓屏连.log");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "logcat -d failed", e);
            throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
        }
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys input");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            reader.close();
            process.waitFor();
            
            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    public void setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        if (Build.VERSION.SDK_INT >= 35) {
            if (powerMode == SurfaceControl.POWER_MODE_OFF) {
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                    Log.i("UserService", "requestDisplayPower by bool");
                } catch(Throwable e) {
                    Log.e("UserService", "failed to power off screen", e);
                    try {
                        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                        Log.i("UserService", "requestDisplayPower by int");
                    } catch(Throwable e2) {
                        Log.e("UserService", "failed to power off screen", e2);
                    }
                }
            } else {
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                    Log.i("UserService", "requestDisplayPower by bool");
                } catch (Throwable e) {
                    Log.e("UserService", "failed to power up screen", e);
                    try {
                        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                        Log.i("UserService", "requestDisplayPower by int");
                    } catch(Throwable e2) {
                        Log.e("UserService", "failed to power up screen", e2);
                    }
                }
            }
        } else {
            IBinder d = SurfaceControl.getBuiltInDisplay();
            if (d == null) {
                Log.i("UserService", "Could not get built-in display");
            } else {
                SurfaceControl.setDisplayPowerMode(d, powerMode);
                Log.i("UserService", "setDisplayPowerMode success");
            }
        }
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey && keepScreenOff && screenOffLoopThread != null && screenOffLoopThread.isAlive()) {
            Log.i("UserService", "startListenVolumeKey: already listening and loop is running, keepScreenOff=" + keepScreenOff);
            return;
        }
        Log.i("UserService", "startListenVolumeKey: starting new loop, previous listenVolumeKey=" + listenVolumeKey + " keepScreenOff=" + keepScreenOff);
        userExited = false;
        listenVolumeKey = true;
        keepScreenOff = true;
        
        // 启动循环熄屏线程（先启动，确保即使音量键监听失败也能保持熄屏）
        screenOffLoopThread = new Thread(() -> {
            Log.i("UserService", "screen off loop started");
            while (keepScreenOff) {
                try {
                    setScreenPower(SurfaceControl.POWER_MODE_OFF);
                    Thread.sleep(SCREEN_OFF_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    Log.i("UserService", "screen off loop interrupted");
                    break;
                } catch (Throwable e) {
                    // 捕获所有异常，防止循环线程崩溃
                    Log.e("UserService", "screen off loop error, continuing...", e);
                    try {
                        Thread.sleep(SCREEN_OFF_CHECK_INTERVAL);
                    } catch (InterruptedException ie) {
                        Log.i("UserService", "screen off loop interrupted during error recovery");
                        break;
                    }
                }
            }
            Log.i("UserService", "screen off loop stopped");
        });
        screenOffLoopThread.start();
        
        // 启动音量键监听线程
        Thread thread = new Thread(() -> {
            try {
                listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                while (true) {
                    String line = reader.readLine();
                    if (line == null || !listenVolumeKey) {
                        break;
                    }
                    if (!line.endsWith("0000 0000 00000000") &&
                        (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                        Log.i("UserService", "volume key pressed, exiting pure black activity");
                        userExited = true;
                        keepScreenOff = false;
                        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                        if (context != null) {
                            Intent intent = new Intent("com.gitee.connect_screen.EXIT_PURE_BLACK");
                            intent.setPackage("com.gitee.connect_screen");
                            context.sendBroadcast(intent);
                        } else {
                            Log.i("UserService", "context is null, can not send EXIT_PURE_BLACK");
                        }
                    }
                }
                reader.close();
                listenVolumeKeyProcess.waitFor();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    listenVolumeKeyProcess.destroyForcibly();
                } else {
                    listenVolumeKeyProcess.destroy();
                }
            } catch (Exception e) {
                Log.e("UserService", "Listen volume key failed", e);
                // 音量键监听失败不影响循环熄屏，只记录日志
            }
        });
        volumeKeyThread = thread;
        thread.start();
    }

    public void stopListenVolumeKey() {
        Log.i("UserService", "stopListenVolumeKey called");
        listenVolumeKey = false;
        keepScreenOff = false;
        
        // 停止循环熄屏线程
        if (screenOffLoopThread != null) {
            screenOffLoopThread.interrupt();
            try {
                screenOffLoopThread.join(1000);
            } catch (InterruptedException e) {
                Log.e("UserService", "join screenOffLoopThread failed", e);
            }
            screenOffLoopThread = null;
        }
        
        // 停止音量键监听进程和线程
        if (listenVolumeKeyProcess != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                listenVolumeKeyProcess.destroyForcibly();
            } else {
                listenVolumeKeyProcess.destroy();
            }
            listenVolumeKeyProcess = null;
        }
        if (volumeKeyThread != null) {
            volumeKeyThread.interrupt();
            volumeKeyThread = null;
        }
    }

    public boolean isLoopActive() {
        return !userExited && listenVolumeKey;
    }
}
