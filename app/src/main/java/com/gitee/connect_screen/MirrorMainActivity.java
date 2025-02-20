package com.gitee.connect_screen;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.widget.FrameLayout;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.DisplayMonitor;
import com.gitee.connect_screen.job.InputDeviceMonitor;
import com.gitee.connect_screen.job.MirrorDisplayMonitor;
import com.gitee.connect_screen.job.ProjectViaDisplaylink;
import com.gitee.connect_screen.job.MirrorDisplaylinkMonitor;
import com.gitee.connect_screen.job.ProjectViaMoonlight;
import com.gitee.connect_screen.job.VirtualDisplayArgs;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.Shizuku;

public class MirrorMainActivity extends AppCompatActivity implements IMainActivity {
    public static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";
    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001; // 定义一个请求码

    private BreadcrumbManager breadcrumbManager;
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    // 添加时间戳和延迟常量
    private static final long MONITOR_INIT_DELAY = 3000; // 5秒延迟
    private long lastCheckTime = 0;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private NvHTTP nvHttp;
    private NvHTTPS nvHttps;
    private RTSPServer rtspServer;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                State.resumeJob();
            }
        }
    };

    
    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "成功添加隐藏API豁免");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "添加隐藏API豁免失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        if (ShizukuUtils.hasPermission() && State.userService == null) {
            Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
            Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
        }

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        breadcrumbManager = new BreadcrumbManager(this, getSupportFragmentManager(), findViewById(R.id.breadcrumb));
        State.breadcrumbManager = breadcrumbManager;
        breadcrumbManager.pushBreadcrumb("首页", () -> new MirrorHomeFragment());

        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);

        // 初始化日志列表
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);

        // 获取启动 Intent 并打印其 Action 到日志
        Intent intent = getIntent();
        String action = intent.getAction();
        State.log("MainActivity created with action: " + action);

        // 查是否是 USB 设备连接的 Intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            MirrorDisplaylinkMonitor.onUsbDeviceAttached(this, device);
        }

        // 注册 USB 权限广播接收器
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        // 监听显示器变化
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        lastCheckTime = System.currentTimeMillis();
        MirrorDisplayMonitor.init(displayManager);
        MirrorDisplaylinkMonitor.init(this);


        NativeServer nativeServer = NativeServer.getInstance();
        // 启动定时ping线程
        new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    nativeServer.ping();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
        if (State.getMediaProjection() == null) {
            State.startNewJob(new ProjectViaMoonlight(nativeServer));
        }
        byte[] caCert = new byte[0];
        byte[] caKey = new byte[0];
        Context context = getApplicationContext();
        try (InputStream certStream = context.getAssets().open("cacert.pem");
             InputStream keyStream = context.getAssets().open("cakey.pem")) {
            caCert = new byte[certStream.available()];
            caKey = new byte[keyStream.available()];
            certStream.read(caCert);
            keyStream.read(caKey);
        } catch (IOException e) {
            android.util.Log.e("MirrorHomeFragment", "无法读取证书文件", e);
        }
        NvHTTP.CA_CERT = caCert;
        NvHTTP.CA_KEY = caKey;

        try {
            InetAddress addr = getWifiIpAddress(context);
            if (addr == null) {
                android.util.Log.e("MirrorHomeFragment", "无法获取WiFi IP地址");
                return;
            }
            android.util.Log.i("MirrorHomeFragment", "获取到WiFi IP地址: " + addr.getHostAddress());

            // 启动 HTTP 服务器
            NvHTTP.ADDRESS = addr;
            nvHttp = new NvHTTP(addr);
            nvHttps = new NvHTTPS(nativeServer);
            rtspServer = new RTSPServer(nativeServer);
            try {
                nvHttp.start();
                nvHttps.start();
                rtspServer.start();
                android.util.Log.i("MirrorHomeFragment", "NvHTTP服务器启动成功，端口: " + NvHTTP.HTTP_PORT);
            } catch (IOException e) {
                android.util.Log.e("MirrorHomeFragment", "NvHTTP服务器启动失败", e);
                return;
            }

            jmdns = JmDNS.create(addr);
            serviceInfo = ServiceInfo.create(
                    "_nvstream._tcp.local.",
                    "MirrorScreen",
                    NvHTTP.HTTP_PORT,
                    "ConnectScreen"
            );

            jmdns.registerService(serviceInfo);
            android.util.Log.i("MirrorHomeFragment", "JmDNS服务注册成功");
        } catch (IOException e) {
            android.util.Log.e("MirrorHomeFragment", "初始化网络服务失败", e);
        }
    }

    public static InetAddress getWifiIpAddress(Context context) throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            return null;
        }

        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        // Convert little-endian to big-endian if needed
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (ipAddress & 0xFF);
        bytes[1] = (byte) ((ipAddress >> 8) & 0xFF);
        bytes[2] = (byte) ((ipAddress >> 16) & 0xFF);
        bytes[3] = (byte) ((ipAddress >> 24) & 0xFF);

        return InetAddress.getByAddress(bytes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.currentActivity = new WeakReference<>(this);
        State.resumeJob();
        
        // 检查时间间隔
        if (MirrorActivity.getInstance() == null && 
            (System.currentTimeMillis() - lastCheckTime > MONITOR_INIT_DELAY)) {
            lastCheckTime = System.currentTimeMillis(); // 记录时间戳
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            MirrorDisplayMonitor.init(displayManager);
            MirrorDisplaylinkMonitor.init(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.unbindUserService();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        State.currentActivity = null;
        unregisterReceiver(usbPermissionReceiver);


        if (nvHttp != null) {
            nvHttp.stop();
        }
        if (jmdns != null) {
            try {
                jmdns.unregisterService(serviceInfo);
                jmdns.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                lastCheckTime = System.currentTimeMillis(); // 记录时间戳
                if (MediaProjectionService.instance != null) {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop 回调");
                        }
                    }, null);
                    State.resumeJob();
                } else {
                    Intent serviceIntent = new Intent(this, MediaProjectionService.class);
                    serviceIntent.putExtra("data", data);
                    startService(serviceIntent);
                }
            } else {
                MediaProjectionService.isStarting = false;
                State.log("用户拒绝了投屏权限");
                State.resumeJob();
            }
        }
    }

    // 添加一个方法来检查服务是否在运行
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        breadcrumbManager.popBreadcrumb();
    }
    
    // 更新日志列表的方法
    public void updateLogs() {
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
            logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }
} 