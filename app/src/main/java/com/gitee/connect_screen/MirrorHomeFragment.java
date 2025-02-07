package com.gitee.connect_screen;

import android.content.Context;
import android.media.projection.MediaProjection;
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
import com.gitee.connect_screen.airplay.FairPlayVideoEncryptor;
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

import java.nio.ByteBuffer;
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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;

public class MirrorHomeFragment extends Fragment {
    private static final String TAG = "MirrorHomeFragment";
    private static final int NTP_PORT = 55606;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private static final String SERVICE_TYPE = "_airplay._tcp.";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int PERMISSION_REQUEST_CODE_AUDIO = 124;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final String RTSP_OPTIONS_REQUEST =
        "OPTIONS * RTSP/1.0\r\n" +
        "CSeq: 1\r\n" +
        "User-Agent: AirPlay/1.0\r\n\r\n";
    
    private NtpServer ntpServer;
    private AudioRecord audioRecord;
    private MediaCodec audioEncoder;
    private boolean isRecording = false;
    
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
            if (checkAndRequestAudioPermissions()) {
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
            }
        });

        // 先启动 NTP 服务器
        ntpServer = new NtpServer(NTP_PORT);
        ntpServer.start();

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

    private boolean checkAndRequestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE_AUDIO);
            return false;
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
                if (serviceInfo.getServiceType().contains("_airplay") && serviceInfo.getServiceName().contains("多屏互动")) {
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

    private void initAudioRecording(int serverPort, String host) {
        try {
            // 检查系统版本
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                Toast.makeText(requireContext(), "系统版本过低，需要 Android 10 及以上版本", Toast.LENGTH_SHORT).show();
                return;
            }

            MediaProjection mediaProjection = State.getMediaProjection();
            if (mediaProjection == null) {
                Toast.makeText(requireContext(), "未获取到媒体投影权限", Toast.LENGTH_SHORT).show();
                return;
            }

            // 配置音频捕获
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();

            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Toast.makeText(requireContext(), "不支持的音频配置", Toast.LENGTH_SHORT).show();
                return;
            }

            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(minBufferSize)
                    .setAudioPlaybackCaptureConfig(config)  // 设置音频捕获配置
                    .build();

            // 配置 AAC 编码器
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            
            // 开始录音和编码
            audioRecord.startRecording();
            audioEncoder.start();
            isRecording = true;

            // 创建 UDP socket 用于发送音频数据
            DatagramSocket audioSocket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(host);

            // 修改音频处理线程，添加 UDP 发送功能
            new Thread(() -> processAudio(audioSocket, serverAddress, serverPort)).start();

        } catch (Exception e) {
            Log.e(TAG, "初始化音频录制失败: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(requireContext(), "初始化音频录制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processAudio(DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        byte[] buffer = new byte[4096];
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        long frameCount = 0;
        
        Log.i(TAG, "开始音频处理循环");
        
        while (isRecording) {
            try {
                // 读取音频数据
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    Log.d(TAG, String.format("读取到音频数据: %d 字节", bytesRead));
                    
                    // 获取编码器输入缓冲区
                    int inputBufferIndex = audioEncoder.dequeueInputBuffer(-1);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
                        inputBuffer.clear();
                        inputBuffer.put(buffer, 0, bytesRead);
                        long presentationTimeUs = System.nanoTime() / 1000;
                        audioEncoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, 
                                presentationTimeUs, 0);
                        Log.d(TAG, String.format("已将音频数据写入编码器输入缓冲区 #%d, PTS: %d µs", 
                                inputBufferIndex, presentationTimeUs));
                    }

                    // 获取编码后的数据
                    int outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex);
                        frameCount++;
                        
                        if (outputBuffer != null) {
                            byte[] encodedData = new byte[bufferInfo.size];
                            outputBuffer.get(encodedData);
                            
                            // 构建 RTP 包头 (12 字节)
                            byte[] rtpHeader = new byte[12];
                            rtpHeader[0] = (byte) 0x80; // RTP version 2
                            rtpHeader[1] = (byte) 0x60; // Payload type 96 for AAC
                            
                            // 序列号 (2 bytes)
                            short seqNum = (short) frameCount;
                            rtpHeader[2] = (byte) (seqNum >> 8);
                            rtpHeader[3] = (byte) seqNum;
                            
                            // 时间戳 (4 bytes)
                            long timestamp = bufferInfo.presentationTimeUs * 44100 / 1000000;
                            rtpHeader[4] = (byte) (timestamp >> 24);
                            rtpHeader[5] = (byte) (timestamp >> 16);
                            rtpHeader[6] = (byte) (timestamp >> 8);
                            rtpHeader[7] = (byte) timestamp;
                            
                            // SSRC (4 bytes) - 使用固定值
                            rtpHeader[8] = 0x00;
                            rtpHeader[9] = 0x00;
                            rtpHeader[10] = 0x00;
                            rtpHeader[11] = 0x01;
                            
                            // 组合 RTP 头和音频数据
                            byte[] rtpPacket = new byte[rtpHeader.length + encodedData.length];
                            System.arraycopy(rtpHeader, 0, rtpPacket, 0, rtpHeader.length);
                            System.arraycopy(encodedData, 0, rtpPacket, rtpHeader.length, encodedData.length);
                            
                            // 发送 UDP 包
                            DatagramPacket packet = new DatagramPacket(
                                rtpPacket, rtpPacket.length, 
                                serverAddress, serverPort
                            );
                            socket.send(packet);
                            
                            Log.d(TAG, String.format("已发送 RTP 包：大小=%d 字节, 序列号=%d, 时间戳=%d",
                                rtpPacket.length, seqNum, timestamp));
                        }
                        
                        audioEncoder.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                    }
                } else {
                    Log.w(TAG, String.format("音频读取返回: %d", bytesRead));
                }
            } catch (Exception e) {
                Log.e(TAG, "处理音频数据时出错: " + e.getMessage(), e);
                break;
            }
        }
        
        socket.close();
        Log.i(TAG, String.format("音频处理循环结束，共处理 %d 帧", frameCount));
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
        // 停止音频录制
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
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

//        public static final byte[] FP_SETUP_REQUEST_2 = new byte[] {
//            0x46, 0x50, 0x4c, 0x59, 0x03, 0x01, 0x03, 0x00,
//            0x00, 0x00, 0x00, (byte)0x98, 0x01, (byte)0x8f, 0x1a, (byte)0x9c,
//            (byte)0xaf, 0x6c, 0x47, 0x49, (byte)0xf8, (byte)0xb2, 0x09, (byte)0xba, (byte)0xdf, (byte)0xe3, 0x67, (byte)0xf9, 0x7d, (byte)0x85, (byte)0xf7, 0x0d,
//            (byte)0xd6, (byte)0x80, 0x67, (byte)0xdd, 0x33, (byte)0xca, 0x4a, 0x57, (byte)0xe7, 0x3c, (byte)0xaf, (byte)0xa4, (byte)0xaf, 0x28, 0x72, (byte)0xb1,
//            0x04, (byte)0xf0, (byte)0xab, 0x6b, (byte)0xb0, 0x2b, 0x5b, 0x5d, 0x40, 0x5f, (byte)0xfd, (byte)0xa7, 0x05, (byte)0xae, 0x0e, 0x78,
//            (byte)0xbe, (byte)0xf7, (byte)0xeb, (byte)0x9f, 0x43, (byte)0xcd, (byte)0x90, (byte)0xe1, 0x13, 0x79, (byte)0xdb, (byte)0xfc, (byte)0xca, (byte)0xc4, 0x54, (byte)0xac,
//            (byte)0x86, 0x42, (byte)0xc9, (byte)0xdb, 0x4e, 0x2e, (byte)0xaf, 0x47, (byte)0xef, (byte)0xdf, (byte)0xcd, 0x09, (byte)0xfa, 0x3f, (byte)0xb8, 0x78,
//            (byte)0xa0, (byte)0xb8, 0x1d, (byte)0xd3, (byte)0x85, 0x09, (byte)0xf6, 0x6c, 0x46, 0x49, (byte)0x9b, (byte)0xcf, (byte)0xc6, (byte)0xd9, (byte)0xf0, (byte)0xa3,
//            0x7c, (byte)0xfd, (byte)0xf7, (byte)0xc9, (byte)0xac, 0x16, 0x5c, 0x4e, (byte)0xce, 0x3f, (byte)0xae, (byte)0xb9, (byte)0xe1, (byte)0xde, 0x06, (byte)0xb8,
//            (byte)0xd7, (byte)0xd9, 0x4d, 0x43, (byte)0xab, (byte)0x8c, (byte)0xad, (byte)0x8d, (byte)0xbb, 0x6a, (byte)0xca, (byte)0xf3, 0x47, 0x21, 0x1e, (byte)0xee,
//            (byte)0xf2, 0x4d, (byte)0xee, 0x5f, (byte)0xb2, 0x19, (byte)0xce, 0x44, 0x0d, 0x79, 0x63, (byte)0x8f, (byte)0xd8, 0x1d, (byte)0xbd, (byte)0xb4,
//            (byte)0xb9, 0x56, (byte)0xba, (byte)0xf4};
        public static final byte[] FP_SETUP_REQUEST_2 = new byte[]
                {
                        0x46, 0x50, 0x4c, 0x59, 0x03, 0x01, 0x03, 0x00,
                        0x00, 0x00, 0x00, (byte)0x98, 0x01, (byte)0x8f, 0x1a, (byte)0x9c,
                        (byte)0xb1, 0x03, 0x68, (byte)0xbc, (byte)0xa4, (byte)0xa1, (byte)0xd2, (byte)0xe2,
                        0x2d, 0x19, (byte)0xcd, 0x05, (byte)0xa5, (byte)0xd8, 0x6a, (byte)0xf5,
                        0x56, (byte)0xd2, (byte)0xf4, (byte)0xe5, 0x30, (byte)0xc4, (byte)0xa1, (byte)0x9f,
                        (byte)0xd8, 0x78, (byte)0xfe, 0x23, (byte)0x8e, 0x68, (byte)0x94, (byte)0xca,
                        0x37, (byte)0xbe, (byte)0xb5, (byte)0xcb, (byte)0x8d, (byte)0xd2,(byte) 0xef, 0x7b,
                        0x00, (byte)0xd8, 0x1b, 0x0e, 0x17, 0x6e, 0x06, 0x41,
                        (byte)0x90, (byte) 0xcf, 0x30, 0x38, 0x4d, (byte) 0xe7, 0x40, 0x7e,
                        0x26, (byte) 0xa7, 0x0f, (byte) 0xf8, 0x5c, 0x7e, (byte) 0xf1, 0x21,
                        (byte) 0x83, (byte)0xa7, (byte)0x95, (byte)0x8f, (byte)0x88, (byte)0xa9, (byte)0xa6, 0x6e,
                        (byte)0x8a, (byte)0xb0, 0x4e, (byte)0xf0, (byte)0xc2, 0x18, 0x4d, (byte) 0x9d,
                        (byte)0xca, (byte)0x85, 0x3a, (byte)0xc8, (byte)0x8b, 0x5c, 0x3c, 0x5e,
                        0x71, (byte)0xff, (byte)0xde, 0x6c, 0x41, (byte)0xe3, (byte)0xb8, (byte) 0xd7,
                        0x16, (byte)0xce, (byte)0xb9, (byte)0xf4, 0x1c, (byte)0xa9, 0x64, (byte)0xb3,
                        0x0b, 0x7d, 0x67, (byte)0xeb, 0x46, (byte)0x91, 0x13, (byte)0xb7,
                        (byte)0xc5, (byte)0xb2, 0x39, (byte)0xe8, (byte)0xab, (byte)0x89, 0x40, (byte)0xbe,
                        (byte)0xc0, 0x6a, 0x69, 0x38, (byte)0xea, (byte)0x82, 0x35, 0x55,
                        0x21, 0x18, (byte)0xde, 0x34, 0x4f, 0x76, (byte)0x89, (byte)0xa0,
                        0x36, 0x58, 0x6d, 0x5e, 0x71, 0x21, 0x47, (byte)0xe0,
                        (byte)0x9b, (byte)0xd1, (byte)0xa2, 0x0b,
                };

        private static final String INFO_REQUEST =
            "GET /info RTSP/1.0\r\n" +
            "X-Apple-ProtocolVersion: 1\r\n" +
            "Content-Length: 0\r\n" +
            "CSeq: 2\r\n" +
            "DACP-ID: 68D3A0F57F146B1B\r\n" +
            "Active-Remote: 2442483712\r\n" +
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

                // 构建 SDP 内容
                String sdpContent = 
                    "v=0\r\n" +
                    "o=AirTunes 709908614630099013 0 IN IP4 10.140.1.183\r\n" +
                    "s=AirTunes\r\n" +
                    "i=iPhone\r\n" +
                    "c=IN IP4 10.140.1.183\r\n" +
                    "t=0 0\r\n" +
                    "m=audio 0 RTP/AVP 96\r\n" +
                    "a=rtpmap:96 mpeg4-generic/44100/2\r\n" +
                    "a=fmtp:96 mode=AAC-eld; constantDuration=480\r\n" +
                    "a=min-latency:4410\r\n" +
                    "a=max-latency:4410\r\n" +
                    "m=video 0 RTP/AVP 97\r\n" +
                    "a=rtpmap:97 H264\r\n" +
                    "a=fmtp:97\r\n";

                // 计算 SDP 内容的字节长度
                int contentLength = sdpContent.getBytes().length;

                // 构建 ANNOUNCE 请求
                String announceRequest = 
                    "ANNOUNCE rtsp://" + "10.140.1.183" + "/709908614630099013 RTSP/1.0\r\n" +
                    "X-Apple-Device-ID: 0xf0d1a983e4cc\r\n" +
                    "X-Apple-Client-Name: iPhone\r\n" +
                    "CSeq: 9\r\n" +
                    "DACP-ID: 68D3A0F57F146B1B\r\n" +
                    "Active-Remote: 2442483712\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "User-Agent: AirPlay/775.3.1\r\n" +
                    "Content-Length: " + contentLength + "\r\n\r\n" +
                    sdpContent;

                // 发送 ANNOUNCE 请求
                out.write(announceRequest.getBytes());
                out.flush();

                // 读取 ANNOUNCE 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到 ANNOUNCE 响应：" + response.header);

                // 构建 SETUP 请求
                String setupRequest = 
                    "SETUP rtsp://192.168.2.109/10685155445721732316/audio RTSP/1.0\r\n" +
                    "Transport: RTP/AVP/UDP;unicast;mode=screen;timing_port=61544;events;control_port=58147;redundant=2\r\n" +
                    "CSeq: 81\r\n" +
                    "DACP-ID: 2751F74C147C63A1\r\n" +
                    "Active-Remote: 813031159\r\n" +
                    "User-Agent: AirPlay/160.10\r\n\r\n";

                // 发送 SETUP 请求
                out.write(setupRequest.getBytes());
                out.flush();

                // 读取 SETUP 响应
                response = readResponse(in);
                fragment.logOnMainThread("收到 SETUP 响应：" + response.header);
                
                // 解析 Transport header 中的 server_port
                String transportHeader = response.header.lines()
                    .filter(line -> line.startsWith("Transport:"))
                    .findFirst()
                    .orElse("");
                
                int serverPort = -1;
                if (!transportHeader.isEmpty()) {
                    String serverPortStr = Arrays.stream(transportHeader.split(";"))
                        .filter(param -> param.trim().startsWith("server_port="))
                        .map(param -> param.split("=")[1])
                        .findFirst()
                        .orElse("-1");
                    serverPort = Integer.parseInt(serverPortStr);
                    fragment.logOnMainThread("解析到 server_port: " + serverPort);
                }
                
                if (serverPort != -1) {
                    fragment.initAudioRecording(serverPort, host);
                }
                
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