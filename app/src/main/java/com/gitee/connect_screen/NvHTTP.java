package com.gitee.connect_screen;


import androidx.annotation.NonNull;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;

public class NvHTTP extends NanoHTTPD {
    public static byte[] CA_CERT;
    public static byte[] CA_KEY;
    private final InetAddress addr;
    public static final int HTTP_PORT = 47989;
    private final Sha256PairingHash hashAlgo;
    private byte[] cipherKey;
    private byte[] serverSecret;
    private byte[] serverChallenge;
    private byte[] clienthash;
    private X509Certificate clientCert;

    public NvHTTP(InetAddress addr) {
        super(HTTP_PORT);
        this.addr = addr;
        hashAlgo = new Sha256PairingHash();
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
                clientCert = getCaCert(HexUtils.hexToBytes(params.get("clientcert")));
                byte[] salt = HexUtils.hexToBytes(params.get("salt"));
                String pin = "1234";
                cipherKey = Arrays.copyOf(hashAlgo.hashData(saltPin(salt, pin)), 16);
                android.util.Log.d("NvHTTP", "cipherKey: " + HexUtils.bytesToHex(cipherKey));
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"200\">\n" +
                        "<paired>1</paired>\n" +
                        "<plaincert>%s</plaincert>\n" +
                        "</root>", HexUtils.bytesToHex(CA_CERT)));
            } else if (params.containsKey("clientchallenge")) {
                byte[] clientchallenge = HexUtils.hexToBytes(params.get("clientchallenge"));
                byte[] decrypted = decryptAes(clientchallenge, cipherKey);
                X509Certificate caCert = getCaCert(CA_CERT);
                byte[] caCertSignature = caCert.getSignature();
                byte[] toHash = new byte[decrypted.length + caCertSignature.length + 16];
                System.arraycopy(decrypted, 0, toHash, 0, decrypted.length);
                System.arraycopy(caCertSignature, 0, toHash, decrypted.length, caCertSignature.length);
                serverSecret = generateRandomBytes(16);
                System.arraycopy(serverSecret, 0, toHash, decrypted.length + caCertSignature.length, 16);
                byte[] hash = hashAlgo.hashData(toHash);
                byte[] plainText = new byte[hash.length + 16];
                System.arraycopy(hash, 0, plainText, 0, hash.length);
                serverChallenge = generateRandomBytes(16);
                System.arraycopy(serverChallenge, 0, plainText, hash.length, 16);
                byte[] encrypted = encryptAes(plainText, cipherKey);
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"200\">\n" +
                        "<paired>1</paired>\n" +
                        "<challengeresponse>%s</challengeresponse>\n" +
                        "</root>", HexUtils.bytesToHex(encrypted)));
            } else if (params.containsKey("serverchallengeresp")) {
                byte[] serverchallengeresp = HexUtils.hexToBytes(params.get("serverchallengeresp"));
                clienthash = decryptAes(serverchallengeresp, cipherKey);
                byte[] signature = sign256(getCaKey(), serverSecret);
                byte[] pairingsecret = new byte[serverSecret.length + signature.length];
                System.arraycopy(serverSecret, 0, pairingsecret, 0, serverSecret.length);
                System.arraycopy(signature, 0, pairingsecret, serverSecret.length, signature.length);
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<root status_code=\"200\">\n" +
                                "<paired>1</paired>\n" +
                                "<pairingsecret>%s</pairingsecret>\n" +
                                "</root>", HexUtils.bytesToHex(pairingsecret)));
            } else if (params.containsKey("clientpairingsecret")) {
                byte[] clientpairingsecret = HexUtils.hexToBytes(params.get("clientpairingsecret"));
                if (clientpairingsecret.length <= 16) {
                    return failPair("Client pairing secret too short");
                }
                byte[] clientSecret = Arrays.copyOfRange(clientpairingsecret, 0, 16);
                byte[] clientSignature = Arrays.copyOfRange(clientpairingsecret, 16, clientpairingsecret.length);
                byte[] clientCertSignature = clientCert.getSignature();
                byte[] toHash = new byte[serverChallenge.length + clientCertSignature.length + clientSecret.length];
                System.arraycopy(serverChallenge, 0, toHash, 0, serverChallenge.length);
                System.arraycopy(clientCertSignature, 0, toHash, serverChallenge.length, clientCertSignature.length);
                System.arraycopy(clientSecret, 0, toHash, serverChallenge.length + clientCertSignature.length, clientSecret.length);
                byte[] hash = hashAlgo.hashData(toHash);
                if (!Arrays.equals(hash, clienthash)) {
                    return failPair("Client hash mismatch");
                }
                if (!verifySignature(clientSecret, clientSignature, clientCert)) {
                    return failPair("Client signature mismatch");
                }
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"200\">\n" +
                        "<paired>1</paired>\n" +
                        "</root>");
            } else if ("pairchallenge".equals(phrase)) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<root status_code=\"200\">\n" +
                                "<paired>1</paired>\n" +
                                "</root>");
            }
            // 如果不是 getservercert 请求，返回错误响应
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Invalid pairing phrase");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private @NonNull Response failPair(String errorMsg) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<root status_code=\"400\" status_message=\"%s\">\n" +
                        "<paired>0</paired>\n" +
                        "</root>", errorMsg));
    }

    private byte[] sign256(PrivateKey key, byte[] data) {
        Signature signature = getSha256SignatureInstanceForKey(key);
        try {
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate getCaCert(byte[] caCert) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(caCert));
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
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

    private static byte[] decryptAes(byte[] encryptedData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(false, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, encryptedData);
    }

    private static byte[] encryptAes(byte[] plaintextData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(true, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, plaintextData);
    }

    private static byte[] performBlockCipher(BlockCipher blockCipher, byte[] input) {
        int blockSize = blockCipher.getBlockSize();
        int blockRoundedSize = (input.length + (blockSize - 1)) & ~(blockSize - 1);

        byte[] blockRoundedInputData = Arrays.copyOf(input, blockRoundedSize);
        byte[] blockRoundedOutputData = new byte[blockRoundedSize];

        for (int offset = 0; offset < blockRoundedSize; offset += blockSize) {
            blockCipher.processBlock(blockRoundedInputData, offset, blockRoundedOutputData, offset);
        }

        return blockRoundedOutputData;
    }

    private byte[] generateRandomBytes(int length)
    {
        byte[] rand = new byte[length];
        new SecureRandom().nextBytes(rand);
        return rand;
    }

    private static Signature getSha256SignatureInstanceForKey(Key key) {
        try {
            switch (key.getAlgorithm()) {
                case "RSA":
                    return Signature.getInstance("SHA256withRSA");
                case "EC":
                    return Signature.getInstance("SHA256withECDSA");
                default:
                    throw new NoSuchAlgorithmException("Unhandled key algorithm: " + key.getAlgorithm());
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getCaKey() {
        try {
            PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(CA_KEY)));
            Object pemObject = pemParser.readObject();
            
            // 因为是 PKCS#8 格式的未加密私钥，pemObject 应该是 PrivateKeyInfo 类型
            PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) pemObject;
            return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load private key", e);
        }
    }

    private static boolean verifySignature(byte[] data, byte[] signature, Certificate cert) {
        try {
            Signature sig = getSha256SignatureInstanceForKey(cert.getPublicKey());
            sig.initVerify(cert.getPublicKey());
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}