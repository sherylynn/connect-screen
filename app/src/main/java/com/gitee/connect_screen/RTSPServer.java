package com.gitee.connect_screen;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedOutputStream;
import java.util.concurrent.ConcurrentHashMap;

public class RTSPServer extends Thread {
    private static final String TAG = "RTSPServer";
    private final int port;
    private volatile boolean isRunning;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clientHandlers;

    public RTSPServer() {
        this.port = NvHTTP.RTSP_PORT;
        this.clientHandlers = new ConcurrentHashMap<>();
        this.isRunning = true;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            Log.i(TAG, "RTSP Server started on port " + port);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                String clientId = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                ClientHandler clientHandler = new ClientHandler(clientSocket, clientId);
                clientHandlers.put(clientId, clientHandler);
                clientHandler.start();
                Log.i(TAG, "New client connected: " + clientId);
            }
        } catch (IOException e) {
            if (isRunning) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        isRunning = false;
        for (ClientHandler handler : clientHandlers.values()) {
            handler.stopHandler();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket: " + e.getMessage());
        }
    }

    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final String clientId;
        private volatile boolean isRunning;
        private BufferedReader reader;
        private OutputStream writer;
        private String session = null;
        private static final String CRLF = "\r\n";

        public ClientHandler(Socket socket, String clientId) {
            this.clientSocket = socket;
            this.clientId = clientId;
            this.isRunning = true;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = clientSocket.getOutputStream();

                // 只处理一个请求，然后关闭连接
                StringBuilder requestBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    requestBuilder.append(line).append(CRLF);
                }
                
                if (requestBuilder.length() > 0) {
                    handleRTSPRequest(requestBuilder.toString());
                }
                
                // 处理完请求后直接清理并关闭连接
                cleanup();
                
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Client handler error: " + e.getMessage());
                }
            } finally {
                cleanup();
            }
        }

        private void handleRTSPRequest(String request) {
            Log.d(TAG, "开始处理 RTSP 请求: " + request);
            
            String[] lines = request.split("\r\n");
            if (lines.length == 0) {
                Log.e(TAG, "无效的 RTSP 请求: 请求为空");
                cleanup();
                return;
            }
            
            // 解析请求行
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length != 3) {
                Log.e(TAG, "Invalid RTSP request line: " + lines[0]);
                cleanup();
                return;
            }
            
            String method = requestLine[0];
            String uri = requestLine[1];
            String version = requestLine[2];
            
            // 解析请求头
            int cseq = -1;
            try {
                for (String line : lines) {
                    if (line.startsWith("CSeq:")) {
                        cseq = Integer.parseInt(line.substring(6).trim());
                        Log.d(TAG, "解析到 CSeq: " + cseq);
                    } else if (line.startsWith("Session:")) {
                        session = line.substring(9).trim().split(";")[0];
                        Log.d(TAG, "解析到 Session: " + session);
                    }
                }
                
                if (cseq == -1) {
                    Log.e(TAG, "Missing CSeq in RTSP request");
                    cleanup();
                    return;
                }
                
                // 构建响应
                StringBuilder response = new StringBuilder();
                response.append("RTSP/1.0 200 OK").append(CRLF);
                response.append("CSeq: ").append(cseq).append(CRLF);
                
                Log.d(TAG, "处理 RTSP " + method + " 请求");
                
                // 根据不同方法处理请求
                switch (method) {
                    case "OPTIONS":
                        break;
                        
                    case "DESCRIBE":
                        response.append(CRLF);
                        response.append("a=x-ss-general.featureFlags:3").append(CRLF);
                        response.append("a=x-ss-general.encryptionSupported:5").append(CRLF);
                        response.append("a=x-ss-general.encryptionRequested:1").append(CRLF);
                        response.append("sprop-parameter-sets=AAAAAU").append(CRLF);
                        // ... 添加其他SDP信息 ...
                        break;
                        
                    case "SETUP":
                        session = "DEADBEEFCAFE";
                        response.append("Session: ").append(session).append(";timeout = 90").append(CRLF);
                        if (uri.contains("audio")) {
                            response.append("Transport: server_port=48000").append(CRLF);
                        } else if (uri.contains("video")) {
                            response.append("Transport: server_port=47998").append(CRLF);
                        } else if (uri.contains("control")) {
                            response.append("Transport: server_port=47999").append(CRLF);
                            response.append("X-SS-Connect-Data: 2207506894").append(CRLF);
                        }
                        response.append("X-SS-Ping-Payload: A4AACADDA6340FB4").append(CRLF);
                        break;
                        
                    case "ANNOUNCE":
                    case "PLAY":
                        if (session != null) {
                            response.append(CRLF).append("w").append(CRLF);
                        }
                        break;
                }
                
                // 发送响应
                String finalResponse = response.toString();
                Log.d(TAG, "发送 RTSP 响应:\n" + finalResponse);
                try {
                    writer.write((finalResponse + CRLF).getBytes());
                    writer.flush();
                    Log.d(TAG, "RTSP 响应已发送完成");
                } catch (IOException e) {
                    Log.e(TAG, "发送响应时出错: " + e.getMessage());
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "CSeq 解析失败: " + e.getMessage());
                cleanup();
                return;
            }
        }

        public void stopHandler() {
            isRunning = false;
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket: " + e.getMessage());
            }
        }

        private void cleanup() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error during cleanup: " + e.getMessage());
            }
            clientHandlers.remove(clientId);
            Log.i(TAG, "Client disconnected: " + clientId);
        }
    }
} 