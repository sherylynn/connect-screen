package com.gitee.connect_screen;

import android.content.Context;
import android.media.projection.MediaProjectionConfig;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dd.plist.NSArray;
import com.gitee.connect_screen.job.ExitAll;
import com.dd.plist.PropertyListParser;
import com.dd.plist.NSDictionary;
import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.net.Socket;
import android.util.Log;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.widget.Toast;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.net.NetworkInterface;
import java.lang.StringBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MirrorHomeFragment extends Fragment {
    private static final String TAG = "MirrorHomeFragment";
    private static final int NTP_PORT = 55606;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private static final String SERVICE_TYPE = "_airplay._tcp.";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String RTSP_OPTIONS_REQUEST =
        "OPTIONS * RTSP/1.0\r\n" +
        "CSeq: 1\r\n" +
        "User-Agent: AirPlay/1.0\r\n\r\n";
    
    private NtpServer ntpServer;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        Button nsdSearchBtn = view.findViewById(R.id.nsdSearchBtn);
        Button projectBtn = view.findViewById(R.id.projectBtn);
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

        projectBtn.setOnClickListener(v -> {
            MediaProjectionManager mediaProjectionManager = 
                (MediaProjectionManager) requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mediaProjectionManager != null) {
                Intent captureIntent;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    captureIntent = mediaProjectionManager.createScreenCaptureIntent(
                        MediaProjectionConfig.createConfigForDefaultDisplay());
                } else {
                    captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                }
                requireActivity().startActivityForResult(captureIntent, MainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            } else {
                Toast.makeText(requireContext(), "无法获取 MediaProjectionManager 服务", Toast.LENGTH_SHORT).show();
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
                            Log.i(TAG, "服务解析成功");
                            Log.i(TAG, "设备名称: " + deviceName);
                            Log.i(TAG, "设备地址: " + host);
                            Log.i(TAG, "设备端口: " + port);
                            
                            // 先启动 NTP 服务器
                            ntpServer = new NtpServer(NTP_PORT);
                            ntpServer.start();
                            
                            // 然后启动 RTSP 连接线程
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
        // 停止 NTP 服务器
        if (ntpServer != null) {
            ntpServer.interrupt();
        }
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException e) {
                // 忽略已停止的搜索
            }
        }
    }

    private void logOnMainThread(String message) {
        requireActivity().runOnUiThread(() -> {
            Log.i(TAG, message);
        });
    }

    private static class RtspConnectionThread extends Thread {
        private static final byte[] FP_SETUP_REQUEST = new byte[] {
            0x46, 0x50, 0x4c, 0x59, 0x03, 0x01, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x04, 0x02, 0x00, 0x01, (byte)0xbb
        };

        public static final byte[] FP_SETUP_REQUEST_2 = new byte[] {
            0x46, 0x50, 0x4c, 0x59, 0x03, 0x01, 0x03, 0x00, 0x00, 0x00, 0x00, (byte)0x98, 0x01, (byte)0x8f, 0x1a, (byte)0x9c,
            (byte)0xaf, 0x6c, 0x47, 0x49, (byte)0xf8, (byte)0xb2, 0x09, (byte)0xba, (byte)0xdf, (byte)0xe3, 0x67, (byte)0xf9, 0x7d, (byte)0x85, (byte)0xf7, 0x0d,
            (byte)0xd6, (byte)0x80, 0x67, (byte)0xdd, 0x33, (byte)0xca, 0x4a, 0x57, (byte)0xe7, 0x3c, (byte)0xaf, (byte)0xa4, (byte)0xaf, 0x28, 0x72, (byte)0xb1,
            0x04, (byte)0xf0, (byte)0xab, 0x6b, (byte)0xb0, 0x2b, 0x5b, 0x5d, 0x40, 0x5f, (byte)0xfd, (byte)0xa7, 0x05, (byte)0xae, 0x0e, 0x78,
            (byte)0xbe, (byte)0xf7, (byte)0xeb, (byte)0x9f, 0x43, (byte)0xcd, (byte)0x90, (byte)0xe1, 0x13, 0x79, (byte)0xdb, (byte)0xfc, (byte)0xca, (byte)0xc4, 0x54, (byte)0xac,
            (byte)0x86, 0x42, (byte)0xc9, (byte)0xdb, 0x4e, 0x2e, (byte)0xaf, 0x47, (byte)0xef, (byte)0xdf, (byte)0xcd, 0x09, (byte)0xfa, 0x3f, (byte)0xb8, 0x78,
            (byte)0xa0, (byte)0xb8, 0x1d, (byte)0xd3, (byte)0x85, 0x09, (byte)0xf6, 0x6c, 0x46, 0x49, (byte)0x9b, (byte)0xcf, (byte)0xc6, (byte)0xd9, (byte)0xf0, (byte)0xa3,
            0x7c, (byte)0xfd, (byte)0xf7, (byte)0xc9, (byte)0xac, 0x16, 0x5c, 0x4e, (byte)0xce, 0x3f, (byte)0xae, (byte)0xb9, (byte)0xe1, (byte)0xde, 0x06, (byte)0xb8,
            (byte)0xd7, (byte)0xd9, 0x4d, 0x43, (byte)0xab, (byte)0x8c, (byte)0xad, (byte)0x8d, (byte)0xbb, 0x6a, (byte)0xca, (byte)0xf3, 0x47, 0x21, 0x1e, (byte)0xee,
            (byte)0xf2, 0x4d, (byte)0xee, 0x5f, (byte)0xb2, 0x19, (byte)0xce, 0x44, 0x0d, 0x79, 0x63, (byte)0x8f, (byte)0xd8, 0x1d, (byte)0xbd, (byte)0xb4,
            (byte)0xb9, 0x56, (byte)0xba, (byte)0xf4
        };

        private static final String INFO_REQUEST =
            "GET /info RTSP/1.0\r\n" +
            "X-Apple-ProtocolVersion: 1\r\n" +
            "Content-Length: 0\r\n" +
            "CSeq: 2\r\n" +
            "DACP-ID: 2CC18E0712799F6D\r\n" +
            "Active-Remote: 1140620407\r\n" +
            "User-Agent: AirPlay/775.3.1\r\n\r\n";

        private final String host;
        private final int port;
        private final Context context;
        private final MirrorHomeFragment fragment;
        private boolean isRunning = true;

        public RtspConnectionThread(String host, int port, Context context, MirrorHomeFragment fragment) {
            this.host = host;
            this.port = port;
            this.context = context;
            this.fragment = fragment;
        }

        @Override
        public void run() {
            try {
                System.out.println(fragment.stringFromJNI());
                Socket socket = new Socket(host, port);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                
                // 发送 RTSP OPTIONS 请求
                out.write(RTSP_OPTIONS_REQUEST.getBytes());
                out.flush();
                
                // 读取 OPTIONS 响应
                RtspResponse response = readResponse(in);
                fragment.logOnMainThread("收到 OPTIONS 响应：" + response.header);

                // 如果需要处理 body
                if (response.body.length > 0) {
                    // 处理 body 数据
                    fragment.logOnMainThread("收到 body 数据，长度：" + response.body.length);
                }

                // 发送 GET /info 请求
                out.write(INFO_REQUEST.getBytes());
                out.flush();

                // 读取 info 响应
                response = readResponse(in);
                fragment.logOnMainThread("解析 info: " + response.header);

                // 解析 plist
                try {
                    NSDictionary rootDict = (NSDictionary)PropertyListParser.parse(response.body);
                    fragment.logOnMainThread("解析 INFO plist 内容：" + rootDict.toXMLPropertyList());
                } catch (Exception e) {
                    fragment.logOnMainThread("解析 plist 失败：" + e.getMessage());
                }

                // 发送 pair-setup 请求
                String pairSetupRequest = 
                    "POST /pair-setup RTSP/1.0\r\n" +
                    "Content-Length: 32\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "CSeq: 1\r\n" +
                    "DACP-ID: 72B5A52C22E5647C\r\n" +
                    "Active-Remote: 2558876681\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";

                byte[] pairSetupData = new byte[] {
                    (byte)0xb7, 0x75, (byte)0xc9, 0x79, 0x75, 0x5a, 0x71, (byte)0xd3,
                    0x09, (byte)0x81, (byte)0xdc, (byte)0xd4, 0x57, (byte)0xa9, 0x3c, (byte)0x92,
                    0x5f, 0x0a, 0x26, 0x03, 0x58, (byte)0x87, (byte)0xf8, 0x3b,
                    (byte)0xea, 0x38, 0x76, 0x09, 0x38, (byte)0xd3, (byte)0xf4, (byte)0xdf
                };

                out.write(pairSetupRequest.getBytes());
                out.write(pairSetupData);
                out.flush();

                // 读取 pair-setup 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到 pair-setup 响应：" + response.header);

                // 添加 pair-verify 请求
                KeyPair keyPair = new KeyPairGenerator().generateKeyPair();
                Curve25519 curve25519 = Curve25519.getInstance(Curve25519.BEST);
                Curve25519KeyPair curve25519KeyPair = curve25519.generateKeyPair();
                byte[] pairVerify1Request = new byte[68];
                pairVerify1Request[0] = 1;
                pairVerify1Request[1] = 0;
                pairVerify1Request[2] = 0;
                pairVerify1Request[3] = 0;
                System.arraycopy(curve25519KeyPair.getPublicKey(), 0, pairVerify1Request, 4, 32);
                System.arraycopy(((EdDSAPublicKey) keyPair.getPublic()).getAbyte(), 0, pairVerify1Request, 36, 32);

                String pairVerifyRequest = 
                    "POST /pair-verify RTSP/1.0\r\n" +
                    "X-Apple-PD: 1\r\n" +
                    "X-Apple-AbsoluteTime: 759932340\r\n" +
                    "Content-Length: 68\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "CSeq: 2\r\n" +
                    "DACP-ID: 72B5A52C22E5647C\r\n" +
                    "Active-Remote: 2558876681\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";

                out.write(pairVerifyRequest.getBytes());
                out.write(pairVerify1Request);
                out.flush();

                // 读取 pair-verify 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到第一个 pair-verify 响应：" + response.header);

                // 发送第二个 pair-verify 请求

                byte[] atvPublicKey = Arrays.copyOfRange(response.body, 0, 32);
                byte[] sharedSecret = curve25519.calculateAgreement(atvPublicKey, curve25519KeyPair.getPrivateKey());

                MessageDigest sha512Digest = MessageDigest.getInstance("SHA-512");
                sha512Digest.update("Pair-Verify-AES-Key".getBytes(StandardCharsets.UTF_8));
                sha512Digest.update(sharedSecret);
                byte[] sharedSecretSha512AesKey = Arrays.copyOfRange(sha512Digest.digest(), 0, 16);

                sha512Digest.update("Pair-Verify-AES-IV".getBytes(StandardCharsets.UTF_8));
                sha512Digest.update(sharedSecret);
                byte[] sharedSecretSha512AesIV = Arrays.copyOfRange(sha512Digest.digest(), 0, 16);

                Cipher aesCtr128Encrypt = Cipher.getInstance("AES/CTR/NoPadding");
                aesCtr128Encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sharedSecretSha512AesKey, "AES"), new IvParameterSpec(sharedSecretSha512AesIV));

                aesCtr128Encrypt.update(Arrays.copyOfRange(response.body, 32, 96));
                EdDSAEngine edDSAEngine = new EdDSAEngine();
                edDSAEngine.initSign(keyPair.getPrivate());

                byte[] dataToSign = new byte[64];
                System.arraycopy(curve25519KeyPair.getPublicKey(), 0, dataToSign, 0, 32);
                System.arraycopy(atvPublicKey, 0, dataToSign, 32, 32);
                byte[] signature = aesCtr128Encrypt.update(edDSAEngine.signOneShot(dataToSign));

                byte[] pairVerify2Request = new byte[68];
                pairVerify2Request[0] = 0;
                pairVerify2Request[1] = 0;
                pairVerify2Request[2] = 0;
                pairVerify2Request[3] = 0;
                System.arraycopy(signature, 0, pairVerify2Request, 4, 64);
                String pairVerifyRequest2 = 
                    "POST /pair-verify RTSP/1.0\r\n" +
                    "X-Apple-PD: 1\r\n" +
                    "X-Apple-AbsoluteTime: 759932340\r\n" +
                    "Content-Length: 68\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "CSeq: 3\r\n" +
                    "DACP-ID: 72B5A52C22E5647C\r\n" +
                    "Active-Remote: 2558876681\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";

                out.write(pairVerifyRequest2.getBytes());
                out.write(pairVerify2Request);
                out.flush();

                // 读取第二个 pair-verify 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到第二个 pair-verify 响应：" + response.header);

                // 发送 FP-SETUP 请求
                String fpSetupRequest = 
                    "POST /fp-setup RTSP/1.0\r\n" +
                    "X-Apple-ET: 32\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "CSeq: 4\r\n" +
                    "DACP-ID: 2CC18E0712799F6D\r\n" +
                    "Active-Remote: 1140620407\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";
                
                out.write(fpSetupRequest.getBytes());
                out.flush();
                out.write(FP_SETUP_REQUEST);
                out.flush();

                // 读取fp-setup响应
                response = readResponse(in);
                fragment.logOnMainThread("收到FP-SETUP响应：" + response.header);
                
                // 发送第二个 FP-SETUP 请求
                String fpSetupRequest2 = 
                    "POST /fp-setup RTSP/1.0\r\n" +
                    "X-Apple-ET: 32\r\n" +
                    "Content-Length: 164\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "CSeq: 5\r\n" +
                    "DACP-ID: 2CC18E0712799F6D\r\n" +
                    "Active-Remote: 1140620407\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";
                
                out.write(fpSetupRequest2.getBytes());
                out.flush();
                out.write(FP_SETUP_REQUEST_2);
                out.flush();

                // 读取第二个fp-setup响应
                response = readResponse(in);
                fragment.logOnMainThread("收到第二个FP-SETUP响应：" + response.header);
                
                // 构建 SETUP 请求的 plist
                NSDictionary setupDict = new NSDictionary();
                setupDict.put("et", 32);
                setupDict.put("statsCollectionEnabled", false);
                setupDict.put("eiv", decodeBase64("SR3Us18zUP+dM7tX2CapMQ=="));
                setupDict.put("sessionUUID", "09DA1A1F-AE04-4845-B404-7FBAF046D7D5");
                setupDict.put("timingProtocol", "NTP");
                setupDict.put("osName", "iPhone OS");
                setupDict.put("osBuildVersion", "21G93");
                setupDict.put("sourceVersion", "775.3.1");
                setupDict.put("timingPort", 55606);
                setupDict.put("isScreenMirroringSession", true);
                setupDict.put("osVersion", "17.6.1");
                setupDict.put("ekey", decodeBase64("RlBMWQECAQAAAAA8AAAAAJkBYx+MhWFfX7SWE1/KGIQAAAAQRk8i+JxY/UiO0KQ6YaNn9LfVDlQB04zcOPjatJZbPOMVUtTs"));
                setupDict.put("sessionCorrelationUUID", "22E39508-74C6-4BCE-8685-AB01DB111C21");
                setupDict.put("deviceID", getMacAddress(context));  // 使用真实的 MAC 地址
                setupDict.put("model", "iPad14,2");
                setupDict.put("name", "舒舒平板");
                setupDict.put("macAddress", getMacAddress(context));  // 使用真实的 MAC 地址
                
                // 将 plist 转换为二进制数据
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PropertyListParser.saveAsBinary(setupDict, baos);
                byte[] plistData = baos.toByteArray();
                
                // 构建 SETUP 请求
                String setupRequest = 
                    "SETUP rtsp://" + host + "/709908614630099013 RTSP/1.0\r\n" +
                    "Content-Length: " + plistData.length + "\r\n" +
                    "Content-Type: application/x-apple-binary-plist\r\n" +
                    "CSeq: 6\r\n" +
                    "DACP-ID: 2CC18E0712799F6D\r\n" +
                    "Active-Remote: 1140620407\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";
                
                // 发送请求头和 plist 数据
                out.write(setupRequest.getBytes());
                out.write(plistData);
                out.flush();
                
                // 读取 SETUP 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到 SETUP 响应：" + response.header);
                
                // 发送 RECORD 请求
                String recordRequest = 
                    "RECORD rtsp://" + host + "/709908614630099013 RTSP/1.0\r\n" +
                    "CSeq: 7\r\n" +
                    "DACP-ID: 2CC18E0712799F6D\r\n" +
                    "Active-Remote: 1140620407\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";
                
                out.write(recordRequest.getBytes());
                out.flush();
                
                // 读取 RECORD 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到 RECORD 响应：" + response.header);
                
                // 构建第二个 SETUP 请求的 plist
                NSDictionary setupDict2 = new NSDictionary();
                NSDictionary streamDict = new NSDictionary();
                NSArray streams = new NSArray(1);
                NSArray timestampInfo = new NSArray(5);
                
                // 构建 timestampInfo 数组
                int index = 0;
                for (String name : new String[]{"SubSu", "BePxT", "AfPxT", "BefEn", "EmEnc"}) {
                    NSDictionary timeDict = new NSDictionary();
                    timeDict.put("name", name);
                    timestampInfo.setValue(index, timeDict);
                    index++;
                }
                
                streamDict.put("timestampInfo", timestampInfo);
                streamDict.put("latencyMs", 100);
                streamDict.put("type", 110);
                streamDict.put("streamConnectionID", 7360034197512602439L);
                
                streams.setValue(0, streamDict);  // 将 streamDict 设置为数组的第一个元素
                setupDict2.put("streams", streams);
                
                // 将 plist 转换为二进制数据
                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                PropertyListParser.saveAsBinary(setupDict2, baos2);
                byte[] plistData2 = baos2.toByteArray();
                
                // 构建第二个 SETUP 请求
                String setupRequest2 = 
                    "SETUP rtsp://" + host + "/709908614630099013 RTSP/1.0\r\n" +
                    "Content-Length: " + plistData2.length + "\r\n" +
                    "Content-Type: application/x-apple-binary-plist\r\n" +
                    "CSeq: 8\r\n" +
                    "DACP-ID: 2CC18E0712799F6D\r\n" +
                    "Active-Remote: 1140620407\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n\r\n";
                
                // 发送第二个 SETUP 请求
                out.write(setupRequest2.getBytes());
                out.write(plistData2);
                out.flush();
                
                // 读取第二个 SETUP 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到第二个 SETUP 响应：" + response.header);

                // 解析二进制 plist
                NSDictionary responseDict = (NSDictionary)PropertyListParser.parse(response.body);
                NSArray responseStreams = (NSArray)responseDict.get("streams");
                NSDictionary streamInfo = (NSDictionary)responseStreams.objectAtIndex(0);
                
                // 获取数据端口
                int dataPort = streamInfo.get("dataPort").toJavaObject(Integer.class);
                fragment.logOnMainThread("获取到数据端口: " + dataPort);
                
                // 创建并启动 RTP 发送线程
                RtpSenderThread rtpSender = new RtpSenderThread(host, dataPort, com.gitee.connect_screen.State.getMediaProjection());
                rtpSender.start();

                while (isRunning) {
                    // TODO: 从编码器获取 H.264 帧数据并发送
                    Thread.sleep(500);
                }
                socket.close();
            } catch (Exception e) {
                fragment.logOnMainThread("连接失败：" + e.getMessage());
            }
        }

        // 修改为返回包含 header 和 body 的类
        private static class RtspResponse {
            public final String header;
            public final byte[] body;

            public RtspResponse(String header, byte[] body) {
                this.header = header;
                this.body = body;
            }
        }

        private RtspResponse readResponse(InputStream in) throws IOException {
            StringBuilder headerBuilder = new StringBuilder();
            byte[] buffer = new byte[1];
            
            // 读取 header 直到遇到空行（\r\n\r\n）
            int consecutiveNewlines = 0;
            while (consecutiveNewlines < 4) {
                if (in.read(buffer) == -1) break;
                headerBuilder.append((char) buffer[0]);
                if (buffer[0] == '\r' || buffer[0] == '\n') {
                    consecutiveNewlines++;
                } else {
                    consecutiveNewlines = 0;
                }
            }
            
            String header = headerBuilder.toString();
            
            // 解析 Content-Length
            int contentLength = 0;
            for (String line : header.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    break;
                }
            }
            
            // 读取 body
            byte[] body = new byte[contentLength];
            int totalBytesRead = 0;
            while (totalBytesRead < contentLength) {
                int bytesRead = in.read(body, totalBytesRead, contentLength - totalBytesRead);
                if (bytesRead == -1) break;
                totalBytesRead += bytesRead;
            }
            
            return new RtspResponse(header, body);
        }

        // 添加 Base64 解码辅助方法
        private byte[] decodeBase64(String base64String) {
            return android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
        }

        // 添加获取 MAC 地址的方法
        private String getMacAddress(Context context) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String macAddress = wifiInfo.getMacAddress();
            
            // 如果获取不到或者是默认值，尝试通过网络接口获取
            if (macAddress == null || macAddress.equals("02:00:00:00:00:00")) {
                try {
                    List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
                    for (NetworkInterface nif : all) {
                        if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                        byte[] macBytes = nif.getHardwareAddress();
                        if (macBytes == null) {
                            return "02:00:00:00:00:00";
                        }

                        StringBuilder sb = new StringBuilder();
                        for (byte b : macBytes) {
                            sb.append(String.format("%02X:", b));
                        }

                        if (sb.length() > 0) {
                            sb.deleteCharAt(sb.length() - 1);
                        }
                        return sb.toString();
                    }
                } catch (Exception ex) {
                    return "02:00:00:00:00:00";
                }
            }
            return macAddress;
        }
    }

    // 添加原生方法声明
    public native String stringFromJNI();
    public native byte[] playfairDecrypt(byte[] message3, byte[] cipherText);
    
    // 在类的静态初始化块中加载库
    static {
        System.loadLibrary("connect_screen");
    }

    private byte[] decodeBase64(String base64String) {
        return android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
    }

}