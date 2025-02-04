package com.gitee.connect_screen;


import fi.iki.elonen.NanoHTTPD;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.Map;

public class NvHTTP extends NanoHTTPD {
    public static byte[] CA_CERT;
    public static byte[] CA_KEY;
    private final InetAddress addr;
    public static final int HTTP_PORT = 47989;
    private byte[] cipherKey;

    public NvHTTP(InetAddress addr) {
        super(HTTP_PORT);
        this.addr = addr;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getUri().equals("/serverinfo")) {
            String response = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<root status_code=\"200\">\n" +
                "<hostname>android-device</hostname>\n" +
                "<appversion>7.1.431.-1</appversion>\n" +
                "<GfeVersion>3.23.0.74</GfeVersion>\n" +
                "<uniqueid>DBD31B2E-EA1C-872C-5D8F-87DF1AD4C341</uniqueid>\n" +
                "<HttpsPort>47984</HttpsPort>\n" +
                "<ExternalPort>%d</ExternalPort>\n" +
                "<MaxLumaPixelsHEVC>1869449984</MaxLumaPixelsHEVC>\n" +
                "<mac>00:00:00:00:00:00</mac>\n" +
                "<LocalIP>%s</LocalIP>\n" +
                "<ServerCodecModeSupport>197377</ServerCodecModeSupport>\n" +
                "<PairStatus>0</PairStatus>\n" +
                "<currentgame>0</currentgame>\n" +
                "<state>SUNSHINE_SERVER_FREE</state>\n" +
                "</root>",
                HTTP_PORT,
                addr.getHostAddress()
            );
            
            return newFixedLengthResponse(Response.Status.OK, "application/xml", response);
        }
        
        if (session.getUri().equals("/pair")) {
            // 获取请求参数
            Map<String, String> params = session.getParms();
            String phrase = params.get("phrase");
            
            // 检查是否是 getservercert 请求
            if ("getservercert".equals(phrase)) {
                byte[] salt = HexUtils.hexToBytes(params.get("salt"));
                Sha256PairingHash hashAlgo = new Sha256PairingHash();
                String pin = "1234";
                cipherKey = Arrays.copyOf(hashAlgo.hashData(saltPin(salt, pin)), 16);
                android.util.Log.d("NvHTTP", "cipherKey: " + HexUtils.bytesToHex(cipherKey));
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"200\">\n" +
                        "<paired>1</paired>\n" +
                        "<plaincert>%s</plaincert>\n" +
                        "</root>", HexUtils.bytesToHexReverse(CA_CERT)));
            } else if (params.containsKey("clientchallenge")) {
                byte[] clientchallenge = HexUtils.hexToBytes(params.get("clientchallenge"));
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"200\">\n" +
                        "<paired>1</paired>\n" +
                        "<challengeresponse>%s</challengeresponse>\n" +
                        "</root>", ""));
            }
            // 如果不是 getservercert 请求，返回错误响应
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid pairing phrase");
        }
        
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private static byte[] saltPin(byte[] salt, String pin) {
        try {
            byte[] saltedPin = new byte[salt.length + pin.length()];
            System.arraycopy(salt, 0, saltedPin, 0, salt.length);
            System.arraycopy(pin.getBytes("UTF-8"), 0, saltedPin, salt.length, pin.length());
            return saltedPin;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private interface PairingHashAlgorithm {
        int getHashLength();
        byte[] hashData(byte[] data);
    }

    private static class Sha1PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 20;
        }

        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private static class Sha256PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 32;
        }

        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}