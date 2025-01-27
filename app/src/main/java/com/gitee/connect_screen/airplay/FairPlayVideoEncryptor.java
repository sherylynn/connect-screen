package com.gitee.connect_screen.airplay;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class FairPlayVideoEncryptor {
    private final byte[] aesKey;
    private final String streamConnectionID;

    private final Cipher aesCtrEncrypt;
    private final byte[] og = new byte[16];

    private int nextEncryptCount;

    private byte[] encryptAesKey;
    private byte[] encryptAesIV;

    public FairPlayVideoEncryptor() throws Exception {
        String streamConnectionID = "7360034197512602439";
        this.aesKey = new byte[] {
            0x46, (byte)0xa9, (byte)0xa7, 0x4a, 
            0x0d, (byte)0xbf, 0x2b, 0x58, 
            (byte)0xa1, (byte)0x92, 0x1c, 0x26, 
            (byte)0xf7, 0x66, 0x77, 0x2d
        };
        this.streamConnectionID = streamConnectionID;

        aesCtrEncrypt = Cipher.getInstance("AES/CTR/NoPadding");

        initAesCtrCipher();
    }

    public void encrypt(byte[] video) throws Exception {
        if (nextEncryptCount > 0) {
            for (int i = 0; i < nextEncryptCount; i++) {
                video[i] ^= og[(16 - nextEncryptCount) + i];
            }
        }

        int encryptlen = ((video.length - nextEncryptCount) / 16) * 16;
        aesCtrEncrypt.update(video, nextEncryptCount, encryptlen, video, nextEncryptCount);

        int restlen = (video.length - nextEncryptCount) % 16;
        int reststart = video.length - restlen;
        nextEncryptCount = 0;
        if (restlen > 0) {
            Arrays.fill(og, (byte) 0);
            System.arraycopy(video, reststart, og, 0, restlen);
            aesCtrEncrypt.update(og, 0, 16, og, 0);
            System.arraycopy(og, 0, video, reststart, restlen);
            nextEncryptCount = 16 - restlen;
        }
    }

    private void initAesCtrCipher() throws Exception {
        MessageDigest sha512Digest = MessageDigest.getInstance("SHA-512");
        
        byte[] skey = ("AirPlayStreamKey" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(skey);
        sha512Digest.update(aesKey);
        byte[] hash1 = sha512Digest.digest();

        byte[] siv = ("AirPlayStreamIV" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(siv);
        sha512Digest.update(aesKey);
        byte[] hash2 = sha512Digest.digest();

        encryptAesKey = new byte[16];
        encryptAesIV = new byte[16];
        System.arraycopy(hash1, 0, encryptAesKey, 0, 16);
        System.arraycopy(hash2, 0, encryptAesIV, 0, 16);

        StringBuilder keyLog = new StringBuilder("Video AES Key: \n");
        StringBuilder ivLog = new StringBuilder("Video AES IV: \n");
        
        for (byte b : encryptAesKey) {
            keyLog.append(String.format("%02x\n", b & 0xFF));
        }
        
        for (byte b : encryptAesIV) {
            ivLog.append(String.format("%02x\n", b & 0xFF));
        }
        
        System.out.println(keyLog.toString());
        System.out.println(ivLog.toString());

        aesCtrEncrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptAesKey, "AES"), new IvParameterSpec(encryptAesIV));
    }
} 