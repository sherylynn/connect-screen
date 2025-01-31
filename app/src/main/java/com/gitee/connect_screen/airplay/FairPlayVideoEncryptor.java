package com.gitee.connect_screen.airplay;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class FairPlayVideoEncryptor {

    private final byte[] aesKey;
    private final byte[] sharedSecret;
    private final String streamConnectionID;

    private final Cipher aesCtrEncrypt;
    private final byte[] og = new byte[16];

    private int nextEncryptCount;

    public FairPlayVideoEncryptor(byte[] sharedSecret) throws Exception {
        this.aesKey =  new byte[] {
//                0x46, (byte)0xa9, (byte)0xa7, 0x4a,
//                0x0d, (byte)0xbf, 0x2b, 0x58,
//                (byte)0xa1, (byte)0x92, 0x1c, 0x26,
//                (byte)0xf7, 0x66, 0x77, 0x2d
                (byte) 0xae, (byte) 0x9c, (byte) 0xdf, 0x39, 0x43, (byte) 0xfd, 0x2a,
                0x03, 0x66, 0x70, 0x3a, 0x31, (byte) 0x98, 0x20, (byte) 0x8c, 0x2f
        };
        this.sharedSecret = sharedSecret;
        this.streamConnectionID = "7360034197512602439";

        aesCtrEncrypt = Cipher.getInstance("AES/CTR/NoPadding");

        initAesCtrCipher();
    }

    public void encrypt(byte[] video) throws Exception {
        if (nextEncryptCount > 0) {
            for (int i = 0; i < nextEncryptCount; i++) {
                video[i] = (byte) (video[i] ^ og[(16 - nextEncryptCount) + i]);
            }
        }

        int encryptlen = ((video.length - nextEncryptCount) / 16) * 16;
        aesCtrEncrypt.update(video, nextEncryptCount, encryptlen, video, nextEncryptCount);
        System.arraycopy(video, nextEncryptCount, video, nextEncryptCount, encryptlen);

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
        sha512Digest.update(aesKey);
        sha512Digest.update(sharedSecret);
        byte[] eaesKey = sha512Digest.digest();

        byte[] skey = ("AirPlayStreamKey" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(skey);
        sha512Digest.update(eaesKey, 0, 16);
        byte[] hash1 = sha512Digest.digest();

        byte[] siv = ("AirPlayStreamIV" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(siv);
        sha512Digest.update(eaesKey, 0, 16);
        byte[] hash2 = sha512Digest.digest();

        byte[] decryptAesKey = new byte[16];
        byte[] decryptAesIV = new byte[16];
        System.arraycopy(hash1, 0, decryptAesKey, 0, 16);
        System.arraycopy(hash2, 0, decryptAesIV, 0, 16);

        aesCtrEncrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decryptAesKey, "AES"), new IvParameterSpec(decryptAesIV));
    }
}