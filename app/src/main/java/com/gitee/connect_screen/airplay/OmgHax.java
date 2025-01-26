public class OmgHax {
    // 需要从原代码中导入的表格数据
    private static byte[] index_mangle = {0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte)0x80, 0x1B, 0x36, 0x6C};
    private static byte[] t_key; // 需要从原代码导入
    private static byte[][] table_s1; // 需要从原代码导入
    private static byte[] table_s5; // 需要从原代码导入
    private static byte[] table_s6; // 需要从原代码导入
    private static byte[] table_s7; // 需要从原代码导入
    private static byte[] table_s8; // 需要从原代码导入
    
    private static byte[] tableIndex(int i) {
        return table_s1[((31 * i) % 0x28) << 8];
    }
    
    private static void t_xor(byte[] in, byte[] out) {
        for (int i = 0; i < 16; i++) {
            out[i] = (byte)(in[i] ^ t_key[i]);
        }
    }
    
    public static void generateKeySchedule(byte[] keyMaterial, int[][] keySchedule) {
        int[] keyData = new int[4];
        byte[] buffer = new byte[16];
        
        // 初始化 key_schedule
        for (int i = 0; i < 11; i++) {
            keySchedule[i] = new int[4];
            for (int j = 0; j < 4; j++) {
                keySchedule[i][j] = 0xdeadbeef;
            }
        }
        
        // G
        t_xor(keyMaterial, buffer);
        
        // 将 buffer 复制到 keyData
        for (int i = 0; i < 4; i++) {
            keyData[i] = ((buffer[i*4] & 0xFF) << 24) |
                        ((buffer[i*4+1] & 0xFF) << 16) |
                        ((buffer[i*4+2] & 0xFF) << 8) |
                        (buffer[i*4+3] & 0xFF);
        }
        
        int ti = 0;
        for (int round = 0; round < 11; round++) {
            // H
            keySchedule[round][0] = keyData[0];
            
            // I
            byte[] table1 = tableIndex(ti);
            byte[] table2 = tableIndex(ti + 1);
            byte[] table3 = tableIndex(ti + 2);
            byte[] table4 = tableIndex(ti + 3);
            ti += 4;
            
            buffer[0] ^= (byte)(table1[buffer[0x0d] & 0xFF] ^ index_mangle[round]);
            buffer[1] ^= table2[buffer[0x0e] & 0xFF];
            buffer[2] ^= table3[buffer[0x0f] & 0xFF];
            buffer[3] ^= table4[buffer[0x0c] & 0xFF];
            
            // 更新 keyData[0]
            keyData[0] = ((buffer[0] & 0xFF) << 24) |
                        ((buffer[1] & 0xFF) << 16) |
                        ((buffer[2] & 0xFF) << 8) |
                        (buffer[3] & 0xFF);
            
            // H
            keySchedule[round][1] = keyData[1];
            
            // J
            keyData[1] ^= keyData[0];
            
            // H
            keySchedule[round][2] = keyData[2];
            
            // J
            keyData[2] ^= keyData[1];
            
            // K and L
            keySchedule[round][3] = keyData[3];
            
            // J
            keyData[3] ^= keyData[2];
        }
    }

    public static void generateSessionKey(byte[] oldSap, byte[] messageIn, byte[] sessionKey) {
        byte[] decryptedMessage = new byte[128];
        byte[] newSap = new byte[320];
        
        // 解密消息
        decryptMessage(messageIn, decryptedMessage);
        
        // 组合新的 SAP
        System.arraycopy(STATIC_SOURCE_1, 0, newSap, 0x000, 0x11);
        System.arraycopy(decryptedMessage, 0, newSap, 0x011, 0x80);
        System.arraycopy(oldSap, 0x80, newSap, 0x091, 0x80);
        System.arraycopy(STATIC_SOURCE_2, 0, newSap, 0x111, 0x2f);
        
        // 复制初始会话密钥
        System.arraycopy(INITIAL_SESSION_KEY, 0, sessionKey, 0, 16);

        // 进行5轮处理
        for (int round = 0; round < 5; round++) {
            byte[] base = Arrays.copyOfRange(newSap, round * 64, round * 64 + 64);
            byte[] md5 = new byte[16];
            
            // 计算 MD5 和 SAP hash
            modifiedMd5(base, sessionKey, md5);
            sapHash(base, sessionKey);
            
            // 合并结果
            for (int i = 0; i < 4; i++) {
                int sessionKeyWord = bytesToInt(sessionKey, i * 4);
                int md5Word = bytesToInt(md5, i * 4);
                int result = (sessionKeyWord + md5Word) & 0xffffffff;
                intToBytes(result, sessionKey, i * 4);
            }
        }

        // 交换字节顺序
        for (int i = 0; i < 16; i += 4) {
            swapBytes(sessionKey, i, i + 3);
            swapBytes(sessionKey, i + 1, i + 2);
        }
        
        // 最后与 121 进行异或
        for (int i = 0; i < 16; i++) {
            sessionKey[i] ^= 121;
        }
    }

    private static void swapBytes(byte[] array, int i, int j) {
        byte temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }

    private static void intToBytes(int value, byte[] bytes, int offset) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    // 需要添加的静态常量
    private static final byte[] STATIC_SOURCE_1 = {
        (byte)0xFA, (byte)0x9C, (byte)0xAD, (byte)0x4D, (byte)0x4B, (byte)0x68, (byte)0x26, (byte)0x8C,
        (byte)0x7F, (byte)0xF3, (byte)0x88, (byte)0x99, (byte)0xDE, (byte)0x92, (byte)0x2E, (byte)0x95,
        (byte)0x1E
    };

    private static final byte[] STATIC_SOURCE_2 = {
        (byte)0xEC, (byte)0x4E, (byte)0x27, (byte)0x5E, (byte)0xFD, (byte)0xF2, (byte)0xE8, (byte)0x30,
        (byte)0x97, (byte)0xAE, (byte)0x70, (byte)0xFB, (byte)0xE0, (byte)0x00, (byte)0x3F, (byte)0x1C,
        (byte)0x39, (byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x10,
        (byte)0x09, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
    };

    private static final byte[] INITIAL_SESSION_KEY = {
        (byte)0xDC, (byte)0xDC, (byte)0xF3, (byte)0xB9, (byte)0x0B, (byte)0x74, (byte)0xDC, (byte)0xFB,
        (byte)0x86, (byte)0x7F, (byte)0xF7, (byte)0x60, (byte)0x16, (byte)0x72, (byte)0x90, (byte)0x51
    };

    private static void cycle(byte[] block, int[][] keySchedule) {
        int ptr1 = 0;
        int ptr2 = 0;
        int ptr3 = 0;
        int ptr4 = 0;
        
        // 将block转换为int数组以便进行32位操作
        int[] bWords = new int[4];
        for (int i = 0; i < 4; i++) {
            bWords[i] = ((block[i*4] & 0xFF) << 24) |
                        ((block[i*4+1] & 0xFF) << 16) |
                        ((block[i*4+2] & 0xFF) << 8) |
                        (block[i*4+3] & 0xFF);
        }
        
        // 与最后一个key schedule异或
        bWords[0] ^= keySchedule[10][0];
        bWords[1] ^= keySchedule[10][1];
        bWords[2] ^= keySchedule[10][2];
        bWords[3] ^= keySchedule[10][3];
        
        // 将int数组写回block
        for (int i = 0; i < 4; i++) {
            block[i*4] = (byte)(bWords[i] >>> 24);
            block[i*4+1] = (byte)(bWords[i] >>> 16);
            block[i*4+2] = (byte)(bWords[i] >>> 8);
            block[i*4+3] = (byte)bWords[i];
        }
        
        // 第一次置换
        permuteBlock1(block);
        
        // 主循环
        for (int round = 0; round < 9; round++) {
            byte[] key0 = intToBytes(keySchedule[9-round][0]);
            
            // E 操作
            ptr1 = table_s5[(block[3] & 0xFF) ^ (key0[3] & 0xFF)];
            ptr2 = table_s6[(block[2] & 0xFF) ^ (key0[2] & 0xFF)];
            ptr3 = table_s8[(block[0] & 0xFF) ^ (key0[0] & 0xFF)];
            ptr4 = table_s7[(block[1] & 0xFF) ^ (key0[1] & 0xFF)];
            
            // A B 操作
            int ab = ptr1 ^ ptr2 ^ ptr3 ^ ptr4;
            
            // C 操作
            bWords[0] = ab;
            
            byte[] key1 = intToBytes(keySchedule[9-round][1]);
            ptr2 = table_s5[(block[7] & 0xFF) ^ (key1[3] & 0xFF)];
            ptr1 = table_s6[(block[6] & 0xFF) ^ (key1[2] & 0xFF)];
            ptr4 = table_s7[(block[5] & 0xFF) ^ (key1[1] & 0xFF)];
            ptr3 = table_s8[(block[4] & 0xFF) ^ (key1[0] & 0xFF)];
            
            // A B 再次操作
            ab = ptr1 ^ ptr2 ^ ptr3 ^ ptr4;
            bWords[1] = ab;
            
            // D 操作
            byte[] key2 = intToBytes(keySchedule[9-round][2]);
            byte[] key3 = intToBytes(keySchedule[9-round][3]);
            
            bWords[2] = table_s5[(block[11] & 0xFF) ^ (key2[3] & 0xFF)] ^
                        table_s6[(block[10] & 0xFF) ^ (key2[2] & 0xFF)] ^
                        table_s7[(block[9] & 0xFF) ^ (key2[1] & 0xFF)] ^
                        table_s8[(block[8] & 0xFF) ^ (key2[0] & 0xFF)];
            
            bWords[3] = table_s5[(block[15] & 0xFF) ^ (key3[3] & 0xFF)] ^
                        table_s6[(block[14] & 0xFF) ^ (key3[2] & 0xFF)] ^
                        table_s7[(block[13] & 0xFF) ^ (key3[1] & 0xFF)] ^
                        table_s8[(block[12] & 0xFF) ^ (key3[0] & 0xFF)];
            
            // 将int数组写回block
            for (int i = 0; i < 4; i++) {
                block[i*4] = (byte)(bWords[i] >>> 24);
                block[i*4+1] = (byte)(bWords[i] >>> 16);
                block[i*4+2] = (byte)(bWords[i] >>> 8);
                block[i*4+3] = (byte)bWords[i];
            }
            
            // 在最后一轮之前进行置换
            permuteBlock2(block, 8-round);
        }
        
        // 最后与key schedule[0]异或
        bWords[0] ^= keySchedule[0][0];
        bWords[1] ^= keySchedule[0][1];
        bWords[2] ^= keySchedule[0][2];
        bWords[3] ^= keySchedule[0][3];
        
        // 最后一次将int数组写回block
        for (int i = 0; i < 4; i++) {
            block[i*4] = (byte)(bWords[i] >>> 24);
            block[i*4+1] = (byte)(bWords[i] >>> 16);
            block[i*4+2] = (byte)(bWords[i] >>> 8);
            block[i*4+3] = (byte)bWords[i];
        }
    }

    // 辅助方法：将int转换为byte数组
    private static byte[] intToBytes(int value) {
        return new byte[] {
            (byte)(value >>> 24),
            (byte)(value >>> 16),
            (byte)(value >>> 8),
            (byte)value
        };
    }
} 