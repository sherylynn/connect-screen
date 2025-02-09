package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.ExitAll;
import com.gitee.connect_screen.job.ListenOpenglAndPostFrame;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;

public class MirrorHomeFragment extends Fragment {
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private NvHTTP nvHttp;
    private NvHTTPS nvHttps;
    private RTSPServer rtspServer;
    private VideoServer videoServer;

    static {
        System.loadLibrary("server");
    }
    
    private native void startServer();
    private native void stopServer();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        TextView mirrorStatus = view.findViewById(R.id.mirrorStatus);
        if (MirrorActivity.getInstance() != null) {
            mirrorStatus.setText("镜像投屏中");
        } else {
            mirrorStatus.setText("请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞");
        }

        settingsBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("设置", () -> new MirrorSettingsFragment());
        });

        exitBtn.setOnClickListener(v -> {
            ExitAll.execute(requireContext());
        });

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        byte[] caCert = new byte[0];
        byte[] caKey = new byte[0];
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
        initializeNsdService(context);
    }

    private void initializeNsdService(Context context) {
        new Thread(() -> {
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
                nvHttps = new NvHTTPS();
                rtspServer = new RTSPServer();
                videoServer = new VideoServer();
                startServer();
                try {
                    nvHttp.start();
                    nvHttps.start();
                    rtspServer.start();
                    videoServer.start();
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
        }).start();
    }

    @Override
    public void onDestroy() {
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
        stopServer();
        super.onDestroy();
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
}