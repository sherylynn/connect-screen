#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include <enet/enet.h>
#include "stream.h"
#include "logging.h"
#include "globals.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ServerNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ServerNative", __VA_ARGS__)

static bool running = false;
static JavaVM* javaVM = nullptr;
static jobject serverInstance = nullptr;
static std::unique_ptr<logging::deinit_t> deinit;
static std::shared_ptr<stream::session_t> sessionObj;

// JNI_OnLoad implementation
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    javaVM = vm;
    return JNI_VERSION_1_6;
}

// 回调辅助函数
namespace stream {
    void notifyMoonlightConnected() {
        JNIEnv *env;
        if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            jclass serverClass = env->GetObjectClass(serverInstance);
            jmethodID methodId = env->GetMethodID(serverClass, "onMoonlightConnected", "()V");

            if (methodId != nullptr) {
                env->CallVoidMethod(serverInstance, methodId);
            }

            env->DeleteLocalRef(serverClass);
            javaVM->DetachCurrentThread();
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_startServer(JNIEnv* env, jobject thiz, jbyteArray gcmKey, jbyteArray iv, jstring peerIp) {
    // 保存全局引用
    serverInstance = env->NewGlobalRef(thiz);
    
    if (!running) {
        running = true;
        mail::man = std::make_shared<safe::mail_raw_t>();
        deinit = logging::init(0);

        stream::session::launch_session_t launch_session = {
                .av_ping_payload = "A4AACADDA6340FB4"
        };
        stream::config_t config = {};

        jbyte* gcmKeyBytes = env->GetByteArrayElements(gcmKey, nullptr);
        jsize gcmKeyLength = env->GetArrayLength(gcmKey);

        if (gcmKeyLength > 0) {
            launch_session.gcm_key.resize(gcmKeyLength);
            std::memcpy(launch_session.gcm_key.data(), gcmKeyBytes, gcmKeyLength);
        }

        env->ReleaseByteArrayElements(gcmKey, gcmKeyBytes, JNI_ABORT);

        // 获取iv数组数据
        jbyte* ivBytes = env->GetByteArrayElements(iv, nullptr);
        jsize ivLength = env->GetArrayLength(iv);
        
        // 将iv数据复制到launch_session中
        if (ivLength > 0) {
            launch_session.iv.resize(ivLength);
            std::memcpy(launch_session.iv.data(), ivBytes, ivLength);
        }
        
        // 释放iv数组
        env->ReleaseByteArrayElements(iv, ivBytes, JNI_ABORT);

        // 获取peerIp字符串
        const char* peerIpStr = env->GetStringUTFChars(peerIp, nullptr);

        sessionObj = stream::session::alloc(config, launch_session);
        stream::session::start(*sessionObj, peerIpStr);
        
        // 释放peerIp字符串
        env->ReleaseStringUTFChars(peerIp, peerIpStr);
        
        LOGI("Server thread started!!!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        if (serverInstance != nullptr) {
            env->DeleteGlobalRef(serverInstance);
            serverInstance = nullptr;
        }
        LOGI("Server thread stopped");
        deinit = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_postFrame(
    JNIEnv* env, jobject /* this */,
    jbyteArray frameData, jboolean isIdr, jlong frameIndex) {

    // 获取帧数据
    jbyte* bytes = env->GetByteArrayElements(frameData, nullptr);
    jsize length = env->GetArrayLength(frameData);

    // 创建帧数据向量
    std::vector<uint8_t> frame_vector(length);
    std::memcpy(frame_vector.data(), bytes, length);

    // 释放Java字节数组
    env->ReleaseByteArrayElements(frameData, bytes, JNI_ABORT);

    // 创建packet_raw_generic对象
    auto packet = std::make_unique<video::packet_raw_generic>(
        std::move(frame_vector),
        frameIndex,
        isIdr
    );

    // TODO: 在这里处理packet，比如发送到视频流
    LOGI("received postFrame");
}