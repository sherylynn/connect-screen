package com.gitee.connect_screen;
import androidx.annotation.NonNull;

import fi.iki.elonen.NanoHTTPD;
import javax.net.ssl.*;
import javax.security.cert.CertificateException;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Map;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class NvHTTPS extends NanoHTTPD {
    private final NativeServer nativeServer;

    public NvHTTPS(NativeServer nativeServer)  {
        super(NvHTTP.HTTPS_PORT);
        this.nativeServer = nativeServer;
        SSLContext sslContext = createSSLContext();

        // 配置 SSL 参数
        SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.2", "TLSv1.3"});
        sslParameters.setNeedClientAuth(true);

        // 启用 HTTPS
        makeSecure(sslContext.getServerSocketFactory(), null);
    }

    private @NonNull SSLContext createSSLContext() {
        // 获取 CA 证书和私钥
        X509Certificate caCert = getCaCert(NvHTTP.CA_CERT);
        PrivateKey caKey = getCaKey();

        // 创建密钥管理器
        KeyManager[] keyManagers = new KeyManager[] {
            new X509KeyManager() {
                @Override
                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    return null;
                }

                @Override
                public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                    return null;
                }

                @Override
                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return new String[] {"serverkey"};
                }

                @Override
                public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                    return "serverkey";
                }

                @Override
                public X509Certificate[] getCertificateChain(String alias) {
                    return new X509Certificate[] {caCert};
                }

                @Override
                public PrivateKey getPrivateKey(String alias) {
                    return caKey;
                }
            }
        };

        // 创建自定义的信任管理器
        TrustManager[] trustManagers = new TrustManager[] {
            new CustomTrustManager()
        };

        try {
            // 创建 SSL 上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getUri().equals("/serverinfo")) {
            return NvHTTP.handleServerInfo(true);
        } else if (session.getUri().equals("/pair")) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<root status_code=\"200\">\n" +
                    "<paired>1</paired>\n" +
                    "</root>");
        } else if (session.getUri().equals("/applist")) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<root status_code=\"200\">" +
                            "<App><IsHdrSupported>1</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>881448767</ID></App>" +
                            "</root>");
        } else if (session.getUri().equals("/launch")) {
            Map<String, String> params = session.getParms();
            String rikey = params.get("rikey");
            String rikeyid = params.get("rikeyid");
            byte[] gcmKey = HexUtils.hexToBytes(rikey);
            byte[] iv = new byte[16];
            // 将rikeyid转换为big-endian的32位整数，并复制到iv数组的开头
            int rikeyidInt = Integer.parseInt(rikeyid);
            iv[0] = (byte) (rikeyidInt >> 24);
            iv[1] = (byte) (rikeyidInt >> 16);
            iv[2] = (byte) (rikeyidInt >> 8);
            iv[3] = (byte) rikeyidInt;
            nativeServer.gcmKey = gcmKey;
            nativeServer.iv = iv;
            nativeServer.peerIp = session.getRemoteIpAddress();
            String protocol = "rtsp";
//            if (Integer.parseInt(corever) >= 1) {
//                protocol = "rtspenc";
//            }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    String.format("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<root status_code=\"200\">" +
                            "<sessionUrl0>%s://%s:%d</sessionUrl0>" +
                            "<gamesession>1</gamesession>" +
                            "</root>", protocol, NvHTTP.ADDRESS.getHostAddress(), NvHTTP.RTSP_PORT));
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }

    // 自定义信任管理器
    private static class CustomTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // 不需要验证服务器证书
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private X509Certificate getCaCert(byte[] caCert) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(caCert));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getCaKey() {
        try {
            PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(NvHTTP.CA_KEY)));
            Object pemObject = pemParser.readObject();
            
            // 因为是 PKCS#8 格式的未加密私钥，pemObject 应该是 PrivateKeyInfo 类型
            PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) pemObject;
            return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load private key", e);
        }
    }
}