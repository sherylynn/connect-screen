package com.gitee.connect_screen;

import android.content.Context;
import android.os.IBinder;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.job.AndroidVersions;
import com.gitee.connect_screen.shizuku.PermissionManager;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;
import com.gitee.connect_screen.shizuku.SurfaceControl;
import com.termux.x11.MainActivity;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.Shizuku;

import java.util.HashSet;
import java.util.Set;

public class PureBlackActivity extends AppCompatActivity {
    // 添加 Set 来存储外部设备 ID
    private final Set<Integer> externalDeviceIds = new HashSet<>();
    private final boolean hasShizukuPermission = ShizukuUtils.hasPermission();
    private IInputManager inputManager;
    private boolean useRealScreenOff;

    // 将 ExitReceiver 修改为静态内部类
    public static class ExitReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.gitee.connect_screen.EXIT_PURE_BLACK".equals(intent.getAction())) {
                if (State.isInPureBlackActivity != null) {
                    State.isInPureBlackActivity.finish();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        State.isInPureBlackActivity = this;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 设置全屏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        // 支持刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        useRealScreenOff = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("use_real_screen_off", false);

        // 如果使用真实熄屏，则不显示黑色背景，直接让屏幕熄灭
        if (!useRealScreenOff) {
            // 设置状态栏和导航栏透明
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);

            // 设置纯黑背景
            View view = new View(this);
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(Color.BLACK);
            setContentView(view);
            
            // 添加鼠标捕获
            view.setOnGenericMotionListener((v, event) -> {
                TouchpadActivity.setFocus(inputManager, Display.DEFAULT_DISPLAY);
                view.requestFocus();
                view.requestFocusFromTouch();
                view.requestPointerCapture();
                return false;
            });
            
            view.setOnCapturedPointerListener((v, event) -> {
                // 检测鼠标中键按下事件
                if (event.getButtonState() == MotionEvent.BUTTON_STYLUS_PRIMARY) {
                    handleMouseMiddleButtonClick();
                    return true;
                }
                
                if (MainActivity.mInputHandler != null) {
                    MainActivity.lorieView.forceHasPointerCapture = true;
                    MainActivity.mInputHandler.handleTouchEvent(MainActivity.lorieView, MainActivity.lorieView, event);
                    MainActivity.lorieView.forceHasPointerCapture = false;
                }
                return true;
            });
            
            DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);

            // 修改触摸监听器
            view.setOnTouchListener((v, event) -> {
                if (isExternalDevice(event)) {
                    Display targetDisplay = displayManager.getDisplay(State.lastSingleAppDisplay);
                    if (targetDisplay == null)
                        return true;

                    // 获取原始坐标
                    float x = event.getX();
                    float y = event.getY();
                    
                    // 计算相对坐标
                    float relativeX = x / v.getWidth();
                    float relativeY = y / v.getHeight();

                    // 获取目标显示器的旋转角度
                    int rotation = targetDisplay.getRotation();
                    float targetWidth = targetDisplay.getWidth();
                    float targetHeight = targetDisplay.getHeight();
                    
                    // 根据旋转角度调整坐标映射
                    float mappedX, mappedY;
                    switch (rotation) {
                        case Surface.ROTATION_270:
                            mappedX = (1 - relativeY) * targetWidth;
                            mappedY = relativeX * targetHeight;
                            break;
                        case Surface.ROTATION_180:
                            mappedX = (1 - relativeX) * targetWidth;
                            mappedY = (1 - relativeY) * targetHeight;
                            break;
                        case Surface.ROTATION_90:
                            mappedX = relativeY * targetWidth;
                            mappedY = (1 - relativeX) * targetHeight;
                            break;
                        default: // Surface.ROTATION_0
                            mappedX = relativeX * targetWidth;
                            mappedY = relativeY * targetHeight;
                            break;
                    }
                    // 设置整后的坐标
                    event.setLocation(mappedX, mappedY);

                    MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                    motionEventHidden.setDisplayId(State.lastSingleAppDisplay);
                    ServiceUtils.getInputManager().injectInputEvent(event, 0);
                    return true;
                }
                finish();
                return true;
            });
        }
       if (ShizukuUtils.hasPermission()) {
           inputManager = ServiceUtils.getInputManager();
           TouchpadActivity.setFocus(inputManager, State.lastSingleAppDisplay);
           if(TouchpadAccessibilityService.getInstance() == null) {
               if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
                   // 获取现有的无障碍服务配置
                   String existingServices = Settings.Secure.getString(getContentResolver(), 
                       Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                   
                   // 准备新的服务字符串
                   String newService = "com.gitee.connect_screen/.TouchpadAccessibilityService";
                   String finalServices;
                   
                   // 如果已有配置，则追加新服务；否则直接使用新服务
                   if (existingServices != null && !existingServices.isEmpty()) {
                       finalServices = existingServices + ":" + newService;
                   } else {
                       finalServices = newService;
                   }
                   
                   // 更新无障碍服务配置
                   Settings.Secure.putString(getContentResolver(), 
                       Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, finalServices);
                   Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                   Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
                   this.startService(serviceIntent);
               }
           }
           // 确保真实熄屏所需的 userService 已就绪；若为空则先绑定后再尝试
           if (useRealScreenOff) {
               if (State.userService == null) {
                   try {
                       Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
                       Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
                   } catch (Throwable t) {
                       State.log("bind userService failed: " + t);
                   }
                   new Handler().postDelayed(this::powerOffScreen, 300);
               } else {
                   powerOffScreen();
               }
           }
       } else if(TouchpadAccessibilityService.getInstance() != null) {
           TouchpadActivity.setFocus(null, State.lastSingleAppDisplay);
       } else if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
           Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
           this.startService(serviceIntent);
           new Handler().postDelayed(() -> {
               TouchpadActivity.setFocus(null, State.lastSingleAppDisplay);
           }, 500);
       }
    }

    private void powerOffScreen() {
        if (useRealScreenOff && State.userService != null) {
            try {
                State.userService.startListenVolumeKey();
                State.userService.setScreenPower(SurfaceControl.POWER_MODE_OFF);
            } catch (RemoteException e) {
                State.log("powerOffScreen failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        State.isInPureBlackActivity = null;
        if (useRealScreenOff && State.userService != null) {
            try {
                State.userService.stopListenVolumeKey();
                State.userService.setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
            } catch (RemoteException e) {
                State.log("powerUpScreen failed: " + e.getMessage());
            }
        }
    }

    // 处理鼠标中键点击事件，循环切换屏幕
    private void handleMouseMiddleButtonClick() {
        // 检查设置中是否启用了鼠标中键切换屏幕功能
        boolean enableMouseSwitch = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("mouse_middle_button_switch_display", false);
        
        if (!enableMouseSwitch) {
            return;
        }
        
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        
        if (displays.length <= 1) {
            // 只有一个显示器，无需切换
            return;
        }
        
        // 获取当前鼠标所在的显示器ID（这里使用 State.lastSingleAppDisplay 作为当前显示）
        int currentDisplayId = State.lastSingleAppDisplay;
        
        // 找到下一个显示器
        int nextDisplayId = Display.DEFAULT_DISPLAY;
        for (int i = 0; i < displays.length; i++) {
            if (displays[i].getDisplayId() == currentDisplayId) {
                // 找到下一个显示器（循环）
                int nextIndex = (i + 1) % displays.length;
                nextDisplayId = displays[nextIndex].getDisplayId();
                break;
            }
        }
        
        // 切换到下一个显示器
        if (nextDisplayId != currentDisplayId && inputManager != null) {
            State.lastSingleAppDisplay = nextDisplayId;
            TouchpadActivity.setFocus(inputManager, nextDisplayId);
            Log.d("PureBlackActivity", "鼠标中键切换屏幕: " + currentDisplayId + " -> " + nextDisplayId);
        }
    }

    private boolean isExternalDevice(MotionEvent event) {
        if (!hasShizukuPermission) {
            return false;
        }
        int deviceId = event.getDeviceId();
        if (externalDeviceIds.contains(deviceId)) {
            return true;
        }
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device != null) {
            if (device.isExternal()) {
                externalDeviceIds.add(deviceId);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (useRealScreenOff) {
           if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
               // 让系统处理音量调节
               super.onKeyDown(keyCode, event);
               // 关闭当前Activity
               finish();
               return true;
           }
        }
        return super.onKeyDown(keyCode, event);
    }
}