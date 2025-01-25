package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.ExitAll;
import com.gitee.connect_screen.job.ListenOpenglAndPostFrame;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import android.util.Log;

public class MirrorHomeFragment extends Fragment {
    private static final String TAG = "MirrorHomeFragment";
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private static final String SERVICE_TYPE = "_airplay._tcp.";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String RTSP_INFO_REQUEST = 
        "OPTIONS * RTSP/1.0\r\n" +
        "CSeq: 1\r\n" +
        "User-Agent: AirPlay/1.0\r\n\r\n";
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        Button nsdSearchBtn = view.findViewById(R.id.nsdSearchBtn);
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

        nsdSearchBtn.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startNsdDiscovery();
            }
        });

        return view;
    }

    private boolean checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.NEARBY_WIFI_DEVICES},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void startNsdDiscovery() {
        nsdManager = (NsdManager) requireContext().getSystemService(Context.NSD_SERVICE);
        
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "搜索启动失败: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "停止搜索失败: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.i(TAG, "开始搜索 AirPlay 设备...");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "停止搜索");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo.getServiceType().contains("_airplay") && serviceInfo.getServiceName().contains("UxPlay")) {
                    Log.i(TAG, "发现服务: " + serviceInfo.getServiceName());
                    Log.i(TAG, "服务类型: " + serviceInfo.getServiceType());
                    Log.i(TAG, "服务端口: " + serviceInfo.getPort());
                    if (serviceInfo.getHost() != null) {
                        Log.i(TAG, "服务地址: " + serviceInfo.getHost().getHostAddress());
                    }
                    if (serviceInfo.getAttributes() != null && !serviceInfo.getAttributes().isEmpty()) {
                        Log.i(TAG, "服务属性: " + serviceInfo.getAttributes().toString());
                    }
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "解析服务失败: " + errorCode);
                        }

                        @Override 
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            String deviceName = serviceInfo.getServiceName();
                            String host = serviceInfo.getHost().getHostAddress();
                            int port = serviceInfo.getPort();
                            
                            // 使用新的线程类
                            new RtspConnectionThread(host, port, requireContext(), MirrorHomeFragment.this).start();
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "设备离线: " + serviceInfo.getServiceName());
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException e) {
                // 忽略已停止的搜索
            }
        }
    }

    private static class RtspConnectionThread extends Thread {
        private final String host;
        private final int port;
        private final Context context;
        private final MirrorHomeFragment fragment;

        public RtspConnectionThread(String host, int port, Context context, MirrorHomeFragment fragment) {
            this.host = host;
            this.port = port;
            this.context = context;
            this.fragment = fragment;
        }

        @Override
        public void run() {
            try {
                Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                
                // 发送 RTSP INFO 请求
                out.print(RTSP_INFO_REQUEST);
                out.flush();
                
                // 读取响应
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    response.append(line).append("\n");
                }
                
                // 在主线程显示响应
                fragment.requireActivity().runOnUiThread(() -> {
                    Log.i(TAG, "收到响应：" + response.toString());
                });
                
                socket.close();
            } catch (IOException e) {
                fragment.requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "连接失败：" + e.getMessage());
                });
            }
        }
    }
}