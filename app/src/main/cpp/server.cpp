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

static std::unique_ptr<logging::deinit_t> deinit;

static std::shared_ptr<stream::session_t> session;

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_startServer(JNIEnv* env, jobject /* this */, jbyteArray iv) {
    if (!running) {
        running = true;
        mail::man = std::make_shared<safe::mail_raw_t>();
        deinit = logging::init(0);

        // 获取iv数组数据
        jbyte* ivBytes = env->GetByteArrayElements(iv, nullptr);
        jsize ivLength = env->GetArrayLength(iv);
        
        stream::session::launch_session_t launch_session = {};
        stream::config_t config = {};
        
        // 将iv数据复制到launch_session中
        if (ivLength > 0) {
            launch_session.iv.resize(ivLength);
            std::memcpy(launch_session.iv.data(), ivBytes, ivLength);
        }
        
        // 释放iv数组
        env->ReleaseByteArrayElements(iv, ivBytes, JNI_ABORT);
        
        session = stream::session::alloc(config, launch_session);
        stream::session::start(*session, "1.2.3.4");
        LOGI("Server thread started!!!");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gitee_connect_1screen_NativeServer_stopServer(JNIEnv* env, jobject /* this */) {
    if (running) {
        running = false;
        LOGI("Server thread stopped");
        deinit = nullptr;
    }
} 