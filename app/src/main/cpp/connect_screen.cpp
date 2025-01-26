#include <jni.h>
#include <string>
#include "playfair.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gitee_connect_1screen_MirrorHomeFragment_playfairDecrypt(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray message3,
        jbyteArray cipherText) {
    
    // 获取输入字节数组
    jbyte* message3Ptr = env->GetByteArrayElements(message3, nullptr);
    jbyte* cipherTextPtr = env->GetByteArrayElements(cipherText, nullptr);
    
    // 创建输出密钥数组
    unsigned char keyOut[16];
    
    // 调用 playfair_decrypt
    playfair_decrypt(
        reinterpret_cast<unsigned char*>(message3Ptr),
        reinterpret_cast<unsigned char*>(cipherTextPtr),
        keyOut
    );
    
    // 创建返回的字节数组
    jbyteArray result = env->NewByteArray(16);
    env->SetByteArrayRegion(result, 0, 16, reinterpret_cast<jbyte*>(keyOut));
    
    // 释放资源
    env->ReleaseByteArrayElements(message3, message3Ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(cipherText, cipherTextPtr, JNI_ABORT);
    
    return result;
}