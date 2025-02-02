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
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.ExitAll;
import com.gitee.connect_screen.job.ListenOpenglAndPostFrame;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

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
        initializeNsdService();
    }
    
    private void initializeNsdService() {
        try {
            InetAddress addr = getWifiIpAddress(getContext());
            if (addr == null) {
                Toast.makeText(requireContext(), "无法获取WiFi IP地址", Toast.LENGTH_SHORT).show();
                return;
            }

            jmdns = JmDNS.create(addr);
            serviceInfo = ServiceInfo.create(
                "_nvstream._tcp.local.",  // 服务类型
                "MirrorScreen",           // 服务名称
                8000,                     // 端口
                "ConnectScreen"              // 服务描述
            );
            
            jmdns.registerService(serviceInfo);
            Toast.makeText(requireContext(), "MDNS服务注册成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "MDNS服务注册失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        if (jmdns != null) {
            try {
                jmdns.unregisterService(serviceInfo);
                jmdns.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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